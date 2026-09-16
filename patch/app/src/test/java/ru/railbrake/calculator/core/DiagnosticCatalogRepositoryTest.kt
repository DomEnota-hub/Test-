package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCatalogRepositoryTest {
    private val expansionScenarios: List<DiagnosticScenario>
        get() = DiagnosticExpansionRepository.scenarios + DiagnosticExpansionRepository2.scenarios

    @Test
    fun expansion_containsExpectedNumberOfGranularScenarios() {
        assertEquals(48, DiagnosticExpansionRepository.scenarios.size)
        assertEquals(20, DiagnosticExpansionRepository2.scenarios.size)
        assertEquals(68, expansionScenarios.size)
        assertTrue(expansionScenarios.all { it.id.startsWith("ext-") })
        assertEquals(expansionScenarios.size, expansionScenarios.map { it.id }.distinct().size)
    }

    @Test
    fun combinedCatalog_hasAtLeastSeventyFiveUniqueScenarios() {
        val scenarios = DiagnosticCatalogRepository.scenarios

        assertTrue("expected at least 75 scenarios, got ${scenarios.size}", scenarios.size >= 75)
        assertEquals(scenarios.size, scenarios.map { it.id }.distinct().size)
    }

    @Test
    fun expandedScenarios_haveCompleteTriageAndSafetyContent() {
        expansionScenarios.forEach { scenario ->
            assertTrue(scenario.title.isNotBlank())
            assertFalse(scenario.immediateActions.isEmpty())
            assertTrue(scenario.dangerSigns.size >= 2)
            assertTrue(scenario.questions.size >= 2)
            assertTrue(scenario.probableCauses.size >= 3)
            assertTrue(scenario.checks.size >= 2)
            assertFalse(scenario.prohibited.isEmpty())
            assertFalse(scenario.stopConditions.isEmpty())
            assertFalse(scenario.reportFields.isEmpty())
            assertFalse(scenario.relatedEquipment.isEmpty())
            assertTrue(scenario.sourceNote.isNotBlank())
            assertTrue(scenario.checks.any { it.level != DiagnosticActionLevel.CAB })
        }
    }

    @Test
    fun combinedSearch_findsGranularSymptomsAndEquipment() {
        assertTrue(DiagnosticCatalogRepository.search("букс").any { it.id == "ext-axlebox-overheat" })
        assertTrue(DiagnosticCatalogRepository.search("песочниц").any { it.id == "ext-sanding-no-work" })
        assertTrue(DiagnosticCatalogRepository.search("радиосвяз").any { it.id == "ext-radio-failure" })
        assertTrue(DiagnosticCatalogRepository.search("маслонасос").any { it.id == "ext-transformer-pump" })
        assertTrue(DiagnosticCatalogRepository.search("ползун").any { it.id == "ext-wheel-flat" })
        assertTrue(DiagnosticCatalogRepository.search("возбуждения").any { it.id == "ext-rheostatic-no-excitation" })
    }

    @Test
    fun hazardousElectricalExpansion_keepsAuthorizedBoundary() {
        expansionScenarios
            .filter { it.category == "Высоковольтные цепи" || it.category == "Силовое оборудование" }
            .forEach { scenario ->
                assertTrue(
                    "${scenario.id} must contain an authorised or stop boundary",
                    scenario.checks.any {
                        it.level == DiagnosticActionLevel.AUTHORIZED_ONLY || it.level == DiagnosticActionLevel.STOP
                    }
                )
            }
    }
}
