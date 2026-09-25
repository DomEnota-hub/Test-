package ru.railbrake.calculator.core.assistant

import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

data class AssistantSearchRequest(
    val query: String,
    val family: TechnicalFamily? = null,
    val preferredSection: TechnicalSection? = null,
    val componentId: String? = null,
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
    private val documents = documents.toList()

    fun size(): Int = documents.size

    fun search(request: AssistantSearchRequest): List<AssistantSearchHit> {
        val query = request.query.normalizeAssistantText()
        val tokens = query.split(' ').filter { it.length > 1 }.toSet()

        return documents.mapNotNull { document ->
            var score = 0
            val reasons = mutableListOf<String>()

            if (query.isNotBlank() && query in document.searchText) {
                score += 45
                reasons += "phrase"
            }

            if (tokens.isNotEmpty()) {
                val overlap = tokens.count { it in document.searchText }
                if (overlap > 0) {
                    score += overlap * 6
                    reasons += "tokens:$overlap"
                }
            }

            request.family?.let { family ->
                if (document.family == family || document.family == null) {
                    score += 40
                    reasons += "family"
                } else {
                    score -= 80
                    reasons += "family-conflict"
                }
            }

            request.preferredSection?.let { section ->
                if (document.section == section) {
                    score += 35
                    reasons += "section"
                } else {
                    score -= 8
                }
            }

            request.componentId?.takeIf(String::isNotBlank)?.let { componentId ->
                if (componentId in document.componentIds) {
                    score += 50
                    reasons += "component"
                }
            }

            if (score <= 0) null
            else AssistantSearchHit(document, score, reasons)
        }
            .sortedWith(
                compareByDescending<AssistantSearchHit> { it.score }
                    .thenBy { it.document.title }
            )
            .take(request.limit.coerceIn(1, 20))
    }
}
