package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticP0DeepeningBatch6Test {
    private val ids = setOf(
        "intersection-command-loss", "relay-panel-overheat", "main-reservoir-slow-fill",
        "feed-line-leak", "battery-no-charge", "radio-communication-loss", "fire-loop-fault"
    )

    @Test
    fun sixthP0BatchUsesSubjectSpecificTrees() {
        ids.forEach { id ->
            val s = DiagnosticRepository.scenario(id)!!
            assertEquals("$id: three-step route", 3, s.questions.size)
            assertEquals("$id: unique questions", 3, s.questions.map { it.text }.distinct().size)
            assertFalse("$id: generic root", s.questions.first().text == "Признак относится только к одной секции, тележке или группе?")
            assertTrue("$id: causes", s.probableCauses.size >= 5)
            assertTrue("$id: links", s.relatedScenarioIds.size >= 4)
            assertTrue("$id: authorization boundary", s.checks.any { it.level == DiagnosticActionLevel.AUTHORIZED_ONLY })
        }
    }

    @Test
    fun fireLoopNeverDismissesPhysicalFireSigns() {
        val s = DiagnosticRepository.scenario("fire-loop-fault")!!
        val text = (s.questions.flatMap { listOf(it.text, it.yesMeaning, it.noMeaning) } + s.checks.flatMap { listOf(it.action, it.ifAbnormal) }).joinToString(" ")
        assertTrue(text.contains("физичес", ignoreCase = true))
        assertTrue(text.contains("пожарн", ignoreCase = true))
        assertTrue("smoke-fire-flashover" in s.relatedScenarioIds)
    }

    @Test
    fun feedLeakEscalatesWhenAirReserveCannotBeMaintained() {
        val s = DiagnosticRepository.scenario("feed-line-leak")!!
        assertEquals(DiagnosticSeverity.STOP_AND_REPORT, s.severity)
        assertTrue(s.questions.any { it.text.contains("запас воздуха", ignoreCase = true) })
    }

    @Test
    fun radioSeparatesOnboardFailureFromExternalCoverage() {
        val s = DiagnosticRepository.scenario("radio-communication-loss")!!
        val text = s.questions.joinToString(" ") { it.text + " " + it.yesMeaning + " " + it.noMeaning }
        assertTrue(text.contains("внешн", ignoreCase = true) || s.probableCauses.any { it.contains("внешн", ignoreCase = true) })
        assertTrue(text.contains("приём", ignoreCase = true))
        assertTrue(text.contains("передач", ignoreCase = true))
    }
}
