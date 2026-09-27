package ru.railbrake.calculator.core

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticUniversalRouteAuditTest {
    private val legacyGenericQuestions = setOf(
        "Признак относится только к одной секции, тележке или группе?",
        "Симптом устойчиво относится только к одной секции, группе или аппарату?",
        "Признак устойчиво повторяется при одинаковом безопасном наблюдении?",
        "Есть опасный нагрев, дым, дуга, утечка, разрушение или защитное отключение?"
    )

    @Test
    fun legacyUniversalTemplateIsAbsentEverywhere() {
        val hits = DiagnosticRepository.scenarios.flatMap { scenario ->
            scenario.questions.filter { it.text in legacyGenericQuestions }.map { scenario.id + "::" + it.text }
        }
        println("VL80S_LEGACY_GENERIC_HITS=" + hits.joinToString(" | "))
        assertTrue("legacy generic questions remain: $hits", hits.isEmpty())
    }

    @Test
    fun completeQuestionTreesAreNotDuplicatedAcrossScenarios() {
        val duplicates = DiagnosticRepository.scenarios
            .filter { it.questions.isNotEmpty() }
            .groupBy { scenario -> scenario.questions.map { it.text.trim().lowercase() } }
            .filterValues { it.size > 1 }
            .values
            .map { group -> group.map { it.id }.sorted() }
            .sortedBy { it.joinToString(",") }
        println("VL80S_DUPLICATE_QUESTION_TREES=" + duplicates.joinToString(" | "))
        assertTrue("duplicate full question trees remain: $duplicates", duplicates.isEmpty())
    }
}
