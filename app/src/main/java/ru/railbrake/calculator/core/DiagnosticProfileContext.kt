package ru.railbrake.calculator.core

enum class DiagnosticLocomotiveFamily(val policyId: String) {
    VL80S("VL80S"),
    ERMAK_2ES5K("2ES5K"),
    ERMAK_3ES5K("3ES5K")
}

enum class ErmakAtlasProfile(val policyId: String) {
    BASE_EARLY("base_early")
}

enum class ErmakSectionProfile(val sourceValue: String) {
    HEAD("head"),
    BOOSTER("booster")
}

enum class ErmakControlSystemProfile(val policyId: String) {
    MSUD_N("msud_n_base"),
    MSUD_015("msud_015_extended")
}

enum class ErmakTractionRegulationProfile(val policyId: String) {
    GROUP("traction_group_base"),
    AXLE("traction_axle_control_modern")
}

enum class ErmakBrakeProfile(val policyId: String) {
    CRANE_395("brake_395"),
    CRANE_130_UKTOL("brake_130_uktol"),
    CRANE_130_2("brake_130_2_uktol")
}

enum class ErmakSafetySystemProfile(val policyId: String) {
    KLUB_U_SAUT_TSKBM("safety_klub_u_tskbm_saut_base"),
    BLOK_2ES5K("safety_blok_confirmed_2es5k")
}

enum class ErmakMotorAxleBearingProfile(val policyId: String) {
    SLIDING("motor_axle_bearing_plain"),
    ROLLING("motor_axle_bearing_rolling")
}

/**
 * Explicit profile evidence for diagnostic policy. Null means "not confirmed".
 * Values mirror source-backed profile dimensions from the Ermak atlas; one trait is never
 * inferred from another and the legacy UI default is never treated as confirmation.
 */
data class DiagnosticProfileContext(
    val family: DiagnosticLocomotiveFamily? = null,
    val ermakAtlasProfile: ErmakAtlasProfile? = null,
    val ermakSection: ErmakSectionProfile? = null,
    val ermakControlSystem: ErmakControlSystemProfile? = null,
    val ermakTractionRegulation: ErmakTractionRegulationProfile? = null,
    val ermakBrakeProfile: ErmakBrakeProfile? = null,
    val ermakSafetySystemProfile: ErmakSafetySystemProfile? = null,
    val ermakMotorAxleBearing: ErmakMotorAxleBearingProfile? = null,
    val fireSuppressionProfileId: String? = null,
    val legacyProfileIds: Set<String> = emptySet(),
    val confirmed: Boolean = false
) {
    /** IDs that are allowed to participate in policy matching after explicit confirmation. */
    fun confirmedPolicyIds(): Set<String> =
        if (!confirmed || validationIssue() != null) emptySet() else candidatePolicyIds()

    /**
     * Fire suppression is retained as source evidence but intentionally does not unlock a
     * diagnostic action until the canonical atlas defines a matching policy profile.
     */
    fun hasSourceFireProfile(): Boolean = cleanId(fireSuppressionProfileId) != null

    fun validationIssue(): String? = when {
        family == DiagnosticLocomotiveFamily.ERMAK_2ES5K && ermakSection == ErmakSectionProfile.BOOSTER ->
            "Бустерная секция подтверждена только для 3ЭС5К."
        family == DiagnosticLocomotiveFamily.ERMAK_3ES5K && ermakSafetySystemProfile == ErmakSafetySystemProfile.BLOK_2ES5K ->
            "Профиль БЛОК из текущего источника нельзя переносить на 3ЭС5К."
        ermakAtlasProfile == ErmakAtlasProfile.BASE_EARLY && ermakControlSystem == ErmakControlSystemProfile.MSUD_015 ->
            "Базовое раннее исполнение нельзя одновременно подтверждать как профиль МСУД-015."
        ermakAtlasProfile == ErmakAtlasProfile.BASE_EARLY && ermakTractionRegulation == ErmakTractionRegulationProfile.AXLE ->
            "Базовое раннее исполнение нельзя одновременно подтверждать как позднее поосное управление."
        ermakControlSystem == ErmakControlSystemProfile.MSUD_N && ermakTractionRegulation == ErmakTractionRegulationProfile.AXLE ->
            "Поосный профиль нельзя подтверждать одновременно с базовым профилем МСУД-Н."
        else -> null
    }

    fun canConfirm(): Boolean =
        family != null && validationIssue() == null && candidatePolicyIds().isNotEmpty()

    /**
     * Converts only explicit, internally consistent evidence. "profile_required" and
     * "all_confirmed_profiles" are generic gates: either accepts one confirmed canonical
     * execution profile, while a concrete profile list still requires an exact match.
     */
    fun toPolicyContext(
        applicability: DiagnosticApplicability,
        safetyGateConfirmed: Boolean = false
    ): DiagnosticPolicyContext {
        val confirmedIds = confirmedPolicyIds()
        val requested = applicability.profiles.map(::policyKey).toSet()
        val genericGate = "all_confirmed_profiles" in requested || "profile_required" in requested
        val selected = when {
            confirmedIds.isEmpty() || family == null -> null
            requested.isEmpty() || genericGate -> confirmedIds.firstOrNull()
            else -> confirmedIds.firstOrNull { policyKey(it) in requested }
        }
        return DiagnosticPolicyContext(
            selectedFamily = family?.policyId,
            selectedProfileId = selected,
            profileConfirmed = selected != null,
            safetyGateConfirmed = safetyGateConfirmed
        )
    }

    private fun candidatePolicyIds(): Set<String> = buildSet {
        ermakAtlasProfile?.policyId?.let(::add)
        ermakControlSystem?.policyId?.let(::add)
        ermakTractionRegulation?.policyId?.let(::add)
        ermakBrakeProfile?.policyId?.let(::add)
        ermakSafetySystemProfile?.policyId?.let(::add)
        ermakMotorAxleBearing?.policyId?.let(::add)
        legacyProfileIds.mapNotNull(::cleanId)
            .filter { policyKey(it) in KNOWN_LEGACY_POLICY_IDS }
            .forEach(::add)
    }

    companion object {
        private val KNOWN_LEGACY_POLICY_IDS = setOf(
            "base_early",
            "base_remote_brake_control",
            "3es5k_axle_control_896plus",
            "traction_group_base",
            "traction_axle_control_modern",
            "msud_n_base",
            "msud_015_extended",
            "brake_395",
            "brake_130_uktol",
            "brake_130_2_uktol",
            "safety_klub_u_tskbm_saut_base",
            "safety_blok_confirmed_2es5k",
            "motor_axle_bearing_plain",
            "motor_axle_bearing_rolling"
        )

        private fun cleanId(value: String?): String? = value?.trim()?.takeIf(String::isNotBlank)
        private fun policyKey(value: String): String = value.trim().lowercase()
    }
}
