package ru.railbrake.calculator.core.assistant

import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

data class AssistantSearchRequest(
    val query: String,
    val literalQuery: String? = null,
    val family: TechnicalFamily? = null,
    val preferredSection: TechnicalSection? = null,
    val componentId: String? = null,
    val failureModes: Set<AssistantFailureMode> = emptySet(),
    val limit: Int = 5
)

data class AssistantSearchHit(
    val document: AssistantDocument,
    val score: Int,
    val reasons: List<String>
)

class InMemoryAssistantIndex(
    documents: List<AssistantDocument>
) {
    private data class IndexedDocument(
        val document: AssistantDocument,
        val failureModes: Set<AssistantFailureMode>,
        val describedComponents: Set<String>,
        val primaryPhrases: Set<String>,
        val aliasPhrases: Set<String>
    )

    private val documents = documents.map { document ->
        val symptomProjection = buildString {
            append(document.title)
            append(' ')
            append(document.summary)
            append(' ')
            append(document.symptomTerms.joinToString(" "))
            append(' ')
            append(document.aliases.joinToString(" "))
        }
        val primaryPhrases = buildSet {
            add(document.title)
            if (document.summary.isNotBlank()) add(document.summary)
            addAll(document.symptomTerms)
        }
            .asSequence()
            .map(String::normalizeAssistantText)
            .filter { phrase -> phrase.length >= 8 && phrase.count(Char::isLetterOrDigit) >= 6 }
            .toSet()
        val aliasPhrases = document.aliases
            .asSequence()
            .map(String::normalizeAssistantText)
            .filter { phrase -> phrase.length >= 8 && phrase.count(Char::isLetterOrDigit) >= 6 }
            .toSet()

        IndexedDocument(
            document = document,
            failureModes = if (document.section == TechnicalSection.DIAGNOSTICS) {
                AssistantFailureModeDetector.detect(symptomProjection)
            } else {
                emptySet()
            },
            describedComponents = AssistantQueryParser.componentKeysInDescription(
                "${document.title} ${document.summary} ${document.symptomTerms.joinToString(" ")}"
            ),
            primaryPhrases = primaryPhrases,
            aliasPhrases = aliasPhrases
        )
    }

    fun size(): Int = documents.size

    fun search(request: AssistantSearchRequest): List<AssistantSearchHit> {
        val query = request.query.normalizeAssistantText()
        val literalQuery = (request.literalQuery ?: request.query).normalizeAssistantText()
        val queryParts = query.split(' ')
        val tokens = queryParts
            .filter { it.length > 1 && it !in stopWords }
            .toSet()
        val diagnosticsFirst = request.preferredSection == TechnicalSection.DIAGNOSTICS

        return documents.mapNotNull { indexed ->
            val document = indexed.document
            // A named locomotive is a hard scope for operational material.
            // A very strong text match must never surface another series.
            if (request.family != null && document.family != null && document.family != request.family) {
                return@mapNotNull null
            }
            var score = 0
            var hasContentEvidence = false
            val reasons = mutableListOf<String>()

            val canonicalId = document.canonicalId.normalizeAssistantText()
            val directCanonicalId = canonicalId.isNotBlank() &&
                (query == canonicalId || queryParts.contains(canonicalId) ||
                    (canonicalId.length >= 5 && query.contains(canonicalId)))
            if (directCanonicalId) {
                score += 180
                hasContentEvidence = true
                reasons += "canonical-id"
            }

            val linkedId = (document.relatedIds + document.componentIds)
                .asSequence()
                .map(String::normalizeAssistantText)
                .filter(String::isNotBlank)
                .firstOrNull { candidate ->
                    query == candidate || queryParts.contains(candidate) ||
                        (candidate.length >= 5 && query.contains(candidate))
                }
            if (linkedId != null) {
                score += 90
                hasContentEvidence = true
                reasons += "linked-id"
            }

            if (query.isNotBlank() && query in document.searchText) {
                score += 45
                hasContentEvidence = true
                reasons += "phrase"
            }

            // The scenario's own title/summary/symptom is authoritative. Query
            // enrichment appends family/component aliases, so compare by
            // containment rather than equality and give this signal enough
            // weight to beat broad cards that share only generic words.
            val primaryPhraseMatched = indexed.primaryPhrases.any { phrase ->
                phrase in literalQuery || (literalQuery.length >= 8 && literalQuery in phrase)
            }
            if (primaryPhraseMatched) {
                score += 260
                hasContentEvidence = true
                reasons += "primary-phrase"
            }

            val aliasPhraseMatched = indexed.aliasPhrases.any { phrase ->
                phrase in literalQuery || (literalQuery.length >= 8 && literalQuery in phrase)
            }
            if (aliasPhraseMatched) {
                score += 65
                hasContentEvidence = true
                reasons += "alias-phrase"
            }

            val symptomPhraseMatched = document.symptomTerms
                .asSequence()
                .map(String::normalizeAssistantText)
                .filter { it.length >= 4 }
                .any { symptom -> symptom in literalQuery || literalQuery in symptom }
            if (symptomPhraseMatched) {
                score += 55
                hasContentEvidence = true
                reasons += "symptom-phrase"
            }

            if (tokens.isNotEmpty()) {
                val overlap = tokens.count { token -> token in document.searchText }
                if (overlap > 0) {
                    score += overlap * 6
                    hasContentEvidence = true
                    reasons += "tokens:$overlap"
                }
            }

            if (diagnosticsFirst && document.section == TechnicalSection.DIAGNOSTICS && request.failureModes.isNotEmpty()) {
                val requestedSpecific = request.failureModes - AssistantFailureMode.GENERAL_FAILURE
                val documentSpecific = indexed.failureModes - AssistantFailureMode.GENERAL_FAILURE
                val matchedModes = request.failureModes.intersect(indexed.failureModes)

                when {
                    matchedModes.isNotEmpty() -> {
                        score += 95 + (matchedModes.size - 1) * 15
                        hasContentEvidence = true
                        reasons += "failure-mode:${matchedModes.joinToString(",") { it.name }}"
                    }
                    requestedSpecific.isNotEmpty() && documentSpecific.isNotEmpty() -> {
                        score -= 90
                        reasons += "failure-mode-conflict"
                    }
                    AssistantFailureMode.GENERAL_FAILURE in request.failureModes -> {
                        score += 12
                        reasons += "general-failure"
                    }
                }
            }

            request.family?.let {
                score += 40
                reasons += "family"
            }

            request.preferredSection?.let { section ->
                if (document.section == section) {
                    score += if (diagnosticsFirst) 70 else 35
                    reasons += if (diagnosticsFirst) "diagnostics-priority" else "section"
                } else {
                    score -= if (diagnosticsFirst) 35 else 8
                    if (diagnosticsFirst) reasons += "non-diagnostic-penalty"
                }
            }

            request.componentId?.takeIf(String::isNotBlank)?.let { componentId ->
                if (componentId in indexed.describedComponents || componentId in document.componentIds) {
                    score += 110
                    hasContentEvidence = true
                    reasons += "component"
                } else if (diagnosticsFirst && document.section == TechnicalSection.DIAGNOSTICS) {
                    score -= 80
                    reasons += "component-mismatch"
                }
            }

            if (!hasContentEvidence || score <= 0) null
            else AssistantSearchHit(document, score, reasons)
        }
            .sortedWith(
                compareByDescending<AssistantSearchHit> { it.score }
                    .thenBy { it.document.title }
            )
            .take(request.limit.coerceIn(1, 20))
    }

    private companion object {
        val stopWords = setOf(
            "на", "не", "и", "или", "в", "во", "по", "для", "что", "как",
            "где", "покажи", "найди", "открой", "про", "при", "это", "он", "она",
            "id", "ид", "айди", "карточка", "карточку", "сценарий", "сценария"
        )
    }
}
