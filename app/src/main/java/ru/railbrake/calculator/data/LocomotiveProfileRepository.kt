package ru.railbrake.calculator.data

import android.content.Context
import ru.railbrake.calculator.core.DiagnosticLocomotiveFamily
import ru.railbrake.calculator.core.DiagnosticProfileContext
import ru.railbrake.calculator.core.ErmakAtlasProfile
import ru.railbrake.calculator.core.ErmakBrakeProfile
import ru.railbrake.calculator.core.ErmakControlSystemProfile
import ru.railbrake.calculator.core.ErmakMotorAxleBearingProfile
import ru.railbrake.calculator.core.ErmakSafetySystemProfile
import ru.railbrake.calculator.core.ErmakSectionProfile
import ru.railbrake.calculator.core.ErmakTractionRegulationProfile
import ru.railbrake.calculator.core.LocomotiveProfiles

class LocomotiveProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("locomotive_profile", Context.MODE_PRIVATE)

    /**
     * Legacy UI selection. It intentionally remains separate from diagnostic confirmation:
     * a default tab/series must never be treated as a confirmed execution profile.
     */
    fun selectedProfileId(): String = prefs.getString(KEY_PROFILE, LocomotiveProfiles.VL80S_ID)
        ?: LocomotiveProfiles.VL80S_ID

    fun selectedVariantId(): String = prefs.getString(KEY_VARIANT, LocomotiveProfiles.VL80S_GENERAL)
        ?: LocomotiveProfiles.VL80S_GENERAL

    fun select(profileId: String, variantId: String) {
        prefs.edit()
            .putString(KEY_PROFILE, profileId)
            .putString(KEY_VARIANT, variantId)
            .putBoolean(KEY_DIAGNOSTIC_CONFIRMED, false)
            .apply()
    }

    fun diagnosticContext(): DiagnosticProfileContext = DiagnosticProfileContext(
        family = enumPreference<DiagnosticLocomotiveFamily>(prefs.getString(KEY_DIAGNOSTIC_FAMILY, null)),
        ermakAtlasProfile = enumPreference<ErmakAtlasProfile>(prefs.getString(KEY_ERMAK_ATLAS_PROFILE, null)),
        ermakSection = enumPreference<ErmakSectionProfile>(prefs.getString(KEY_ERMAK_SECTION, null)),
        ermakControlSystem = enumPreference<ErmakControlSystemProfile>(prefs.getString(KEY_ERMAK_CONTROL_SYSTEM, null)),
        ermakTractionRegulation = enumPreference<ErmakTractionRegulationProfile>(prefs.getString(KEY_ERMAK_REGULATION, null)),
        ermakBrakeProfile = enumPreference<ErmakBrakeProfile>(prefs.getString(KEY_ERMAK_BRAKE, null)),
        ermakSafetySystemProfile = enumPreference<ErmakSafetySystemProfile>(prefs.getString(KEY_SAFETY_SYSTEM, null)),
        ermakMotorAxleBearing = enumPreference<ErmakMotorAxleBearingProfile>(prefs.getString(KEY_ERMAK_MOP, null)),
        fireSuppressionProfileId = prefs.getString(KEY_FIRE_SUPPRESSION, null)?.trim()?.takeIf(String::isNotBlank),
        legacyProfileIds = prefs.getStringSet(KEY_LEGACY_PROFILE_IDS, emptySet()).orEmpty(),
        confirmed = prefs.getBoolean(KEY_DIAGNOSTIC_CONFIRMED, false)
    )

    fun saveDiagnosticContext(context: DiagnosticProfileContext) {
        val editor = prefs.edit()
        editor.putOptionalEnum(KEY_DIAGNOSTIC_FAMILY, context.family)
        editor.putOptionalEnum(KEY_ERMAK_ATLAS_PROFILE, context.ermakAtlasProfile)
        editor.putOptionalEnum(KEY_ERMAK_SECTION, context.ermakSection)
        editor.putOptionalEnum(KEY_ERMAK_CONTROL_SYSTEM, context.ermakControlSystem)
        editor.putOptionalEnum(KEY_ERMAK_REGULATION, context.ermakTractionRegulation)
        editor.putOptionalEnum(KEY_ERMAK_BRAKE, context.ermakBrakeProfile)
        editor.putOptionalEnum(KEY_SAFETY_SYSTEM, context.ermakSafetySystemProfile)
        editor.putOptionalEnum(KEY_ERMAK_MOP, context.ermakMotorAxleBearing)
        editor.putOptionalString(KEY_FIRE_SUPPRESSION, context.fireSuppressionProfileId)
        editor.putStringSet(KEY_LEGACY_PROFILE_IDS, context.legacyProfileIds.map(String::trim).filter(String::isNotBlank).toSet())
        editor.putBoolean(KEY_DIAGNOSTIC_CONFIRMED, context.confirmed)
        editor.apply()
    }

    fun clearDiagnosticContext() {
        prefs.edit()
            .remove(KEY_DIAGNOSTIC_FAMILY)
            .remove(KEY_ERMAK_ATLAS_PROFILE)
            .remove(KEY_ERMAK_SECTION)
            .remove(KEY_ERMAK_CONTROL_SYSTEM)
            .remove(KEY_ERMAK_REGULATION)
            .remove(KEY_ERMAK_BRAKE)
            .remove(KEY_SAFETY_SYSTEM)
            .remove(KEY_ERMAK_MOP)
            .remove(KEY_FIRE_SUPPRESSION)
            .remove(KEY_LEGACY_PROFILE_IDS)
            .remove(KEY_DIAGNOSTIC_CONFIRMED)
            .apply()
    }

    private fun android.content.SharedPreferences.Editor.putOptionalEnum(key: String, value: Enum<*>?) {
        if (value == null) remove(key) else putString(key, value.name)
    }

    private fun android.content.SharedPreferences.Editor.putOptionalString(key: String, value: String?) {
        val normalized = value?.trim()?.takeIf(String::isNotBlank)
        if (normalized == null) remove(key) else putString(key, normalized)
    }

    private inline fun <reified T : Enum<T>> enumPreference(value: String?): T? =
        value?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }

    companion object {
        private const val KEY_PROFILE = "selected_profile"
        private const val KEY_VARIANT = "selected_variant"
        private const val KEY_DIAGNOSTIC_FAMILY = "diagnostic_family"
        private const val KEY_ERMAK_ATLAS_PROFILE = "diagnostic_ermak_atlas_profile"
        private const val KEY_ERMAK_SECTION = "diagnostic_ermak_section"
        private const val KEY_ERMAK_CONTROL_SYSTEM = "diagnostic_ermak_control_system"
        private const val KEY_ERMAK_REGULATION = "diagnostic_ermak_regulation"
        private const val KEY_ERMAK_BRAKE = "diagnostic_ermak_brake"
        private const val KEY_SAFETY_SYSTEM = "diagnostic_safety_system"
        private const val KEY_ERMAK_MOP = "diagnostic_ermak_mop"
        private const val KEY_FIRE_SUPPRESSION = "diagnostic_fire_suppression"
        private const val KEY_LEGACY_PROFILE_IDS = "diagnostic_legacy_profile_ids"
        private const val KEY_DIAGNOSTIC_CONFIRMED = "diagnostic_profile_confirmed"
    }
}
