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
    val safetyTopicId: String? = null,
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
        val aliasPhrases: Set<String>,
        val symptomPhrases: Set<String>,
        val linkedIds: Set<String>,
        val canonicalId: String
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
            aliasPhrases = aliasPhrases,
            symptomPhrases = document.symptomTerms.map(String::normalizeAssistantText).filter { it.length >= 4 }.toSet(),
            linkedIds = (document.relatedIds + document.componentIds).map(String::normalizeAssistantText).filter(String::isNotBlank).toSet(),
            canonicalId = document.canonicalId.normalizeAssistantText()
        )
    }
    private val sectionDocuments = this.documents.groupBy { it.document.section }
    private val indexedIdentifiers = this.documents.asSequence()
        .flatMap { sequenceOf(it.canonicalId) + it.linkedIds.asSequence() }
        .filter { it.length >= 4 }
        .toSet()

    fun size(): Int = documents.size

    fun search(request: AssistantSearchRequest): List<AssistantSearchHit> {
        val query = request.query.normalizeAssistantText()
        val literalQuery = (request.literalQuery ?: request.query).normalizeAssistantText()
        val queryParts = query.split(' ')
        val tokens = queryParts
            .filter { it.length > 1 && it !in stopWords }
            .toSet()
        val diagnosticsFirst = request.preferredSection == TechnicalSection.DIAGNOSTICS

        // The parser supplies a likely section before ranking. Search that
        // already-indexed partition first; do not make it an exclusive gate:
        // sparse content, ambiguous speech and ID commands need the full index.
        val hasDirectIdentifier = queryParts.any(indexedIdentifiers::contains)
        val preferred = if (hasDirectIdentifier) null else request.preferredSection?.let(sectionDocuments::get)
        if (preferred != null && preferred.size < documents.size) {
            val focused = score(preferred, request, query, literalQuery, queryParts, tokens, diagnosticsFirst)
            if (focused.firstOrNull()?.let { hit ->
                    hit.score >= 115 && hit.reasons.any { reason ->
                        reason in setOf("primary-phrase", "alias-phrase", "symptom-phrase", "component", "safety-topic", "canonical-id") ||
                            reason.startsWith("failure-mode:")
                    }
                } == true) return focused
        }
        return score(documents, request, query, literalQuery, queryParts, tokens, diagnosticsFirst)
    }

    private fun score(
        candidates: List<IndexedDocument>, request: AssistantSearchRequest,
        query: String, literalQuery: String, queryParts: List<String>,
        tokens: Set<String>, diagnosticsFirst: Boolean
    ): List<AssistantSearchHit> {

        return candidates.mapNotNull { indexed ->
            val document = indexed.document
            // A named locomotive is a hard scope for operational material.
            // A very strong text match must never surface another series.
            if (request.family != null && document.family != null && document.family != request.family) {
                return@mapNotNull null
            }
            var score = 0
            var hasContentEvidence = false
            val reasons = mutableListOf<String>()

            val canonicalId = indexed.canonicalId
            val directCanonicalId = canonicalId.isNotBlank() &&
                (query == canonicalId || queryParts.contains(canonicalId) ||
                    (canonicalId.length >= 5 && query.contains(canonicalId)))
            if (directCanonicalId) {
                score += 180
                hasContentEvidence = true
                reasons += "canonical-id"
            }

            val linkedId = indexed.linkedIds
                .asSequence()
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

            val symptomPhraseMatched = indexed.symptomPhrases
                .asSequence()
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

            // A high-confidence first-aid topic beats unrelated engineering
            // cards sharing "удар" or "ток", while preserving other results.
            if (request.safetyTopicId != null && document.kind == AssistantDocumentKind.FIRST_AID &&
                document.canonicalId == request.safetyTopicId) {
                score += 500
                hasContentEvidence = true
                reasons += "safety-topic"
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
