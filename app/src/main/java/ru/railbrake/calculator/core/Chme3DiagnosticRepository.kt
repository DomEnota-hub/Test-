package ru.railbrake.calculator.core

import android.content.Context

/** Uses the same verified decision-route model and UI as Ermak. */
class Chme3DiagnosticRepository(private val context: Context) {
    fun scenarios(family: TechnicalFamily): List<ErmakDiagnosticScenario> {
        require(family.isChme3)
        return parseErmakDiagnostics(
            TechnicalAssetReader.json(context.applicationContext,
                "technical/chme3_${family.name.lowercase()}_diagnostics.json")
        )
    }
}
