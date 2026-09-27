package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticP0DeepeningBatch2Test {
    private val ids = setOf(
        "vvk-interlock-fault",
        "roof-insulation-signs",
        "alsn-code-loss",
        "vigilance-control-fault",
        "speed-indication-fault",
        "wheel-flat-impact",
        "brake-rigging-damage"
    )

    @Test
    fun secondP0BatchUsesSubjectSpecificTrees() {
        ids.forEach { id ->
            val scenario = DiagnosticRepository.scenario(id)!!
            assertEquals("$id: three-step route", 3, scenario.questions.size)
            assertEquals("$id: unique questions", 3, scenario.questions.map { it.text }.distinct().size)
            assertFalse("$id: generic first question", scenario.questions.first().text.contains("одной секции, тележке или группе"))
            assertTrue("$id: tailored causes", scenario.probableCauses.size >= 5)
            assertTrue("$id: related routes", scenario.relatedScenarioIds.size >= 4)
            assertTrue("$id: authorized boundary", scenario.checks.any { it.level == DiagnosticActionLevel.AUTHORIZED_ONLY })
        }
    }

    @Test
    fun roofInsulationEscalatesWithoutRoofAccess() {
        val scenario = DiagnosticRepository.scenario("roof-insulation-signs")!!
        val text = (scenario.immediateActions + scenario.checks.flatMap { listOf(it.action, it.ifAbnormal) }).joinToString(" ")
        assertEquals(DiagnosticSeverity.STOP_AND_REPORT, scenario.severity)
        assertTrue("smoke-fire-flashover" in scenario.relatedScenarioIds)
        assertTrue(text.contains("не выходить на крышу", ignoreCase = true) || text.contains("не поднимаясь на крышу", ignoreCase = true))
        assertTrue(text.contains("не повтор", ignoreCase = true))
    }

    @Test
    fun safetyRoutesSeparateSubsystemsFromGeneralFailure() {
        val alsn = DiagnosticRepository.scenario("alsn-code-loss")!!
        val vigilance = DiagnosticRepository.scenario("vigilance-control-fault")!!
        val speed = DiagnosticRepository.scenario("speed-indication-fault")!!

        listOf(alsn, vigilance, speed).forEach { scenario ->
            assertTrue("${scenario.id}: root safety link", "alsn-epk" in scenario.relatedScenarioIds)
            assertTrue("${scenario.id}: profile wording", scenario.checks.any { it.action.contains("комплекс") || it.action.contains("профиль") })
        }
        assertTrue("uncommanded-braking" in alsn.relatedScenarioIds)
        assertTrue("uncommanded-braking" in vigilance.relatedScenarioIds)
    }

    @Test
    fun wheelFlatRequiresObjectiveNormativeAssessment() {
        val scenario = DiagnosticRepository.scenario("wheel-flat-impact")!!
        val text = scenario.checks.flatMap { listOf(it.action, it.expected, it.ifAbnormal) }.joinToString(" ")
        assertTrue(text.contains("измер", ignoreCase = true))
        assertTrue(text.contains("норматив"))
        assertFalse(text.contains(" мм"))
    }
}
