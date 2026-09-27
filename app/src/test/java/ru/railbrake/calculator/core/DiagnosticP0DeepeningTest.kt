package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticP0DeepeningTest {
    private val deepenedIds = setOf(
        "gv-no-open",
        "ekg-position-mismatch",
        "ekg-slow-transition",
        "traction-current-surge",
        "traction-motor-flashover",
        "machine-room-smoke",
        "wheel-bearing-heat"
    )

    @Test
    fun firstP0BatchUsesSubjectSpecificTrees() {
        deepenedIds.forEach { id ->
            val scenario = DiagnosticRepository.scenario(id)!!
            assertTrue("$id: enough diagnostic depth", scenario.questions.size >= 3)
            assertEquals("$id: unique questions", scenario.questions.size, scenario.questions.map { it.text }.distinct().size)
            assertFalse("$id: generic first question", scenario.questions.first().text.contains("одной секции, тележке или группе"))
            assertTrue("$id: tailored causes", scenario.probableCauses.size >= 5)
            assertTrue("$id: related routes", scenario.relatedScenarioIds.size >= 4)
            assertTrue("$id: authorized boundary", scenario.checks.any { it.level == DiagnosticActionLevel.AUTHORIZED_ONLY })
        }
    }

    @Test
    fun smokeAndFlashoverEscalateToEmergencyRoute() {
        listOf("traction-motor-flashover", "machine-room-smoke").forEach { id ->
            val scenario = DiagnosticRepository.scenario(id)!!
            assertEquals("$id: severity", DiagnosticSeverity.STOP_AND_REPORT, scenario.severity)
            assertTrue("$id: fire route", "smoke-fire-flashover" in scenario.relatedScenarioIds)
            assertTrue("$id: no repeat energizing", scenario.immediateActions.any { it.contains("не") && (it.contains("повтор") || it.contains("ВВК")) })
        }
    }

    @Test
    fun ekgIndicationAndActualMismatchRemainSeparate() {
        val indication = DiagnosticRepository.scenario("ekg-position-mismatch")!!
        val desync = DiagnosticRepository.scenario("vl80s-ekg-section-desync")!!

        assertTrue(indication.questions.first().text.contains("указателю") || indication.questions.first().text.contains("сигнализации"))
        assertTrue("vl80s-ekg-section-desync" in indication.relatedScenarioIds)
        assertTrue(desync.id != indication.id)
    }

    @Test
    fun currentSurgeEscalatesPhysicalDamageWithoutReproduction() {
        val scenario = DiagnosticRepository.scenario("traction-current-surge")!!
        val text = (scenario.questions.flatMap { listOf(it.text, it.yesMeaning, it.noMeaning) } +
            scenario.checks.flatMap { listOf(it.action, it.expected, it.ifAbnormal) }).joinToString(" ")

        assertTrue(text.contains("переход"))
        assertTrue(text.contains("защит"))
        assertTrue(text.contains("не") && (text.contains("повтор") || text.contains("воспроиз")))
    }
}
