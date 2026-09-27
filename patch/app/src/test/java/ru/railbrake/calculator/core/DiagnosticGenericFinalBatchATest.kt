package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticGenericFinalBatchATest {
    private val ids = setOf(
        "field-weakening-fault", "fan-vibration", "aux-contactor-chatter",
        "heater-fault", "sand-nozzle-blocked"
    )

    @Test
    fun batchAUsesSubjectSpecificTrees() {
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
    fun fieldWeakeningSeparatesTransitionFromBaseTractionFault() {
        val s = DiagnosticRepository.scenario("field-weakening-fault")!!
        val text = s.questions.joinToString(" ") { it.text + " " + it.yesMeaning + " " + it.noMeaning }
        assertTrue(text.contains("До команды", ignoreCase = true))
        assertTrue(text.contains("подтверж", ignoreCase = true))
        assertTrue(s.relatedScenarioIds.contains("traction-current-surge"))
    }

    @Test
    fun heaterEscalatesFireSignsWithoutRepeatedReset() {
        val s = DiagnosticRepository.scenario("heater-fault")!!
        val text = (s.questions.flatMap { listOf(it.text, it.yesMeaning, it.noMeaning) } + s.checks.flatMap { listOf(it.action, it.ifAbnormal) }).joinToString(" ")
        assertTrue(text.contains("дым", ignoreCase = true))
        assertTrue(text.contains("не выполнять повтор", ignoreCase = true) || text.contains("не обходить", ignoreCase = true))
        assertTrue(s.relatedScenarioIds.contains("smoke-fire-flashover"))
    }

    @Test
    fun sandRouteDoesNotRequireUnsafeMovingInspection() {
        val s = DiagnosticRepository.scenario("sand-nozzle-blocked")!!
        val text = s.checks.joinToString(" ") { it.action + " " + it.ifAbnormal }
        assertTrue(text.contains("безопас", ignoreCase = true))
        assertTrue(text.contains("движущ", ignoreCase = true) || text.contains("закреп", ignoreCase = true))
    }
}
