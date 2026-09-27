package ru.railbrake.calculator.core

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticGenericRouteRegressionTest {
    @Test
    fun strictGenericRoutesAreAbsent() {
        val genericRoot = "Признак относится только к одной секции, тележке или группе?"
        val strictGeneric = DiagnosticRepository.scenarios.filter { scenario ->
            scenario.questions.firstOrNull()?.text == genericRoot
        }
        val ids = strictGeneric.map { it.id }.sorted()
        println("VL80S_STRICT_GENERIC_IDS=" + ids.joinToString(","))
        println("VL80S_STRICT_GENERIC_ROUTES=" + ids.size)
        assertTrue("strict generic routes remain: ${ids.size}: $ids", ids.isEmpty())
    }
}
