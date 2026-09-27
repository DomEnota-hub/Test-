package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticP0DeepeningBatch3Test {
    private val ids = setOf(
        "gv-air-loss",
        "transformer-oil-signs",
        "traction-one-section-low",
        "traction-intermittent",
        "reverser-no-confirm",
        "traction-motor-overheat",
        "rectifier-overheat"
    )

    @Test
    fun thirdP0BatchUsesSubjectSpecificTrees() {
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
    fun sectionLowTractionSeparatesPhysicalEffectFromIndication() {
        val scenario = DiagnosticRepository.scenario("traction-one-section-low")!!
        val text = scenario.questions.joinToString(" ") { it.text + " " + it.yesMeaning + " " + it.noMeaning }
        assertTrue(text.contains("фактичес", ignoreCase = true))
        assertTrue(text.contains("прибор", ignoreCase = true) || text.contains("измер", ignoreCase = true))
        assertTrue("traction-current-imbalance" in scenario.relatedScenarioIds)
    }

    @Test
    fun reverserRouteForbidsTractionWithUnknownDirection() {
        val scenario = DiagnosticRepository.scenario("reverser-no-confirm")!!
        val text = (scenario.questions.flatMap { listOf(it.text, it.yesMeaning, it.noMeaning) } + scenario.checks.flatMap { listOf(it.action, it.ifAbnormal) }).joinToString(" ")
        assertTrue(text.contains("нулев", ignoreCase = true))
        assertTrue(text.contains("не набирать тягу", ignoreCase = true) || text.contains("тягу не собирать", ignoreCase = true))
    }

    @Test
    fun overheatingRoutesDoNotUseRepeatLoadAsAProbe() {
        listOf("transformer-oil-signs", "traction-motor-overheat", "rectifier-overheat").forEach { id ->
            val scenario = DiagnosticRepository.scenario(id)!!
            val text = (scenario.questions.flatMap { listOf(it.yesMeaning, it.noMeaning) } + scenario.checks.flatMap { listOf(it.action, it.ifAbnormal) }).joinToString(" ")
            assertTrue("$id: restrict operation", scenario.severity == DiagnosticSeverity.RESTRICT_OPERATION || scenario.severity == DiagnosticSeverity.STOP_AND_REPORT)
            assertTrue("$id: no repeat-load probing", text.contains("не повтор", ignoreCase = true) || text.contains("не подавать нагрузку", ignoreCase = true) || text.contains("не нагруж", ignoreCase = true))
            assertTrue("$id: emergency link", "smoke-fire-flashover" in scenario.relatedScenarioIds)
        }
    }
}
