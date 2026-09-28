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
    val failureModes: Set<AssistantFailureMode> = emptySet(),
    val ambiguity: AssistantAmbiguity? = null,
    val safetyTopicId: String? = null
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

/** Structured state for one unresolved assistant request. */
data class AssistantQueryContext(
    val family: TechnicalFamily? = null,
    val componentKey: String? = null,
    val intent: AssistantIntent? = null,
    val failureModes: Set<AssistantFailureMode> = emptySet(),
    val section: TechnicalSection? = null,
    val procedureType: AssistantProcedureType? = null,
    val topicScope: AssistantTopicScope? = null
)

enum class AssistantProcedureType { FULL, SHORT, TECHNOLOGICAL }

enum class AssistantTopicScope { DIAGNOSTICS, REFERENCE, SCHEME, PROCEDURE }

data class AssistantPendingClarification(
    val originalQuery: String,
    val normalizedQuery: String,
    val context: AssistantQueryContext,
    val missingParameter: AssistantAmbiguity,
    val allowedOptions: List<AssistantClarificationOption>,
    val candidateIds: List<String> = emptyList()
)

data class AssistantConversationTurn(
    val result: AssistantEngineResult?,
    val pending: AssistantPendingClarification?,
    val continuedFromPending: Boolean = false,
    val cancelled: Boolean = false
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
