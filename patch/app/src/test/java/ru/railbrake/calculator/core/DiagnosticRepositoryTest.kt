package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRepositoryTest {
    @Test
    fun scenarios_haveUniqueIdsAndCompleteSafetyContent() {
        val scenarios = DiagnosticRepository.scenarios

        assertTrue(scenarios.size >= 25)
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
            assertEquals(scenario.questions.size, scenario.questions.map { it.key }.distinct().size)
            scenario.checks.forEach { check ->
                assertTrue(check.action.isNotBlank())
                assertTrue(check.expected.isNotBlank())
                assertTrue(check.ifAbnormal.isNotBlank())
            }
        }
    }

    @Test
    fun expandedScenarios_haveExplanationsFeedbackAndValidLinks() {
        val expanded = DiagnosticRepository.scenarios.filter {
            it.observableSigns.isNotEmpty() || it.systemExplanation.isNotEmpty()
        }

        assertTrue(expanded.size >= 16)
        expanded.forEach { scenario ->
            assertFalse("${scenario.id}: observable signs", scenario.observableSigns.isEmpty())
            assertFalse("${scenario.id}: explanation", scenario.systemExplanation.isEmpty())
            assertFalse("${scenario.id}: consequences", scenario.operationalConsequences.isEmpty())
            assertFalse("${scenario.id}: feedback", scenario.feedbackPrompts.isEmpty())
            scenario.relatedScenarioIds.forEach { relatedId ->
                assertTrue("${scenario.id}: missing link $relatedId", DiagnosticRepository.scenario(relatedId) != null)
            }
            scenario.questions.forEach { question ->
                listOfNotNull(question.yesNextKey, question.noNextKey)
                    .filter { it != DiagnosticRepository.END_OF_FLOW }
                    .forEach { target ->
                        assertTrue(
                            "${scenario.id}: missing branch $target",
                            scenario.questions.any { it.key == target }
                        )
                    }
            }
        }
    }

    @Test
    fun branchingEngine_followsExplicitRouteAndStops() {
        val scenario = DiagnosticRepository.scenario("control-voltage-low")!!
        val first = scenario.questions.first()
        val second = DiagnosticRepository.nextQuestion(scenario, first.key, answerYes = true)
        val third = DiagnosticRepository.nextQuestion(scenario, second!!.key, answerYes = false)

        assertEquals("ctrl-section", second.key)
        assertEquals("ctrl-charge", third!!.key)
        assertEquals(null, DiagnosticRepository.nextQuestion(scenario, third.key, answerYes = true))
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
        assertTrue(DiagnosticRepository.search("АК-11Б").any { it.id == "compressor-no-start" })
        assertTrue(DiagnosticRepository.search("фазорасщепитель").any { it.id == "phase-splitter-no-start" })
        assertTrue(DiagnosticRepository.search("песок").any { it.id == "sanding-failure" })
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
