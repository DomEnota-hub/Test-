package ru.railbrake.calculator.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveSchematicsTest {
    @Test
    fun electricalTrainerCoversRequiredFunctionalCircuits() {
        assertEquals(
            listOf("Тяга", "Подъём ТП", "Вспомогательные", "Реостатный тормоз", "Защита"),
            electricalScenarioTitles()
        )
        assertTrue(electricalScenarioStepCounts().all { it >= 4 })
    }

    @Test
    fun everyElectricalComponentExplainsItsRelationships() {
        assertTrue(allElectricalComponentsHaveDetails())
    }

    @Test
    fun pneumaticRoutesMatchScenarioStepCountsAndSourceBounds() {
        assertEquals(listOf(4, 7, 4, 4), pneumaticRouteStepCounts())
        assertTrue(allPneumaticRoutePointsFitSourceImage())
        assertTrue(pneumaticRoutesExposeFlowReleaseAndControl())
    }
}
