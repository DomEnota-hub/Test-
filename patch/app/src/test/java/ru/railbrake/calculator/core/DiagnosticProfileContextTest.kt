package ru.railbrake.calculator.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticProfileContextTest {
    private val profileAction = DiagnosticActionMetadata(
        riskClass = "normal",
        userFacingPolicy = DiagnosticUserFacingPolicy.SOURCE_AND_PROFILE_REQUIRED,
        rawUserFacingPolicy = "SOURCE_AND_PROFILE_REQUIRED",
        sourceBound = true
    )

    private fun decision(applicability: DiagnosticApplicability, context: DiagnosticProfileContext) =
        DiagnosticPolicyEngine.evaluate(
            applicability,
            profileAction,
            context.toPolicyContext(applicability)
        )

    @Test
    fun unknownProfileStaysBlocked() {
        val applicability = DiagnosticApplicability(
            families = setOf("2ES5K", "3ES5K"),
            profiles = setOf("base_early"),
            variantSelectionRequired = true
        )
        val context = DiagnosticProfileContext(
            family = DiagnosticLocomotiveFamily.ERMAK_2ES5K,
            confirmed = false
        )

        assertFalse(decision(applicability, context).allowed)
    }

    @Test
    fun explicitBaseEarlyProfilePassesOnlyCompatibleFamilyAndProfile() {
        val applicability = DiagnosticApplicability(
            families = setOf("2ES5K"),
            profiles = setOf("base_early"),
            variantSelectionRequired = true
        )
        val compatible = DiagnosticProfileContext(
            family = DiagnosticLocomotiveFamily.ERMAK_2ES5K,
            ermakAtlasProfile = ErmakAtlasProfile.BASE_EARLY,
            confirmed = true
        )
        val wrongFamily = compatible.copy(family = DiagnosticLocomotiveFamily.ERMAK_3ES5K)
        val wrongProfile = compatible.copy(
            ermakAtlasProfile = null,
            ermakBrakeProfile = ErmakBrakeProfile.CRANE_395
        )

        assertTrue(decision(applicability, compatible).allowed)
        assertFalse(decision(applicability, wrongFamily).allowed)
        assertFalse(decision(applicability, wrongProfile).allowed)
    }

    @Test
    fun brakeProfilesNeverCrossMatch() {
        val needs130 = DiagnosticApplicability(
            families = setOf("2ES5K", "3ES5K"),
            profiles = setOf(ErmakBrakeProfile.CRANE_130_UKTOL.policyId),
            variantSelectionRequired = true
        )
        val profile395 = DiagnosticProfileContext(
            family = DiagnosticLocomotiveFamily.ERMAK_2ES5K,
            ermakBrakeProfile = ErmakBrakeProfile.CRANE_395,
            confirmed = true
        )
        val profile130 = profile395.copy(ermakBrakeProfile = ErmakBrakeProfile.CRANE_130_UKTOL)
        val profile130_2 = profile395.copy(ermakBrakeProfile = ErmakBrakeProfile.CRANE_130_2)

        assertFalse(decision(needs130, profile395).allowed)
        assertTrue(decision(needs130, profile130).allowed)
        assertFalse(decision(needs130, profile130_2).allowed)

        val needs395 = needs130.copy(profiles = setOf(ErmakBrakeProfile.CRANE_395.policyId))
        assertFalse(decision(needs395, profile130).allowed)
        assertFalse(decision(needs395, profile130_2).allowed)
    }

    @Test
    fun safetyProfilesDoNotTransferBetweenExecutions() {
        val needsBlok = DiagnosticApplicability(
            families = setOf("2ES5K", "3ES5K"),
            profiles = setOf(ErmakSafetySystemProfile.BLOK_2ES5K.policyId),
            variantSelectionRequired = true
        )
        val blok2es5k = DiagnosticProfileContext(
            family = DiagnosticLocomotiveFamily.ERMAK_2ES5K,
            ermakSafetySystemProfile = ErmakSafetySystemProfile.BLOK_2ES5K,
            confirmed = true
        )
        val baseSafety = blok2es5k.copy(
            ermakSafetySystemProfile = ErmakSafetySystemProfile.KLUB_U_SAUT_TSKBM
        )
        val invalid3es5k = blok2es5k.copy(family = DiagnosticLocomotiveFamily.ERMAK_3ES5K)

        assertTrue(decision(needsBlok, blok2es5k).allowed)
        assertFalse(decision(needsBlok, baseSafety).allowed)
        assertFalse(invalid3es5k.canConfirm())
        assertFalse(decision(needsBlok, invalid3es5k).allowed)
    }

    @Test
    fun profileRequiredAcceptsExplicitConfirmedCanonicalProfile() {
        val applicability = DiagnosticApplicability(
            families = setOf("2ES5K", "3ES5K"),
            profiles = setOf("profile_required"),
            variantSelectionRequired = true
        )
        val context = DiagnosticProfileContext(
            family = DiagnosticLocomotiveFamily.ERMAK_3ES5K,
            ermakBrakeProfile = ErmakBrakeProfile.CRANE_130_2,
            confirmed = true
        )

        assertTrue(decision(applicability, context).allowed)
    }

    @Test
    fun arbitraryLegacyTokenAndFireTextCannotUnlockPolicy() {
        val applicability = DiagnosticApplicability(
            families = setOf("2ES5K", "3ES5K"),
            profiles = setOf("future_untrusted_profile"),
            variantSelectionRequired = true
        )
        val context = DiagnosticProfileContext(
            family = DiagnosticLocomotiveFamily.ERMAK_2ES5K,
            fireSuppressionProfileId = "future_untrusted_profile",
            legacyProfileIds = setOf("future_untrusted_profile"),
            confirmed = true
        )

        assertTrue(context.hasSourceFireProfile())
        assertFalse(context.canConfirm())
        assertFalse(decision(applicability, context).allowed)
    }

    @Test
    fun knownConflictsFailClosed() {
        val earlyLateConflict = DiagnosticProfileContext(
            family = DiagnosticLocomotiveFamily.ERMAK_2ES5K,
            ermakAtlasProfile = ErmakAtlasProfile.BASE_EARLY,
            ermakControlSystem = ErmakControlSystemProfile.MSUD_015,
            confirmed = true
        )
        val booster2es5k = DiagnosticProfileContext(
            family = DiagnosticLocomotiveFamily.ERMAK_2ES5K,
            ermakSection = ErmakSectionProfile.BOOSTER,
            ermakBrakeProfile = ErmakBrakeProfile.CRANE_395,
            confirmed = true
        )

        assertFalse(earlyLateConflict.canConfirm())
        assertTrue(earlyLateConflict.confirmedPolicyIds().isEmpty())
        assertFalse(booster2es5k.canConfirm())
        assertTrue(booster2es5k.confirmedPolicyIds().isEmpty())
    }
}
