package ru.railbrake.calculator.core.assistant

import kotlin.math.abs

class AssistantEngine(
    private val index: InMemoryAssistantIndex
) {
    fun query(rawText: String, limit: Int = 3): AssistantEngineResult {
        val parsed = AssistantQueryParser.parse(rawText)
        return query(parsed, limit)
    }

    internal fun query(parsed: AssistantParsedQuery, limit: Int = 3): AssistantEngineResult {

        parsed.ambiguity?.let { ambiguity ->
            return AssistantEngineResult.Clarify(
                parsedQuery = parsed,
                clarification = clarificationFor(ambiguity, parsed)
            )
        }

        val request = AssistantSearchRequest(
            query = parsed.searchText,
            literalQuery = parsed.normalizedText,
            family = parsed.family,
            preferredSection = parsed.preferredSection,
            componentId = parsed.componentKey,
            failureModes = parsed.failureModes,
            limit = limit.coerceIn(1, 5)
        )
        val hits = index.search(request)

        // A component fault without a named locomotive is not safe to resolve by
        // whichever family happens to have denser text in the index. Ask first.
        if (
            parsed.family == null &&
            parsed.intent in setOf(AssistantIntent.TROUBLESHOOT, AssistantIntent.OPEN_SCHEME) &&
            parsed.componentKey != null &&
            hits.none { "canonical-id" in it.reasons }
        ) {
            return AssistantEngineResult.Clarify(
                parsedQuery = parsed,
                clarification = clarificationFor(AssistantAmbiguity.SERIES_REQUIRED, parsed),
                provisionalHits = hits
            )
        }

        if (hits.isEmpty()) {
            return AssistantEngineResult.NoResult(
                parsedQuery = parsed,
                message = "Точного материала в локальной базе не найдено. Уточните серию, узел или признак неисправности."
            )
        }

        if (parsed.family == null) {
            crossFamilyAmbiguity(hits)?.let { clarification ->
                return AssistantEngineResult.Clarify(
                    parsedQuery = parsed,
                    clarification = clarification,
                    provisionalHits = hits
                )
            }
        }

        val first = hits.first()
        val second = hits.getOrNull(1)
        val margin = first.score - (second?.score ?: 0)

        val recommendedTarget = if (
            !first.document.safetyCritical &&
            parsed.intentConfidence >= 0.82 &&
            first.score >= 55 &&
            margin >= 18
        ) {
            first.document.target
        } else {
            null
        }

        return AssistantEngineResult.Matches(
            parsedQuery = parsed,
            hits = hits,
            recommendedTarget = recommendedTarget
        )
    }

    private fun crossFamilyAmbiguity(hits: List<AssistantSearchHit>): AssistantClarification? {
        val first = hits.firstOrNull() ?: return null
        val firstFamily = first.document.family ?: return null
        val competitor = hits.drop(1).firstOrNull { hit ->
            val family = hit.document.family
            family != null && family != firstFamily && abs(first.score - hit.score) <= 12
        } ?: return null

        if (competitor.document.family == firstFamily) return null

        return AssistantClarification(
            question = "Для какой серии локомотива нужен результат?",
            reason = AssistantAmbiguity.SERIES_REQUIRED,
            options = listOf(
                AssistantClarificationOption("VL80S", "ВЛ80С"),
                AssistantClarificationOption("ERMAK", "2ЭС5К / 3ЭС5К «Ермак»")
            )
        )
    }

    private fun clarificationFor(
        ambiguity: AssistantAmbiguity,
        parsed: AssistantParsedQuery
    ): AssistantClarification = when (ambiguity) {
        AssistantAmbiguity.TOPIC_SCOPE -> {
            if (parsed.componentKey == "MAIN_BREAKER") {
                AssistantClarification(
                    question = "Что нужно по главному выключателю?",
                    reason = ambiguity,
                    options = listOf(
                        AssistantClarificationOption("diagnostics", "Диагностика"),
                        AssistantClarificationOption("reference", "Описание"),
                        AssistantClarificationOption("scheme", "Показать на схеме")
                    )
                )
            } else {
                AssistantClarification(
                    question = "Что именно нужно найти?",
                    reason = ambiguity,
                    options = listOf(
                        AssistantClarificationOption("diagnostics", "Неисправность / диагностика"),
                        AssistantClarificationOption("reference", "Справочная информация"),
                        AssistantClarificationOption("procedure", "Порядок действий / процедура")
                    )
                )
            }
        }

        AssistantAmbiguity.SERIES_REQUIRED -> AssistantClarification(
            question = "На каком локомотиве это произошло?",
            reason = ambiguity,
            options = listOf(
                AssistantClarificationOption("VL80S", "ВЛ80С"),
                AssistantClarificationOption("ERMAK", "2ЭС5К / 3ЭС5К «Ермак»")
            )
        )

        AssistantAmbiguity.COMPONENT_REQUIRED -> AssistantClarification(
            question = "Какой узел или аппарат не работает?",
            reason = ambiguity,
            options = listOf(
                AssistantClarificationOption("MAIN_BREAKER", "Главный выключатель"),
                AssistantClarificationOption("COMPRESSOR", "Компрессор"),
                AssistantClarificationOption("OTHER", "Другой узел")
            )
        )

        AssistantAmbiguity.PROCEDURE_TYPE_REQUIRED -> AssistantClarification(
            question = "Какая проба тормозов нужна?",
            reason = ambiguity,
            options = listOf(
                AssistantClarificationOption("FULL", "Полная"),
                AssistantClarificationOption("SHORT", "Сокращённая"),
                AssistantClarificationOption("TECH", "Технологическая")
            )
        )
    }
}
