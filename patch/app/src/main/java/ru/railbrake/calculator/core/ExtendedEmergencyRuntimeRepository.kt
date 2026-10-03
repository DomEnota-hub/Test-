package ru.railbrake.calculator.core

import android.content.Context
import org.json.JSONObject

data class ExtendedEmergencySourcePresentation(
    val title: String,
    val url: String,
    val provenanceLabel: String,
    val statusLabel: String
)

data class ExtendedEmergencyEvidence(
    val standardScenarioId: String,
    val profiles: Set<String>,
    val actionDisposition: String,
    val dispositionLabel: String,
    val riskLabel: String,
    val summary: String,
    val terminal: String,
    val sources: List<ExtendedEmergencySourcePresentation>
) {
    val prohibited: Boolean get() = actionDisposition == "PROHIBITED"
}

class ExtendedEmergencyRuntimeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val records: List<ExtendedEmergencyEvidence> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching { loadRecords() }.getOrDefault(emptyList())
    }

    fun evidenceFor(
        standardScenarioId: String,
        profileContext: LocomotiveProfileContext,
        modeEnabled: Boolean
    ): List<ExtendedEmergencyEvidence> {
        if (!modeEnabled || standardScenarioId.isBlank()) return emptyList()
        if (!LocomotiveProfileRegistry.isRegisteredExact(profileContext)) return emptyList()
        return records.filter { evidence ->
            evidence.standardScenarioId == standardScenarioId && profileContext.profileId in evidence.profiles
        }
    }

    private fun loadRecords(): List<ExtendedEmergencyEvidence> {
        val root = TechnicalAssetReader.json(appContext, ASSET)
        if (root.optInt("schemaVersion") != 1 || root.optString("modeId") != MODE_ID) return emptyList()
        val policy = root.optJSONObject("policy") ?: return emptyList()
        if (policy.optBoolean("defaultEnabled", true)) return emptyList()
        if (!policy.optBoolean("standardDiagnosticsRemainCanonical", false)) return emptyList()
        if (!policy.optBoolean("attachOnlyAfterStandardScenarioMatch", false)) return emptyList()
        if (!policy.optBoolean("exactProfileOnly", false)) return emptyList()
        if (policy.optString("unknownExecution") != "FAIL_CLOSED") return emptyList()
        if (policy.optString("adjacentVariantInheritance") != "DENY") return emptyList()
        if (policy.optBoolean("expandedEvidenceExecutable", true)) return emptyList()
        if (policy.optBoolean("procedureLevelHazardousDetailVisible", true)) return emptyList()

        val array = root.optJSONArray("records") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseRecord(item)?.let(::add)
            }
        }
    }

    private fun parseRecord(item: JSONObject): ExtendedEmergencyEvidence? {
        val scenarioId = item.optString("standardScenarioId").trim()
        val disposition = item.optString("actionDisposition").trim()
        val safety = item.optJSONObject("runtimeSafety") ?: return null
        if (!runtimeRecordAllowed(
                actionDisposition = disposition,
                exactProfileOnly = safety.optBoolean("exactProfileOnly", false),
                executable = safety.optBoolean("executable", true),
                procedureVisible = safety.optBoolean("procedureVisible", true),
                currentAuthorityVerified = safety.optBoolean("currentAuthorityVerified", true),
                runtimeAuthorityUpgradeAllowed = safety.optBoolean("runtimeAuthorityUpgradeAllowed", true)
            )) return null

        val profilesArray = item.optJSONArray("profiles") ?: return null
        val profiles = buildSet {
            for (index in 0 until profilesArray.length()) {
                profilesArray.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
        if (scenarioId.isEmpty() || profiles.isEmpty()) return null

        val sourcesArray = item.optJSONArray("sources") ?: return null
        val sources = buildList {
            for (index in 0 until sourcesArray.length()) {
                val source = sourcesArray.optJSONObject(index) ?: continue
                val title = source.optString("title").trim()
                val status = source.optString("statusLabel").trim()
                val provenance = source.optString("provenanceLabel").trim()
                if (title.isEmpty() || status.isEmpty() || provenance.isEmpty()) continue
                add(
                    ExtendedEmergencySourcePresentation(
                        title = title,
                        url = source.optString("url").trim(),
                        provenanceLabel = provenance,
                        statusLabel = status
                    )
                )
            }
        }
        if (sources.isEmpty()) return null

        val summary = item.optString("summary").trim()
        val terminal = item.optString("terminal").trim()
        val dispositionLabel = item.optString("dispositionLabel").trim()
        val riskLabel = item.optString("riskLabel").trim()
        if (summary.isEmpty() || terminal.isEmpty() || dispositionLabel.isEmpty() || riskLabel.isEmpty()) return null

        return ExtendedEmergencyEvidence(
            standardScenarioId = scenarioId,
            profiles = profiles,
            actionDisposition = disposition,
            dispositionLabel = dispositionLabel,
            riskLabel = riskLabel,
            summary = summary,
            terminal = terminal,
            sources = sources
        )
    }

    companion object {
        const val MODE_ID = "EXTENDED_EMERGENCY_KNOWLEDGE"
        const val ASSET = "technical/extended_emergency_runtime.json"

        internal fun runtimeRecordAllowed(
            actionDisposition: String,
            exactProfileOnly: Boolean,
            executable: Boolean,
            procedureVisible: Boolean,
            currentAuthorityVerified: Boolean,
            runtimeAuthorityUpgradeAllowed: Boolean
        ): Boolean =
            actionDisposition in setOf("INFORMATION_ONLY", "PROHIBITED") &&
                exactProfileOnly &&
                !executable &&
                !procedureVisible &&
                !currentAuthorityVerified &&
                !runtimeAuthorityUpgradeAllowed
    }
}
