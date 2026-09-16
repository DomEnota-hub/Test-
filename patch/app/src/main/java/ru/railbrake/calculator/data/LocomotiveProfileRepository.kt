package ru.railbrake.calculator.data

import android.content.Context
import ru.railbrake.calculator.core.LocomotiveProfiles

class LocomotiveProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("locomotive_profile", Context.MODE_PRIVATE)

    fun selectedProfileId(): String = prefs.getString(KEY_PROFILE, LocomotiveProfiles.VL80S_ID)
        ?: LocomotiveProfiles.VL80S_ID

    fun selectedVariantId(): String = prefs.getString(KEY_VARIANT, LocomotiveProfiles.VL80S_GENERAL)
        ?: LocomotiveProfiles.VL80S_GENERAL

    fun select(profileId: String, variantId: String) {
        prefs.edit().putString(KEY_PROFILE, profileId).putString(KEY_VARIANT, variantId).apply()
    }

    companion object {
        private const val KEY_PROFILE = "selected_profile"
        private const val KEY_VARIANT = "selected_variant"
    }
}
