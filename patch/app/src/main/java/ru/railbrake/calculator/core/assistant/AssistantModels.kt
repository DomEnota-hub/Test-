package ru.railbrake.calculator.core.assistant

import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

enum class AssistantDocumentKind {
    TECHNICAL_ENTRY,
    VL80_DIAGNOSTIC,
    ERMAK_DIAGNOSTIC,
    KNOWLEDGE_ARTICLE
}

sealed interface AssistantTarget {
    data class Technical(
        val family: TechnicalFamily,
        val section: TechnicalSection,
        val entryId: String
    ) : AssistantTarget

    data class Vl80Diagnostic(
        val scenarioId: String
    ) : AssistantTarget

    data class ErmakDiagnostic(
        val scenarioId: String
    ) : AssistantTarget

    data class Knowledge(
        val articleId: String
    ) : AssistantTarget
}

data class AssistantDocument(
    val key: String,
    val canonicalId: String,
    val kind: AssistantDocumentKind,
    val family: TechnicalFamily?,
    val section: TechnicalSection?,
    val title: String,
    val summary: String,
    val body: String,
    val aliases: Set<String>,
    val componentIds: Set<String>,
    val symptomTerms: Set<String>,
    val tags: Set<String>,
    val relatedIds: Set<String>,
    val safetyCritical: Boolean,
    val target: AssistantTarget,
    val nativeSearchText: String = ""
) {
    val searchText: String = buildString {
        append(title)
        append(' ')
        append(summary)
        append(' ')
        append(body)
        append(' ')
        append(aliases.joinToString(" "))
        append(' ')
        append(componentIds.joinToString(" "))
        append(' ')
        append(symptomTerms.joinToString(" "))
        append(' ')
        append(tags.joinToString(" "))
        append(' ')
        append(nativeSearchText)
    }.normalizeAssistantText()
}

internal fun String.normalizeAssistantText(): String =
    lowercase()
        .replace('ё', 'е')
        .replace(Regex("\\s+"), " ")
        .trim()
