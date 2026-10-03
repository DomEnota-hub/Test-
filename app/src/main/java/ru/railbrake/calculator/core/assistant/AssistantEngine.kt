package ru.railbrake.calculator.core.assistant

import kotlin.math.abs
import ru.railbrake.calculator.core.TechnicalFamily

class AssistantEngine(
    private val index: InMemoryAssistantIndex,
    private val defaultFamily: ru.railbrake.calculator.core.TechnicalFamily? = null
) {
    fun query(rawText: String, limit: Int = 3): AssistantEngineResult {
        val parsed = AssistantQueryParser.parse(rawText)
        if (AssistantQueryParser.hasConflictingSeries(rawText)) {
            return AssistantEngineResult.Clarify(
                parsedQuery = parsed.copy(family = null),
                clarification = clarificationFor(AssistantAmbiguity.SERIES_REQUIRED, parsed)
            )
        }
        return query(parsed, limit)
    }

    internal fun query(input: AssistantParsedQuery, limit: Int = 3): AssistantEngineResult {
        // The working choice supplies context only when the request does not
        // name a series. First aid and general safety remain common.
        val scoped = if (input.family == null && defaultFamily != null &&
            input.intent != AssistantIntent.SAFETY) input.copy(family = defaultFamily) else input
        val diesel = if (scoped.componentKey == null) {
            AssistantQueryParser.dieselFromEngineContext(scoped.normalizedText, scoped.family)
        } else null
        val parsed = if (diesel != null) scoped.copy(
            componentKey = diesel,
            searchText = "${scoped.searchText} ${AssistantQueryParser.componentSearchText(diesel).orEmpty()}"
        ) else scoped

        if (parsed.family == TechnicalFamily.CHME3E && listOf(
                "реостатный тормоз", "эдт", "тормозные резисторы", "вентилятор тормозных резисторов"
            ).any { it in parsed.normalizedText }) {
            return AssistantEngineResult.NoResult(
                parsedQuery = parsed,
                message = "Заводской реостатный тормоз относится к ЧМЭ3Т. Уточните фактическое исполнение или модернизацию тепловоза; маршрут ЧМЭ3Т для ЧМЭ3Э автоматически не открывается."
            )
        }

        if (parsed.family?.isTem2 == true && listOf(
                "реостатный тормоз", "эдт", "тэм2т", "тэм2ум", "тэм2а", "1пд-4а"
            ).any { it in parsed.normalizedText }) {
            return AssistantEngineResult.NoResult(
                parsedQuery = parsed,
                message = "Это оборудование не подтверждено для выбранного профиля ТЭМ2/ТЭМ2У. Уточните фактическое исполнение и документацию; данные соседних вариантов автоматически не применяются."
            )
        }

        if (parsed.intent == AssistantIntent.SAFETY && parsed.safetyTopicIds.isEmpty() &&
            parsed.normalizedText in setOf("опп", "первая помощь", "первую помощь", "помоги человеку")) {
            return AssistantEngineResult.NoResult(
                parsedQuery = parsed,
                message = "Уточните, что произошло с человеком или какие признаки вы наблюдаете."
            )
        }

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
            safetyTopicId = parsed.safetyTopicId,
            safetyTopicIds = parsed.safetyTopicIds,
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

        // A general safety/reference topic can have identical cards under two
        // locomotive catalogues. Only operational questions need a series gate.
        if (parsed.family == null && parsed.intent in setOf(
                AssistantIntent.TROUBLESHOOT, AssistantIntent.OPEN_SCHEME, AssistantIntent.ACCEPTANCE
            )) {
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
                AssistantClarificationOption("ERMAK", "2ЭС5К / 3ЭС5К «Ермак»"),
                AssistantClarificationOption("CHME3", "ЧМЭ3"),
                AssistantClarificationOption("CHME3T", "ЧМЭ3Т"),
                AssistantClarificationOption("CHME3E", "ЧМЭ3Э")
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
                AssistantClarificationOption("ERMAK", "2ЭС5К / 3ЭС5К «Ермак»"),
                AssistantClarificationOption("CHME3", "ЧМЭ3"),
                AssistantClarificationOption("CHME3T", "ЧМЭ3Т"),
                AssistantClarificationOption("CHME3E", "ЧМЭ3Э")
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
