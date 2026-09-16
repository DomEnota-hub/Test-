package ru.railbrake.calculator.data

import android.content.Context

class SecretAccessRepository(context: Context) {
    private val preferences = context.getSharedPreferences("secret_access", Context.MODE_PRIVATE)

    fun isUnlocked(): Boolean = preferences.getBoolean(KEY_UNLOCKED, false)

    fun unlock() {
        preferences.edit().putBoolean(KEY_UNLOCKED, true).apply()
    }

    fun hide() {
        preferences.edit().remove(KEY_UNLOCKED).apply()
    }

    companion object {
        private const val KEY_UNLOCKED = "exam_questions_unlocked"
    }
}
