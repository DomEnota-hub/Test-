package ru.railbrake.calculator.data

import android.content.Context

enum class AcceptanceCheckState(val label: String) {
    NOT_CHECKED("Не проверено"),
    OK("Проверено"),
    NOTE("Замечание"),
    BLOCKING("Блокирующее"),
    NOT_APPLICABLE("Не применяется")
}

class AcceptanceStateRepository(context: Context) {
    private val preferences = context.getSharedPreferences("technical_acceptance_states", Context.MODE_PRIVATE)

    fun state(itemId: String): AcceptanceCheckState =
        preferences.getString(itemId, AcceptanceCheckState.NOT_CHECKED.name)
            ?.let { runCatching { AcceptanceCheckState.valueOf(it) }.getOrNull() }
            ?: AcceptanceCheckState.NOT_CHECKED

    fun setState(itemId: String, state: AcceptanceCheckState) {
        preferences.edit().putString(itemId, state.name).apply()
    }
}
