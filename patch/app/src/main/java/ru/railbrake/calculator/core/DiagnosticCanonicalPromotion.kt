package ru.railbrake.calculator.core

/**
 * Explicit promotion seam for canonical VL80S scenarios that already exist in
 * the dormant granular catalog but are not part of the legacy runtime lists.
 *
 * Promotion reuses the existing scenario object verbatim: no diagnostic text
 * is copied or inferred here.
 */
internal object DiagnosticCanonicalPromotion {
    private val vl80sPromotionIds = listOf(
        "ext-coupler-damage"
    )

    val vl80sScenarios: List<DiagnosticScenario> = vl80sPromotionIds.map(::requireDormantScenario)

    private fun requireDormantScenario(id: String): DiagnosticScenario {
        val matches = DiagnosticExpansionRepository.scenarios.filter { it.id == id }
        require(matches.size == 1) {
            "Canonical promotion $id must resolve to exactly one dormant VL80S scenario; found ${matches.size}"
        }
        return matches.single()
    }
}
