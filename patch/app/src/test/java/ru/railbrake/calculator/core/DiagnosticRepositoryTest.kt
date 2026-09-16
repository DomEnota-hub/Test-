package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRepositoryTest {
    @Test
    fun scenarios_haveUniqueIdsAndCompleteSafetyContent() {
        val scenarios = DiagnosticRepository.scenarios

        assertTrue(scenarios.size >= 10)
        assertEquals(scenarios.size, scenarios.map { it.id }.distinct().size)
        scenarios.forEach { scenario ->
            assertTrue(scenario.title.isNotBlank())
            assertFalse(scenario.immediateActions.isEmpty())
            assertFalse(scenario.dangerSigns.isEmpty())
            assertFalse(scenario.questions.isEmpty())
            assertFalse(scenario.probableCauses.isEmpty())
            assertFalse(scenario.checks.isEmpty())
            assertFalse(scenario.prohibited.isEmpty())
            assertFalse(scenario.stopConditions.isEmpty())
            assertFalse(scenario.reportFields.isEmpty())
            assertTrue(scenario.sourceNote.isNotBlank())
            assertTrue(scenario.applicability.isNotBlank())
            scenario.checks.forEach { check ->
                assertTrue(check.action.isNotBlank())
                assertTrue(check.expected.isNotBlank())
                assertTrue(check.ifAbnormal.isNotBlank())
            }
        }
    }

    @Test
    fun highVoltageScenarios_requireAuthorizedPersonnel() {
        val ids = setOf("pantograph-no-rise", "gv-no-close", "traction-no-assemble", "ekg-stuck", "protection-trip")

        DiagnosticRepository.scenarios.filter { it.id in ids }.forEach { scenario ->
            assertTrue(
                "${scenario.id} must contain an authorized-only boundary",
                scenario.checks.any { it.level == DiagnosticActionLevel.AUTHORIZED_ONLY }
            )
        }
    }

    @Test
    fun search_findsCommonEquipmentNames() {
        assertTrue(DiagnosticRepository.search("№395").any { it.id == "brakes-no-apply-release" })
        assertTrue(DiagnosticRepository.search("ЭКГ").any { it.id == "ekg-stuck" })
        assertTrue(DiagnosticRepository.search("БУРТ").any { it.id == "rheostatic-brake" })
        assertTrue(DiagnosticRepository.search("АЛСН").any { it.id == "alsn-epk" })
    }

    @Test
    fun fireScenario_containsRequiredElectricalSafetyDistances() {
        val fire = DiagnosticRepository.scenarios.single { it.id == "smoke-fire-flashover" }
        val text = listOf(
            fire.immediateActions,
            fire.dangerSigns,
            fire.probableCauses,
            fire.prohibited,
            fire.stopConditions,
            fire.questions.flatMap { listOf(it.text, it.yesMeaning, it.noMeaning) },
            fire.checks.flatMap { listOf(it.action, it.expected, it.ifAbnormal) }
        ).flatten().joinToString(" ")

        assertTrue(text.contains("2 м"))
        assertTrue(text.contains("8 м"))
        assertTrue(text.contains("50 м"))
    }
}
