package ru.railbrake.calculator.core.assistant

import android.content.Context
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.ErmakDiagnosticRepository
import ru.railbrake.calculator.core.Chme3DiagnosticRepository
import ru.railbrake.calculator.core.ErmakDiagnosticScenario
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
    fun loadCore(family: TechnicalFamily? = null, variantId: String? = null): List<AssistantDocument> {
        val appContext = context.applicationContext

        val vl80Diagnostics = if (family == null || family == TechnicalFamily.VL80S) {
            DiagnosticRepository.scenarios.map(Vl80DiagnosticAssistantAdapter::adapt)
        } else emptyList()

        val ermakDiagnostics = if (family == null || family == TechnicalFamily.ERMAK) {
            ErmakDiagnosticRepository(appContext).scenarios()
                .filter { it.availableForAssistantVariant(variantId) }
                .map(ErmakDiagnosticAssistantAdapter::adapt)
        } else emptyList()

        val chme3Diagnostics = (family?.let { listOf(it) } ?: TechnicalFamily.entries)
            .filter { it.isChme3 }
            .flatMap { selected ->
                Chme3DiagnosticRepository(appContext).scenarios(selected).map { scenario ->
                    Chme3DiagnosticAssistantAdapter.adapt(scenario, selected)
                }
            }

        val knowledge = KnowledgeRepository.articles
            .map(KnowledgeArticleAssistantAdapter::adapt)
            .filter { family == null || it.family == null || it.family == family }

        val firstAid = firstAidTopics.map(FirstAidAssistantAdapter::adapt)
        val firstAidKit = firstAidKitDocument()

        return (vl80Diagnostics + ermakDiagnostics + chme3Diagnostics + knowledge + firstAid + firstAidKit)
            .distinctBy(AssistantDocument::key)
    }

    fun loadAdditionalCatalog(family: TechnicalFamily? = null): List<AssistantDocument> {
        val appContext = context.applicationContext
        val technicalRepository = TechnicalDataRepository(appContext)

        // Diagnostics are already represented by dedicated adapters. Profiles
        // remain intentionally outside assistant search.
        val indexedTechnicalSections = TechnicalSection.entries.filterNot { section ->
            section == TechnicalSection.DIAGNOSTICS || section == TechnicalSection.PROFILES
        }
        return (family?.let { listOf(it) } ?: TechnicalFamily.entries).flatMap { catalogFamily ->
            indexedTechnicalSections.flatMap { section ->
                technicalRepository.entries(catalogFamily, section)
            }
        }.map(TechnicalEntryAssistantAdapter::adapt)
            .distinctBy(AssistantDocument::key)
    }

    fun load(family: TechnicalFamily? = null): List<AssistantDocument> =
        (loadCore(family) + loadAdditionalCatalog(family)).distinctBy(AssistantDocument::key)
}

/** Source-declared applicability; an empty set means no model restriction. */
internal fun ErmakDiagnosticScenario.availableForAssistantVariant(variantId: String?): Boolean =
    variantId == null || applicability.families.isEmpty() || variantId in applicability.families

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
