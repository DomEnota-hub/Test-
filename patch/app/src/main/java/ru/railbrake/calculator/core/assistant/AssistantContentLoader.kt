package ru.railbrake.calculator.core.assistant

import android.content.Context
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.ErmakDiagnosticRepository
import ru.railbrake.calculator.core.KnowledgeRepository
import ru.railbrake.calculator.core.TechnicalDataRepository
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.ui.firstAidTopics
import ru.railbrake.calculator.ui.workerKitLines

/**
 * Read-only projection of the existing application repositories.
 *
 * Loading is deliberately split in two stages. Diagnostics, OPP and the small
 * built-in knowledge base form the core index and become usable first. The much
 * larger technical catalog is loaded afterwards so it cannot block access to
 * the assistant for tens of seconds on slower phones.
 */
class AssistantContentLoader(
    private val context: Context
) {
    fun loadCore(): List<AssistantDocument> {
        val appContext = context.applicationContext

        val vl80Diagnostics = DiagnosticRepository.scenarios
            .map(Vl80DiagnosticAssistantAdapter::adapt)

        val ermakDiagnostics = ErmakDiagnosticRepository(appContext)
            .scenarios()
            .map(ErmakDiagnosticAssistantAdapter::adapt)

        val knowledge = KnowledgeRepository.articles
            .map(KnowledgeArticleAssistantAdapter::adapt)

        val firstAid = firstAidTopics.map(FirstAidAssistantAdapter::adapt)
        val firstAidKit = firstAidKitDocument()

        return (vl80Diagnostics + ermakDiagnostics + knowledge + firstAid + firstAidKit)
            .distinctBy(AssistantDocument::key)
    }

    fun loadAdditionalCatalog(): List<AssistantDocument> {
        val appContext = context.applicationContext
        val technicalRepository = TechnicalDataRepository(appContext)

        // Diagnostics are already represented by dedicated adapters. Profiles
        // remain intentionally outside assistant search.
        val indexedTechnicalSections = TechnicalSection.entries.filterNot { section ->
            section == TechnicalSection.DIAGNOSTICS || section == TechnicalSection.PROFILES
        }
        return TechnicalFamily.entries.flatMap { family ->
            indexedTechnicalSections.flatMap { section ->
                technicalRepository.entries(family, section)
            }
        }.map(TechnicalEntryAssistantAdapter::adapt)
            .distinctBy(AssistantDocument::key)
    }

    fun load(): List<AssistantDocument> =
        (loadCore() + loadAdditionalCatalog()).distinctBy(AssistantDocument::key)
}

/** Shared by the runtime loader and the complete-catalog regression test. */
internal fun firstAidKitDocument(): AssistantDocument = AssistantDocument(
    key = "first-aid:kit",
    canonicalId = "kit",
    kind = AssistantDocumentKind.FIRST_AID,
    family = null,
    section = TechnicalSection.SAFETY,
    title = "Аптечка работника",
    summary = "Состав аптечки и средства первой помощи",
    body = workerKitLines.joinToString(" "),
    aliases = setOf("аптечка", "аптечка работника", "жгут", "бинт", "перчатки", "салфетки"),
    componentIds = emptySet(),
    symptomTerms = emptySet(),
    tags = setOf("первая помощь", "оказание первой помощи", "опп"),
    relatedIds = emptySet(),
    safetyCritical = true,
    target = AssistantTarget.FirstAid("kit")
)
