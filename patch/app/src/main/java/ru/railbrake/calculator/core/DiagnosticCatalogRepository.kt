package ru.railbrake.calculator.core

/** Single catalog used by the UI. Keeps the original validated cards intact and adds granular symptom cards. */
object DiagnosticCatalogRepository {
    val safetyNotice: String get() = DiagnosticRepository.safetyNotice

    val scenarios: List<DiagnosticScenario> by lazy {
        (
            DiagnosticRepository.scenarios +
                DiagnosticExpansionRepository.scenarios +
                DiagnosticExpansionRepository2.scenarios
        ).distinctBy { it.id }
    }

    val categories: List<String> get() = listOf("Все") + scenarios.map { it.category }.distinct()

    fun search(query: String, category: String = "Все"): List<DiagnosticScenario> {
        val q = query.trim().lowercase()
        return scenarios.filter { scenario ->
            (category == "Все" || scenario.category == category) &&
                (q.isBlank() || listOf(
                    scenario.title,
                    scenario.summary,
                    scenario.category,
                    scenario.relatedEquipment.joinToString(" "),
                    scenario.probableCauses.joinToString(" "),
                    scenario.immediateActions.joinToString(" "),
                    scenario.dangerSigns.joinToString(" ")
                ).joinToString(" ").lowercase().contains(q))
        }
    }
}
