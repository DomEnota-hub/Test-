package ru.railbrake.calculator.data

import android.content.Context

/**
 * Persistent opt-in for non-standard/archival emergency diagnostic knowledge.
 *
 * Enabling this flag never upgrades provenance or action authority. Runtime
 * callers must still enforce profile applicability and the per-record safety
 * boundary before exposing an expanded branch.
 */
class ExtendedEmergencyModeRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun hasAcknowledgedWarning(): Boolean = prefs.getBoolean(KEY_WARNING_ACKNOWLEDGED_V1, false)

    fun enableAfterAcknowledgement() {
        prefs.edit()
            .putBoolean(KEY_WARNING_ACKNOWLEDGED_V1, true)
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    fun enablePreviouslyAcknowledged() {
        if (!hasAcknowledgedWarning()) return
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
    }

    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
    }

    companion object {
        const val MODE_ID = "EXTENDED_EMERGENCY_KNOWLEDGE"
        const val BADGE = "Расширенный сценарий"
        const val PREFS_NAME = "extended_emergency_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_WARNING_ACKNOWLEDGED_V1 = "warning_acknowledged_v1"
    }
}
