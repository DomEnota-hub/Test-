package ru.railbrake.calculator.core.assistant

import android.content.Context
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.ErmakDiagnosticRepository
import ru.railbrake.calculator.core.KnowledgeRepository
import ru.railbrake.calculator.core.TechnicalDataRepository
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

/**
 * Read-only projection of the existing application repositories.
 * Call from a background dispatcher when wiring it into UI/runtime code.
 */
class AssistantContentLoader(
    private val context: Context
) {
    fun load(): List<AssistantDocument> {
        val appContext = context.applicationContext
        val technicalRepository = TechnicalDataRepository(appContext)

        // TechnicalDataRepository.entries intentionally exposes only catalog sections.
        // Assistant search also needs acceptance and safety, while diagnostics are
        // projected by their dedicated adapters to avoid duplicate diagnostic cards.
        val indexedTechnicalSections = TechnicalSection.entries.filterNot { section ->
            section == TechnicalSection.DIAGNOSTICS || section == TechnicalSection.PROFILES
        }
        val technical = TechnicalFamily.entries.flatMap { family ->
            indexedTechnicalSections.flatMap { section ->
                technicalRepository.entries(family, section)
            }
        }.map(TechnicalEntryAssistantAdapter::adapt)

        val vl80Diagnostics = DiagnosticRepository.scenarios
            .map(Vl80DiagnosticAssistantAdapter::adapt)

        val ermakDiagnostics = ErmakDiagnosticRepository(appContext)
            .scenarios()
            .map(ErmakDiagnosticAssistantAdapter::adapt)

        val knowledge = KnowledgeRepository.articles
            .map(KnowledgeArticleAssistantAdapter::adapt)

        return (technical + vl80Diagnostics + ermakDiagnostics + knowledge)
            .distinctBy(AssistantDocument::key)
    }
}
