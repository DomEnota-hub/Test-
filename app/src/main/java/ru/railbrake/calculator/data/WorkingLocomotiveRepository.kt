package ru.railbrake.calculator.data

import android.content.Context
import ru.railbrake.calculator.core.WorkingLocomotive

/** Independent of the legacy tab preference and the confirmed diagnostic profile. */
class WorkingLocomotiveRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("working_locomotive", Context.MODE_PRIVATE)

    fun selected(): WorkingLocomotive? =
        WorkingLocomotive.fromStored(preferences.getString("selected", null))

    fun select(value: WorkingLocomotive?) {
        preferences.edit().apply {
            if (value == null) remove("selected") else putString("selected", value.name)
        }.apply()
    }
}
