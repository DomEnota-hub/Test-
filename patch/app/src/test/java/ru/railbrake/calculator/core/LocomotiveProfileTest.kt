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

    @Test
    fun equipmentRoutesHaveValidScenariosAndLocations() {
        Vl80sObservationCatalog.equipment.forEach { item ->
            assertTrue("${item.id}: purpose", item.purpose.isNotBlank())
            assertTrue("${item.id}: location", item.location.isNotBlank())
            assertTrue("${item.id}: connections", item.connections.isNotEmpty())
            assertTrue("${item.id}: diagnostic route", item.scenarioIds.isNotEmpty())
            item.scenarioIds.forEach { scenarioId ->
                assertNotNull("${item.id}: missing scenario $scenarioId", DiagnosticRepository.scenario(scenarioId))
            }
        }
    }

    @Test
    fun fastRouteTargetsAreAvailable() {
        val required = setOf(
            "gv-no-close",
            "traction-no-assemble",
            "aux-machines",
            "brake-pipe-leak",
            "alsn-epk",
            "smoke-fire-flashover"
        )
        required.forEach { scenarioId ->
            assertNotNull("quick route missing $scenarioId", DiagnosticRepository.scenario(scenarioId))
        }
    }

    @Test
    fun normalValuesAreSourcedAndMappedToKnownEquipment() {
        assertTrue(Vl80sNormalValues.all.isNotEmpty())
        Vl80sNormalValues.all.forEach { value ->
            assertTrue("${value.id}: value", value.normalValue.isNotBlank())
            assertTrue("${value.id}: source", value.source.isNotBlank())
            value.equipmentIds.forEach { id ->
                assertNotNull("${value.id}: unknown equipment $id", Vl80sObservationCatalog.equipment(id))
            }
        }
    }

    @Test
    fun generalApplicabilityIsSharedWhileConcreteVariantsRemainScoped() {
        assertTrue(
            LocomotiveProfiles.appliesToVariant(
                LocomotiveProfiles.VL80S_LATER,
                setOf(LocomotiveProfiles.VL80S_GENERAL)
            )
        )
        assertTrue(
            LocomotiveProfiles.appliesToVariant(
                LocomotiveProfiles.VL80S_937_1260,
                setOf(LocomotiveProfiles.VL80S_937_1260)
            )
        )
        assertFalse(
            LocomotiveProfiles.appliesToVariant(
                LocomotiveProfiles.VL80S_GENERAL,
                setOf(LocomotiveProfiles.VL80S_LATER)
            )
        )
    }
}