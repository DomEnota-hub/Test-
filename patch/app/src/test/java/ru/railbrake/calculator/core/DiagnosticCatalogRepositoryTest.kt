package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCatalogRepositoryTest {
    @Test
    fun expansion_containsExpectedNumberOfGranularScenarios() {
        assertEquals(48, DiagnosticExpansionRepository.scenarios.size)
        assertTrue(DiagnosticExpansionRepository.scenarios.all { it.id.startsWith("ext-") })
    }

    @Test
    fun combinedCatalog_hasAtLeastSeventyFiveUniqueScenarios() {
        val scenarios = DiagnosticCatalogRepository.scenarios

        assertTrue("expected at least 75 scenarios, got ${scenarios.size}", scenarios.size >= 75)
        assertEquals(scenarios.size, scenarios.map { it.id }.distinct().size)
    }

    @Test
    fun expandedScenarios_haveCompleteTriageAndSafetyContent() {
        DiagnosticExpansionRepository.scenarios.forEach { scenario ->
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
    }

    @Test
    fun highVoltageExpansion_keepsAuthorizedBoundary() {
        DiagnosticExpansionRepository.scenarios
            .filter { it.category == "Высоковольтные цепи" || it.category == "Силовое оборудование" || it.category == "Тяговые цепи" }
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
