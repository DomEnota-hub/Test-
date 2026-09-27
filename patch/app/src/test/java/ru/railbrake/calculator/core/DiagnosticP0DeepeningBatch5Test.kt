package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticP0DeepeningBatch5Test {
    private val ids = setOf(
        "pantograph-slow-rise", "pantograph-wrong-selection", "battery-overcharge",
        "cab-control-mismatch", "handbrake-not-release", "cab-heating-smell", "external-object-impact"
    )

    @Test
    fun fifthP0BatchUsesSubjectSpecificTrees() {
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
    fun controlMismatchSeparatesCommandIndicationAndPhysicalEffect() {
        val s = DiagnosticRepository.scenario("cab-control-mismatch")!!
        val text = s.questions.joinToString(" ") { it.text + " " + it.yesMeaning + " " + it.noMeaning }
        assertTrue(text.contains("фактичес", ignoreCase = true))
        assertTrue(text.contains("индикац", ignoreCase = true))
        assertTrue(text.contains("управ", ignoreCase = true))
    }

    @Test
    fun externalImpactStopsOnConfirmedMechanicalDamage() {
        val s = DiagnosticRepository.scenario("external-object-impact")!!
        assertEquals(DiagnosticSeverity.STOP_AND_REPORT, s.severity)
        assertTrue(s.relatedScenarioIds.contains("brake-rigging-damage"))
        assertTrue(s.relatedScenarioIds.contains("suspension-damage"))
    }
}
