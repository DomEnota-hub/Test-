package ru.railbrake.calculator.core.assistant

import ru.railbrake.calculator.core.TechnicalFamily

/**
 * Stateless multi-turn coordinator. The UI owns [AssistantPendingClarification],
 * while the shared engine remains immutable and safe to cache application-wide.
 */
object AssistantConversation {
    private val cancellationPhrases = setOf(
        "отмена", "отмени", "сброс", "сбросить", "начать заново", "новый запрос"
    )

    fun submit(
        engine: AssistantEngine,
        rawText: String,
        pending: AssistantPendingClarification? = null
    ): AssistantConversationTurn {
        val prepared = rawText.trim()
        if (prepared.isBlank()) return AssistantConversationTurn(null, pending)

        if (pending == null) return finish(engine.query(prepared), prepared, false)

        val normalized = AssistantQueryParser.normalize(prepared)
        if (normalized in cancellationPhrases) {
            return AssistantConversationTurn(result = null, pending = null, cancelled = true)
        }

        val continuation = continuationText(prepared, pending)
        if (continuation != null) {
            val merged = merge(pending.originalQuery, continuation)
            return finish(engine.query(merged), merged, true)
        }

        // A substantial, independently parseable phrase starts a new request.
        // An unknown one-word reply simply leaves the current question active.
        val parsed = AssistantQueryParser.parse(prepared)
        val isNewRequest = normalized.split(' ').size > 1 ||
            parsed.family != null || parsed.componentKey != null || parsed.ambiguity != null ||
            parsed.intent != AssistantIntent.FIND_TOPIC ||
            normalized.contains("-") || normalized.contains(":")
        return if (isNewRequest) {
            finish(engine.query(prepared), prepared, false)
        } else {
            AssistantConversationTurn(
                result = AssistantEngineResult.Clarify(
                    parsedQuery = parsedFromPending(pending),
                    clarification = AssistantClarification(
                        question = questionFor(pending.missingParameter),
                        reason = pending.missingParameter,
                        options = pending.allowedOptions
                    )
                ),
                pending = pending
            )
        }
    }

    private fun continuationText(answer: String, pending: AssistantPendingClarification): String? {
        val parsedAnswer = AssistantQueryParser.parse(answer)
        return when (pending.missingParameter) {
            AssistantAmbiguity.SERIES_REQUIRED -> {
                if (parsedAnswer.componentKey != null || parsedAnswer.failureModes.isNotEmpty()) return null
                when (AssistantQueryParser.familyFromAnswer(answer)) {
                    TechnicalFamily.VL80S -> "ВЛ80С"
                    TechnicalFamily.ERMAK -> when {
                        "2эс5к" in AssistantQueryParser.normalize(answer) -> "2ЭС5К"
                        "3эс5к" in AssistantQueryParser.normalize(answer) -> "3ЭС5К"
                        else -> "Ермак"
                    }
                    null -> null
                }
            }
            AssistantAmbiguity.COMPONENT_REQUIRED -> {
                if (parsedAnswer.family != null || parsedAnswer.failureModes.isNotEmpty()) return null
                val component = AssistantQueryParser.componentFromAnswer(answer) ?: return null
                AssistantQueryParser.componentSearchText(component)
            }
            AssistantAmbiguity.TOPIC_SCOPE -> when (AssistantQueryParser.normalize(answer)) {
                "диагностика", "неисправность", "неисправности", "ремонт", "неисправность диагностика" -> "диагностика"
                "описание", "справка", "устройство", "назначение", "справочная информация" -> "описание"
                "схема", "схему", "на схеме", "показать схему", "показать на схеме" -> "на схеме"
                "процедура", "порядок", "порядок действий", "порядок действий процедура" -> "порядок"
                else -> null
            }
            AssistantAmbiguity.PROCEDURE_TYPE_REQUIRED -> when (AssistantQueryParser.normalize(answer)) {
                "полная", "полное", "полную" -> "полная"
                "сокращенная", "сокращенное", "сокращенную", "частичная", "частичное", "частичную" -> "сокращенная"
                "технологическая", "технологическое", "технологическую" -> "технологическая"
                else -> null
            }
        }
    }

    private fun merge(original: String, continuation: String): String = when (continuation) {
        "диагностика", "описание", "порядок" -> "$continuation $original"
        "полная", "сокращенная", "технологическая" -> "$continuation проба тормозов"
        else -> "$original $continuation"
    }

    private fun finish(
        result: AssistantEngineResult,
        originalQuery: String,
        continued: Boolean
    ): AssistantConversationTurn {
        val pending = (result as? AssistantEngineResult.Clarify)?.let { clarify ->
            AssistantPendingClarification(
                originalQuery = originalQuery,
                normalizedQuery = clarify.parsedQuery.normalizedText,
                context = clarify.parsedQuery.toContext(),
                missingParameter = clarify.clarification.reason,
                allowedOptions = clarify.clarification.options,
                candidateIds = clarify.provisionalHits.map { it.document.canonicalId }.distinct()
            )
        }
        return AssistantConversationTurn(result, pending, continuedFromPending = continued)
    }

    private fun AssistantParsedQuery.toContext(): AssistantQueryContext {
        val procedureType = when {
            "полная" in normalizedText -> AssistantProcedureType.FULL
            "сокращ" in normalizedText -> AssistantProcedureType.SHORT
            "технолог" in normalizedText -> AssistantProcedureType.TECHNOLOGICAL
            else -> null
        }
        val topicScope = when (intent) {
            AssistantIntent.TROUBLESHOOT -> AssistantTopicScope.DIAGNOSTICS
            AssistantIntent.DEFINE_TERM -> AssistantTopicScope.REFERENCE
            AssistantIntent.OPEN_SCHEME -> AssistantTopicScope.SCHEME
            AssistantIntent.PROCEDURE -> AssistantTopicScope.PROCEDURE
            else -> null
        }
        return AssistantQueryContext(
            family = family,
            componentKey = componentKey,
            intent = intent,
            failureModes = failureModes,
            section = preferredSection,
            procedureType = procedureType,
            topicScope = topicScope
        )
    }

    private fun parsedFromPending(pending: AssistantPendingClarification): AssistantParsedQuery =
        AssistantQueryParser.parse(pending.originalQuery)

    private fun questionFor(reason: AssistantAmbiguity): String = when (reason) {
        AssistantAmbiguity.TOPIC_SCOPE -> "Что именно нужно найти?"
        AssistantAmbiguity.SERIES_REQUIRED -> "На каком локомотиве это произошло?"
        AssistantAmbiguity.COMPONENT_REQUIRED -> "Какой узел или аппарат не работает?"
        AssistantAmbiguity.PROCEDURE_TYPE_REQUIRED -> "Какая проба тормозов нужна?"
    }
}
