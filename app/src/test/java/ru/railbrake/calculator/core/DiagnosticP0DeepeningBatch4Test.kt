package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticP0DeepeningBatch4Test {
    private val ids = setOf(
        "pantograph-contact-arcing", "compressor-overheat", "control-fuse-trip",
        "epk-leak", "pressure-gauge-mismatch", "traction-gear-noise", "suspension-damage"
    )

    @Test
    fun fourthP0BatchUsesSubjectSpecificTrees() {
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
    fun repeatedControlProtectionCannotBecomeResetLoop() {
        val scenario = DiagnosticRepository.scenario("control-fuse-trip")!!
        val text = (scenario.questions.flatMap { listOf(it.text, it.yesMeaning, it.noMeaning) } + scenario.checks.flatMap { listOf(it.action, it.ifAbnormal) }).joinToString(" ")
        assertTrue(text.contains("повтор", ignoreCase = true))
        assertTrue(text.contains("не выполнять серию", ignoreCase = true) || text.contains("не продолжать многократ", ignoreCase = true))
        assertTrue(text.contains("не шунт", ignoreCase = true))
    }

    @Test
    fun pressureGaugeRouteSeparatesMeasurementFromPhysicalState() {
        val scenario = DiagnosticRepository.scenario("pressure-gauge-mismatch")!!
        val text = scenario.questions.joinToString(" ") { it.text + " " + it.yesMeaning + " " + it.noMeaning }
        assertTrue(text.contains("независим", ignoreCase = true))
        assertTrue(text.contains("прибор", ignoreCase = true))
        assertTrue(text.contains("реальн", ignoreCase = true))
    }

    @Test
    fun visibleSuspensionDamageIsStopAndReport() {
        val scenario = DiagnosticRepository.scenario("suspension-damage")!!
        assertEquals(DiagnosticSeverity.STOP_AND_REPORT, scenario.severity)
        assertTrue(scenario.questions.any { it.text.contains("перекос", ignoreCase = true) || it.text.contains("смещ", ignoreCase = true) })
    }
}
