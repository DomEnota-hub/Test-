package ru.railbrake.calculator.core

import android.content.Context

/** The completed Stage-3 runtime graph projected without changing its branching. */
class Tem2DiagnosticRepository(private val context: Context) {
    fun scenarios(family: TechnicalFamily): List<ErmakDiagnosticScenario> {
        require(family.isTem2)
        val profile = LocomotiveProfileRegistry.fromTechnicalFamily(family)
        if (!LocomotiveProfileRegistry.isRegisteredExact(profile)) return emptyList()
        val asset = if (profile.profileId == "tem2-base") "technical/tem2_tem2_diagnostics.json"
                    else "technical/tem2_tem2u_diagnostics.json"
        return parseErmakDiagnostics(TechnicalAssetReader.json(context.applicationContext, asset))
            .filter { profile.profileId in it.applicability.profiles }
    }
}
