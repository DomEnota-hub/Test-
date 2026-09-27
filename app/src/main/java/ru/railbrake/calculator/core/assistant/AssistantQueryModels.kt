package ru.railbrake.calculator.core.assistant

import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

enum class AssistantIntent {
    FIND_TOPIC,
    DEFINE_TERM,
    PROCEDURE,
    TROUBLESHOOT,
    OPEN_SCHEME,
    ACCEPTANCE,
    SAFETY
}

data class AssistantParsedQuery(
    val rawText: String,
    val normalizedText: String,
    val searchText: String,
    val intent: AssistantIntent,
    val intentConfidence: Double,
    val family: TechnicalFamily?,
    val preferredSection: TechnicalSection?,
    val componentKey: String?,
    val ambiguity: AssistantAmbiguity? = null
)

enum class AssistantAmbiguity {
    TOPIC_SCOPE,
    SERIES_REQUIRED,
    COMPONENT_REQUIRED,
    PROCEDURE_TYPE_REQUIRED
}

data class AssistantClarificationOption(
    val id: String,
    val label: String
)

data class AssistantClarification(
    val question: String,
    val reason: AssistantAmbiguity,
    val options: List<AssistantClarificationOption>
)

sealed interface AssistantEngineResult {
    val parsedQuery: AssistantParsedQuery

    data class Matches(
        override val parsedQuery: AssistantParsedQuery,
        val hits: List<AssistantSearchHit>,
        val recommendedTarget: AssistantTarget?
    ) : AssistantEngineResult

    data class Clarify(
        override val parsedQuery: AssistantParsedQuery,
        val clarification: AssistantClarification,
        val provisionalHits: List<AssistantSearchHit> = emptyList()
    ) : AssistantEngineResult

    data class NoResult(
        override val parsedQuery: AssistantParsedQuery,
        val message: String
    ) : AssistantEngineResult
}
