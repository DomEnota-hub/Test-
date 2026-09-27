package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticDecisionRouteTest {
    @Test
    fun unsafeGvAndEkgAnswersEndTheOrdinaryQuestionRoute() {
        val gv = DiagnosticRepository.scenario("gv-no-open")!!
        assertEquals("gv-open-danger", gv.questions.first().key)
        assertNull(DiagnosticRepository.nextQuestion(gv, "gv-open-danger", DiagnosticResponse.YES))
        assertEquals("gv-open-indication", DiagnosticRepository.nextQuestion(gv, "gv-open-danger", DiagnosticResponse.NO)?.key)

        val ekg = DiagnosticRepository.scenario("ekg-slow-transition")!!
        assertEquals("ekg-slow-protection", ekg.questions.first().key)
        assertNull(DiagnosticRepository.nextQuestion(ekg, "ekg-slow-protection", DiagnosticResponse.YES))
        assertNull(DiagnosticRepository.nextQuestion(ekg, "ekg-slow-finish", DiagnosticResponse.NO))
        assertEquals("ekg-slow-feedback", DiagnosticRepository.nextQuestion(ekg, "ekg-slow-finish", DiagnosticResponse.YES)?.key)
    }

    @Test
    fun compressorDistinguishesFailedCutoffFromUncertainPressureReading() {
        val scenario = DiagnosticRepository.scenario("compressor-long-run")!!
        assertTrue(scenario.questions.size > 3)
        assertNull(DiagnosticRepository.nextQuestion(scenario, "long-cutoff", DiagnosticResponse.YES))
        assertEquals("long-gauge", DiagnosticRepository.nextQuestion(scenario, "long-cutoff", DiagnosticResponse.NO)?.key)
    }

    @Test
    fun unknownAnswerContinuesOnlyWithAnIndependentQuestionInDeepenedRoutes() {
        for (id in listOf("gv-no-open", "ekg-slow-transition", "compressor-long-run")) {
            val scenario = DiagnosticRepository.scenario(id)!!
            val first = scenario.questions.first()
            val next = DiagnosticRepository.nextQuestion(scenario, first.key, DiagnosticResponse.UNKNOWN)
            assertTrue(id, next != null)
            assertTrue(id, next?.key != first.key)
            assertTrue(id, first.unknownMeaning.contains(first.yesMeaning))
            assertTrue(id, first.unknownMeaning.contains(first.noMeaning))
        }
    }

    @Test
    fun physicalFireAndHighVoltageDamageDoNotContinueRoutineQuestions() {
        val emergencyFirstQuestions = mapOf(
            "traction-motor-flashover" to "flash-physical",
            "machine-room-smoke" to "smoke-active",
            "roof-insulation-signs" to "roof-physical",
            "fire-loop-fault" to "flf-physical",
            "compressor-overheat" to "co-danger",
            "cab-heating-smell" to "chs-smoke",
            "relay-panel-overheat" to "rpo-danger",
            "extinguisher-system-fault" to "ext-fire"
        )
        for ((id, key) in emergencyFirstQuestions) {
            val scenario = DiagnosticRepository.scenario(id)!!
            assertEquals(id, key, scenario.questions.first().key)
            assertNull(id, DiagnosticRepository.nextQuestion(scenario, key, DiagnosticResponse.YES))
            assertTrue(id, DiagnosticRepository.nextQuestion(scenario, key, DiagnosticResponse.NO) != null)
        }
    }
}
