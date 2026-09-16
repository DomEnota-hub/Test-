package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocomotiveProfileTest {
    @Test
    fun vl80sProfileHasVariantSafeDefaults() {
        val profile = LocomotiveProfiles.vl80s

        assertEquals(LocomotiveProfiles.VL80S_ID, profile.id)
        assertTrue(profile.variants.any { it.id == LocomotiveProfiles.VL80S_GENERAL })
        assertTrue(profile.variants.any { it.id == LocomotiveProfiles.VL80S_937_1260 })
        assertTrue(profile.variants.any { it.id == LocomotiveProfiles.VL80S_LATER })
        assertTrue(ProfileFeature.DIAGNOSTICS in profile.supportedFeatures)
        assertTrue(ProfileFeature.TRAINING_SIMULATOR in profile.supportedFeatures)
    }

    @Test
    fun observationSearchUnderstandsRailwayTermsAndLinksAreValid() {
        val (observations, equipment) = Vl80sObservationCatalog.search("главник")
        assertTrue(observations.any { it.id == "gv-lamp" })
        assertTrue(equipment.any { it.id == "gv" })

        Vl80sObservationCatalog.observations.forEach { observation ->
            assertFalse(observation.scenarioIds.isEmpty())
            observation.scenarioIds.forEach { assertNotNull(DiagnosticRepository.scenario(it)) }
            observation.equipmentIds.forEach { assertNotNull(Vl80sObservationCatalog.equipment(it)) }
        }
        Vl80sObservationCatalog.equipment.forEach { item ->
            item.scenarioIds.forEach { assertNotNull(DiagnosticRepository.scenario(it)) }
        }
    }

    @Test
    fun flowEngineHandlesUnknownWithoutInventingAnswer() {
        val scenario = DiagnosticRepository.scenario("control-voltage-low")!!
        val initial = DiagnosticDecisionEngine.start(scenario)
        val result = DiagnosticDecisionEngine.answer(scenario, initial, DiagnosticResponse.UNKNOWN)

        assertEquals(1, result.state.answers.size)
        assertEquals(DiagnosticResponse.UNKNOWN, result.state.answers.single().response)
        assertTrue(result.state.answers.single().conclusion.contains("Недостаточно данных"))
    }
}
