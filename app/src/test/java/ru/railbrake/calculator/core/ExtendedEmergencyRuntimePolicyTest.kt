package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedEmergencyRuntimePolicyTest {
    @Test
    fun profileMappingIsExactAndDoesNotFallBackAcrossVariants() {
        assertEquals("chme3-base", expandedEmergencyProfileId(TechnicalFamily.CHME3))
        assertEquals("chme3t-rheostatic", expandedEmergencyProfileId(TechnicalFamily.CHME3T))
        assertEquals("chme3e-electronic", expandedEmergencyProfileId(TechnicalFamily.CHME3E))
        assertNull(expandedEmergencyProfileId(TechnicalFamily.ERMAK))
        assertNull(expandedEmergencyProfileId(TechnicalFamily.VL80S))
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
