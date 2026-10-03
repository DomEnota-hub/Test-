package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedEmergencyRuntimePolicyTest {
    @Test
    fun registryResolvesEveryIntegratedProfileWithoutNullFallbacks() {
        val expected = mapOf(
            "chme3-base" to "chme3-family",
            "chme3t-rheostatic" to "chme3-family",
            "chme3e-electronic" to "chme3-family",
            "tem2-base" to "tem2-family",
            "tem2u-improved" to "tem2-family"
        )
        assertEquals(expected, LocomotiveProfileRegistry.registeredProfiles)
        expected.forEach { (profileId, familyId) ->
            val context = LocomotiveProfileRegistry.resolve(profileId)
            assertTrue(context.isExact)
            assertFalse(context.failClosed)
            assertEquals(profileId, context.profileId)
            assertEquals(familyId, context.familyId)
            assertTrue(LocomotiveProfileRegistry.isRegisteredExact(context))
        }
    }

    @Test
    fun legacyTechnicalFamilyMappingNeverReturnsNullAndFailsClosedWhenNotRegistered() {
        assertEquals("chme3-base", LocomotiveProfileRegistry.fromTechnicalFamily(TechnicalFamily.CHME3).profileId)
        assertEquals("chme3t-rheostatic", LocomotiveProfileRegistry.fromTechnicalFamily(TechnicalFamily.CHME3T).profileId)
        assertEquals("chme3e-electronic", LocomotiveProfileRegistry.fromTechnicalFamily(TechnicalFamily.CHME3E).profileId)

        val ermak = LocomotiveProfileRegistry.fromTechnicalFamily(TechnicalFamily.ERMAK)
        val vl80s = LocomotiveProfileRegistry.fromTechnicalFamily(TechnicalFamily.VL80S)
        assertTrue(ermak.failClosed)
        assertTrue(vl80s.failClosed)
        assertEquals(LocomotiveProfileRegistry.UNKNOWN_PROFILE_ID, ermak.profileId)
        assertEquals(LocomotiveProfileRegistry.UNKNOWN_PROFILE_ID, vl80s.profileId)
        assertFalse(LocomotiveProfileRegistry.isRegisteredExact(ermak))
        assertFalse(LocomotiveProfileRegistry.isRegisteredExact(vl80s))
    }

    @Test
    fun unknownOrCrossFamilyProfilesFailClosedInsteadOfInheritingAdjacentVariant() {
        val unknown = LocomotiveProfileRegistry.resolve("future-profile")
        assertTrue(unknown.failClosed)
        assertEquals(LocomotiveProfileRegistry.UNKNOWN_PROFILE_ID, unknown.profileId)

        val wrongFamily = LocomotiveProfileRegistry.resolve("tem2u-improved", "chme3-family")
        assertTrue(wrongFamily.failClosed)
        assertEquals("chme3-family", wrongFamily.familyId)
        assertEquals(LocomotiveProfileRegistry.UNKNOWN_PROFILE_ID, wrongFamily.profileId)

        val tem2 = LocomotiveProfileRegistry.resolve("tem2-base", "tem2-family")
        val tem2u = LocomotiveProfileRegistry.resolve("tem2u-improved", "tem2-family")
        assertEquals("tem2-base", tem2.profileId)
        assertEquals("tem2u-improved", tem2u.profileId)
        assertTrue(tem2.isExact)
        assertTrue(tem2u.isExact)
    }

    @Test
    fun safeResearchRecordsRemainNonExecutable() {
        assertTrue(
            ExtendedEmergencyRuntimeRepository.runtimeRecordAllowed(
                actionDisposition = "INFORMATION_ONLY",
                exactProfileOnly = true,
                executable = false,
                procedureVisible = false,
                currentAuthorityVerified = false,
                runtimeAuthorityUpgradeAllowed = false
            )
        )
        assertTrue(
            ExtendedEmergencyRuntimeRepository.runtimeRecordAllowed(
                actionDisposition = "PROHIBITED",
                exactProfileOnly = true,
                executable = false,
                procedureVisible = false,
                currentAuthorityVerified = false,
                runtimeAuthorityUpgradeAllowed = false
            )
        )
    }

    @Test
    fun anyAuthorityOrProcedureDriftFailsClosed() {
        assertFalse(
            ExtendedEmergencyRuntimeRepository.runtimeRecordAllowed(
                actionDisposition = "CONDITIONAL_ACTION",
                exactProfileOnly = true,
                executable = false,
                procedureVisible = false,
                currentAuthorityVerified = false,
                runtimeAuthorityUpgradeAllowed = false
            )
        )
        assertFalse(
            ExtendedEmergencyRuntimeRepository.runtimeRecordAllowed(
                actionDisposition = "INFORMATION_ONLY",
                exactProfileOnly = false,
                executable = false,
                procedureVisible = false,
                currentAuthorityVerified = false,
                runtimeAuthorityUpgradeAllowed = false
            )
        )
        assertFalse(
            ExtendedEmergencyRuntimeRepository.runtimeRecordAllowed(
                actionDisposition = "INFORMATION_ONLY",
                exactProfileOnly = true,
                executable = true,
                procedureVisible = false,
                currentAuthorityVerified = false,
                runtimeAuthorityUpgradeAllowed = false
            )
        )
        assertFalse(
            ExtendedEmergencyRuntimeRepository.runtimeRecordAllowed(
                actionDisposition = "PROHIBITED",
                exactProfileOnly = true,
                executable = false,
                procedureVisible = true,
                currentAuthorityVerified = false,
                runtimeAuthorityUpgradeAllowed = false
            )
        )
        assertFalse(
            ExtendedEmergencyRuntimeRepository.runtimeRecordAllowed(
                actionDisposition = "PROHIBITED",
                exactProfileOnly = true,
                executable = false,
                procedureVisible = false,
                currentAuthorityVerified = true,
                runtimeAuthorityUpgradeAllowed = false
            )
        )
        assertFalse(
            ExtendedEmergencyRuntimeRepository.runtimeRecordAllowed(
                actionDisposition = "PROHIBITED",
                exactProfileOnly = true,
                executable = false,
                procedureVisible = false,
                currentAuthorityVerified = false,
                runtimeAuthorityUpgradeAllowed = true
            )
        )
    }
}
