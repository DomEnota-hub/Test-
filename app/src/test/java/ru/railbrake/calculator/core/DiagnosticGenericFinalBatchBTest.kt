package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticGenericFinalBatchBTest {
    private val ids = setOf("headlight-fault", "wiper-fault", "door-lock-fault", "extinguisher-system-fault")

    @Test
    fun finalBatchUsesSubjectSpecificTrees() {
        ids.forEach { id ->
            val s = DiagnosticRepository.scenario(id)!!
            assertEquals("$id: three-step route", 3, s.questions.size)
            assertEquals("$id: unique questions", 3, s.questions.map { it.text }.distinct().size)
            assertFalse("$id: generic root", s.questions.first().text == "Признак относится только к одной секции, тележке или группе?")
            assertTrue("$id: causes", s.probableCauses.size >= 5)
            assertTrue("$id: authorization boundary", s.checks.any { it.level == DiagnosticActionLevel.AUTHORIZED_ONLY })
        }
    }

    @Test
    fun doorRouteSeparatesVvkFromOrdinaryDoor() {
        val s = DiagnosticRepository.scenario("door-lock-fault")!!
        val text = s.questions.joinToString(" ") { it.text + " " + it.yesMeaning + " " + it.noMeaning }
        assertTrue(text.contains("ВВК", ignoreCase = true))
        assertTrue(text.contains("обычн", ignoreCase = true))
        assertTrue("vvk-interlock-fault" in s.relatedScenarioIds)
    }

    @Test
    fun extinguisherChecksRealFireBeforeReadiness() {
        val s = DiagnosticRepository.scenario("extinguisher-system-fault")!!
        val first = s.questions.first().text
        assertTrue(first.contains("дым", ignoreCase = true) || first.contains("огонь", ignoreCase = true))
        assertTrue("smoke-fire-flashover" in s.relatedScenarioIds)
        val checks = s.checks.joinToString(" ") { it.action + " " + it.ifAbnormal }
        assertTrue(checks.contains("не проверять", ignoreCase = true) || checks.contains("не шунтировать", ignoreCase = true))
    }

    @Test
    fun wiperRouteEscalatesLossOfVisibility() {
        val s = DiagnosticRepository.scenario("wiper-fault")!!
        val text = s.questions.joinToString(" ") { it.text + " " + it.yesMeaning + " " + it.noMeaning }
        assertTrue(text.contains("обзор", ignoreCase = true))
        assertTrue(text.contains("безопас", ignoreCase = true))
    }

    @Test
    fun headlightSeparatesCommonFromLocalLightingFault() {
        val s = DiagnosticRepository.scenario("headlight-fault")!!
        val text = s.questions.joinToString(" ") { it.text + " " + it.yesMeaning + " " + it.noMeaning }
        assertTrue(text.contains("прожектор", ignoreCase = true))
        assertTrue(text.contains("буфер", ignoreCase = true))
        assertTrue(text.contains("защит", ignoreCase = true))
    }
}
