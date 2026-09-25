package ru.railbrake.calculator.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticPolicyEngineTest {
    private val profileSpecific = DiagnosticApplicability(
        families = setOf("2ES5K", "3ES5K"),
        profiles = setOf("base_early"),
        variantSelectionRequired = true
    )

    @Test
    fun safetyGateFailsClosedUntilProfileAndSafetyAreConfirmed() {
        val action = DiagnosticActionMetadata(
            riskClass = "hv_manual",
            userFacingPolicy = DiagnosticUserFacingPolicy.SAFETY_GATE_REQUIRED,
            rawUserFacingPolicy = "SAFETY_GATE_REQUIRED",
            sourceBound = true
        )

        assertFalse(
            DiagnosticPolicyEngine.evaluate(
                profileSpecific,
                action,
                DiagnosticPolicyContext()
            ).allowed
        )

        assertFalse(
            DiagnosticPolicyEngine.evaluate(
                profileSpecific,
                action,
                DiagnosticPolicyContext(
                    selectedFamily = "2ES5K",
                    selectedProfileId = "base_early",
                    profileConfirmed = true,
                    safetyGateConfirmed = false
                )
            ).allowed
        )

        assertTrue(
            DiagnosticPolicyEngine.evaluate(
                profileSpecific,
                action,
                DiagnosticPolicyContext(
                    selectedFamily = "2ES5K",
                    selectedProfileId = "base_early",
                    profileConfirmed = true,
                    safetyGateConfirmed = true
                )
            ).allowed
        )
    }

    @Test
    fun emergencySourceBoundCanRunWithoutProfileWhenScenarioIsGeneral() {
        val decision = DiagnosticPolicyEngine.evaluate(
            DiagnosticApplicability(
                profiles = setOf("all_confirmed_profiles"),
                variantSelectionRequired = false
            ),
            DiagnosticActionMetadata(
                riskClass = "fire_emergency",
                userFacingPolicy = DiagnosticUserFacingPolicy.EMERGENCY_SOURCE_BOUND,
                rawUserFacingPolicy = "EMERGENCY_SOURCE_BOUND",
                sourceBound = true
            ),
            DiagnosticPolicyContext()
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun unknownPolicyAndUnclassifiedRiskFailClosed() {
        assertFalse(
            DiagnosticPolicyEngine.evaluate(
                DiagnosticApplicability(),
                DiagnosticActionMetadata(
                    riskClass = "hv_manual",
                    userFacingPolicy = DiagnosticUserFacingPolicy.UNKNOWN,
                    rawUserFacingPolicy = "FUTURE_POLICY",
                    sourceBound = true
                ),
                DiagnosticPolicyContext()
            ).allowed
        )

        assertFalse(
            DiagnosticPolicyEngine.evaluate(
                DiagnosticApplicability(),
                DiagnosticActionMetadata(
                    riskClass = "manual_power_apparatus",
                    userFacingPolicy = DiagnosticUserFacingPolicy.UNSPECIFIED,
                    sourceBound = true
                ),
                DiagnosticPolicyContext()
            ).allowed
        )
    }
}
