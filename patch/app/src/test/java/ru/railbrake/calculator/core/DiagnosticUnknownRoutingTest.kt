package ru.railbrake.calculator.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class DiagnosticUnknownRoutingTest {
    private fun ermakAsset(): JSONObject =
        JSONObject(
            GZIPInputStream(
                File("src/main/assets/technical/ermak_diagnostics.json.gz").inputStream()
            ).bufferedReader().use { it.readText() }
        )

    @Test
    fun ermakEveryQuestionHasExplicitQuestionSpecificUnknownRoute() {
        val scenarios = parseErmakDiagnostics(ermakAsset())
        val questions = scenarios.flatMap { scenario ->
            scenario.nodes.values.filter { it.type == "question" }.map { scenario to it }
        }

        assertEquals(136, scenarios.size)
        assertEquals(725, questions.size)
        questions.forEach { (scenario, question) ->
            val unknown = question.choices.filter { it.isUnknown }
            assertEquals("${scenario.id}/${question.id}", 1, unknown.size)
            assertEquals("Не знаю", unknown.single().label)
            val uncertainty = scenario.nodes[unknown.single().nextNodeId]
            assertNotNull("${scenario.id}/${question.id}", uncertainty)
            assertEquals("${scenario.id}/${question.id}", "uncertainty", uncertainty!!.type)
            assertTrue("${scenario.id}/${question.id}", uncertainty.text.contains(scenario.title))
            assertNotNull("${scenario.id}/${question.id}", uncertainty.nextNodeId)
            assertTrue(
                "${scenario.id}/${question.id}: missing continuation ${uncertainty.nextNodeId}",
                scenario.nodes.containsKey(uncertainty.nextNodeId)
            )
        }
    }

    @Test
    fun vl80sEveryQuestionHasExplicitUnknownSemanticsAndValidTarget() {
        val scenarios = DiagnosticRepository.scenarios
        assertEquals(103, scenarios.size)
        assertEquals(318, scenarios.sumOf { it.questions.size })

        scenarios.forEach { scenario ->
            scenario.questions.forEach { question ->
                val target = question.unknownNextKey
                assertNotNull("${scenario.id}/${question.key}: implicit UNKNOWN", target)
                assertTrue("${scenario.id}/${question.key}: weak UNKNOWN explanation", question.unknownMeaning.length >= 80)
                assertFalse(
                    "${scenario.id}/${question.key}: legacy generic UNKNOWN",
                    question.unknownMeaning == "Состояние считать неподтверждённым; расширять действия нельзя." ||
                        question.unknownMeaning.startsWith("Недостаточно данных: не делать вывод")
                )
                if (target != DiagnosticRepository.END_OF_FLOW) {
                    assertTrue(
                        "${scenario.id}/${question.key}: missing UNKNOWN target $target",
                        scenario.questions.any { it.key == target }
                    )
                    assertFalse("${scenario.id}/${question.key}: UNKNOWN self-loop", target == question.key)
                }
            }
        }
    }

    @Test
    fun unknownKeepsBothHypothesisGroupsAndUsesIndependentNextQuestion() {
        val scenario = DiagnosticRepository.scenario("gv-no-close")!!
        val scope = scenario.questions.first { it.key == "gvc-scope" }

        assertTrue(scope.unknownMeaning.contains(scope.yesMeaning))
        assertTrue(scope.unknownMeaning.contains(scope.noMeaning))
        assertEquals("gvc-voltage", DiagnosticRepository.nextQuestion(scenario, scope.key, DiagnosticResponse.UNKNOWN)?.key)
        assertTrue(DiagnosticRepository.candidateCauseIds(scope, DiagnosticResponse.UNKNOWN).isNotEmpty())
    }
}
