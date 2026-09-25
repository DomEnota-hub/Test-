package ru.railbrake.calculator.core.assistant

import ru.railbrake.calculator.core.DiagnosticScenario
import ru.railbrake.calculator.core.DiagnosticSeverity
import ru.railbrake.calculator.core.ErmakDiagnosticScenario
import ru.railbrake.calculator.core.KnowledgeArticle
import ru.railbrake.calculator.core.TechnicalEntry
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

object TechnicalEntryAssistantAdapter {
    fun adapt(entry: TechnicalEntry): AssistantDocument {
        val body = entry.blocks.joinToString("\n") { block ->
            buildString {
                if (block.title.isNotBlank()) append(block.title).append(": ")
                append(block.lines.joinToString(" "))
            }
        }

        val aliases = buildSet {
            add(entry.title)
            if (entry.subtitle.isNotBlank()) add(entry.subtitle)
            addAll(entry.sequenceLabels.values.filter(String::isNotBlank))
            addAll(entry.hotspots.map { it.label }.filter(String::isNotBlank))
        }

        val componentIds = buildSet {
            if (entry.section == TechnicalSection.EQUIPMENT) add(entry.id)
            addAll(entry.hotspots.map { it.equipmentId }.filter(String::isNotBlank))
        }

        return AssistantDocument(
            key = "technical:${entry.family.name}:${entry.section.name}:${entry.id}",
            canonicalId = entry.id,
            kind = AssistantDocumentKind.TECHNICAL_ENTRY,
            family = entry.family,
            section = entry.section,
            title = entry.title,
            summary = entry.subtitle,
            body = body,
            aliases = aliases,
            componentIds = componentIds,
            symptomTerms = emptySet(),
            tags = setOf(entry.status).filter(String::isNotBlank).toSet(),
            relatedIds = entry.relatedIds.toSet(),
            safetyCritical = entry.section in setOf(
                TechnicalSection.SAFETY,
                TechnicalSection.DIAGNOSTICS,
                TechnicalSection.ACCEPTANCE
            ),
            target = AssistantTarget.Technical(
                family = entry.family,
                section = entry.section,
                entryId = entry.id
            ),
            nativeSearchText = entry.searchText
        )
    }
}

object Vl80DiagnosticAssistantAdapter {
    fun adapt(scenario: DiagnosticScenario): AssistantDocument {
        val body = buildList {
            addAll(scenario.immediateActions)
            addAll(scenario.dangerSigns)
            addAll(scenario.probableCauses)
            addAll(scenario.observableSigns)
            addAll(scenario.systemExplanation)
            addAll(scenario.operationalConsequences)
            addAll(scenario.checks.flatMap { check ->
                listOf(check.title, check.action, check.expected, check.ifAbnormal)
            })
            addAll(scenario.prohibited)
            addAll(scenario.stopConditions)
            addAll(scenario.reportFields)
            addAll(scenario.trainingNotes)
        }.filter(String::isNotBlank).joinToString(" ")

        val aliases = buildSet {
            add(scenario.title)
            add(scenario.summary)
            addAll(scenario.observableSigns)
            addAll(scenario.relatedEquipment)
        }

        return AssistantDocument(
            key = "diagnostic:VL80S:${scenario.id}",
            canonicalId = scenario.id,
            kind = AssistantDocumentKind.VL80_DIAGNOSTIC,
            family = TechnicalFamily.VL80S,
            section = TechnicalSection.DIAGNOSTICS,
            title = scenario.title,
            summary = scenario.summary,
            body = body,
            aliases = aliases,
            componentIds = scenario.relatedEquipment.toSet(),
            symptomTerms = scenario.observableSigns.toSet(),
            tags = setOf(scenario.category, scenario.severity.name),
            relatedIds = scenario.relatedScenarioIds.toSet(),
            safetyCritical = scenario.severity != DiagnosticSeverity.INFORMATION,
            target = AssistantTarget.Vl80Diagnostic(scenario.id)
        )
    }
}

object ErmakDiagnosticAssistantAdapter {
    fun adapt(scenario: ErmakDiagnosticScenario): AssistantDocument {
        // Runtime graph control-flow deliberately stays in ErmakDiagnosticRepository.
        // The assistant indexes only the user-facing projection and stable target ID.
        val body = buildList {
            addAll(scenario.immediateActions)
            addAll(scenario.dangerSigns)
            addAll(scenario.probableCauses)
            addAll(scenario.safeChecks)
            addAll(scenario.prohibited)
            addAll(scenario.reportFields)
        }.filter(String::isNotBlank).joinToString(" ")

        return AssistantDocument(
            key = "diagnostic:ERMAK:${scenario.id}",
            canonicalId = scenario.id,
            kind = AssistantDocumentKind.ERMAK_DIAGNOSTIC,
            family = TechnicalFamily.ERMAK,
            section = TechnicalSection.DIAGNOSTICS,
            title = scenario.title,
            summary = scenario.symptom,
            body = body,
            aliases = setOf(scenario.title, scenario.symptom).filter(String::isNotBlank).toSet(),
            componentIds = scenario.equipmentIds,
            symptomTerms = setOf(scenario.symptom).filter(String::isNotBlank).toSet(),
            tags = setOf(scenario.category, scenario.severity).filter(String::isNotBlank).toSet(),
            relatedIds = emptySet(),
            safetyCritical = scenario.severity.uppercase() !in setOf("INFO", "INFORMATION"),
            target = AssistantTarget.ErmakDiagnostic(scenario.id)
        )
    }
}

object KnowledgeArticleAssistantAdapter {
    fun adapt(article: KnowledgeArticle): AssistantDocument = AssistantDocument(
        key = "knowledge:${article.id}",
        canonicalId = article.id,
        kind = AssistantDocumentKind.KNOWLEDGE_ARTICLE,
        family = inferFamily(article),
        section = TechnicalSection.KNOWLEDGE,
        title = article.title,
        summary = article.summary,
        body = article.body.joinToString(" "),
        aliases = buildSet {
            add(article.title)
            addAll(article.tags)
        },
        componentIds = emptySet(),
        symptomTerms = emptySet(),
        tags = article.tags.toSet() + article.category + article.status,
        relatedIds = article.relatedArticleIds.toSet(),
        safetyCritical = article.status.contains("авар", ignoreCase = true) ||
            article.tags.any { tag ->
                tag.contains("безопас", ignoreCase = true) ||
                    tag.contains("пожар", ignoreCase = true)
            },
        target = AssistantTarget.Knowledge(article.id)
    )

    private fun inferFamily(article: KnowledgeArticle): TechnicalFamily? {
        val haystack = buildString {
            append(article.category)
            append(' ')
            append(article.title)
            append(' ')
            append(article.tags.joinToString(" "))
        }.normalizeAssistantText()

        return when {
            "вл80" in haystack -> TechnicalFamily.VL80S
            "ермак" in haystack || "2эс5к" in haystack || "3эс5к" in haystack -> TechnicalFamily.ERMAK
            else -> null
        }
    }
}
