package ru.railbrake.calculator.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ErmakDiagnosticChoice(
    val label: String,
    val nextNodeId: String
)

data class ErmakDiagnosticNode(
    val id: String,
    val type: String,
    val text: String,
    val choices: List<ErmakDiagnosticChoice>,
    val nextNodeId: String?,
    val terminalStatus: String?,
    val actionMetadata: DiagnosticActionMetadata = DiagnosticActionMetadata()
)

fun ErmakDiagnosticNode.requiresPolicyEvaluation(): Boolean =
    type == "source_action" || type == "emergency_action"

data class ErmakDiagnosticScenario(
    val id: String,
    val title: String,
    val symptom: String,
    val severity: String,
    val category: String,
    val equipmentIds: Set<String>,
    val startNodeId: String,
    val nodes: Map<String, ErmakDiagnosticNode>,
    val reportFields: List<String>,
    val immediateActions: List<String>,
    val dangerSigns: List<String>,
    val probableCauses: List<String>,
    val safeChecks: List<String>,
    val prohibited: List<String>,
    val applicability: DiagnosticApplicability = DiagnosticApplicability(),
    val informationConfidence: String = "",
    val sourceAgeNote: String = "",
    val sourceRefs: List<DiagnosticSourceReference> = emptyList()
)

class ErmakDiagnosticRepository(context: Context) {
    private val appContext = context.applicationContext

    fun scenarios(): List<ErmakDiagnosticScenario> {
        val root = TechnicalAssetReader.json(appContext, "technical/ermak_diagnostics.json")
        return parseErmakDiagnostics(root)
    }
}

internal fun parseErmakDiagnostics(root: JSONObject): List<ErmakDiagnosticScenario> {
    val source = root.optJSONArray("scenarios") ?: JSONArray()
    return buildList {
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val graph = item.optJSONObject("graph") ?: JSONObject()
            val nodes = linkedMapOf<String, ErmakDiagnosticNode>()
            val nodeArray = graph.optJSONArray("nodes") ?: JSONArray()

            for (nodeIndex in 0 until nodeArray.length()) {
                val node = nodeArray.optJSONObject(nodeIndex) ?: continue
                val id = node.optString("id").trim()
                if (id.isBlank()) continue
                val choices = node.optJSONArray("choices").stringChoices()
                val rawPolicy = node.optString("userFacingPolicy").trim()
                nodes[id] = ErmakDiagnosticNode(
                    id = id,
                    type = node.optString("type").trim(),
                    text = firstNonBlank(
                        node.optString("prompt"),
                        node.optString("text"),
                        node.optString("result")
                    ),
                    choices = choices,
                    nextNodeId = node.optString("nextNodeId").trim().takeIf(String::isNotBlank),
                    terminalStatus = node.optString("terminalStatus").trim().takeIf(String::isNotBlank),
                    actionMetadata = DiagnosticActionMetadata(
                        riskClass = node.optString("riskClass").trim(),
                        userFacingPolicy = DiagnosticUserFacingPolicy.parse(rawPolicy),
                        rawUserFacingPolicy = rawPolicy,
                        sourceBound = node.optBoolean("sourceBound", false)
                    )
                )
            }

            val projection = item.optJSONObject("vl80sUiProjection") ?: JSONObject()
            val applicabilityJson = item.optJSONObject("applicability") ?: JSONObject()

            val startNode = firstNonBlank(
                graph.optString("startNodeId"),
                projection.optString("startNodeId")
            ).ifBlank { nodes.keys.firstOrNull().orEmpty() }

            val applicability = DiagnosticApplicability(
                families = applicabilityJson.optJSONArray("families").stringSet(),
                profiles = applicabilityJson.optJSONArray("profiles").stringSet(),
                variantSelectionRequired = applicabilityJson.optBoolean("variantSelectionRequired", false),
                lateProfiles = applicabilityJson.optString("lateProfiles").trim()
            )

            add(
                ErmakDiagnosticScenario(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    symptom = item.optString("symptom"),
                    severity = projection.optString("severity").ifBlank { item.optString("severity") },
                    category = item.optString("category"),
                    equipmentIds = projection.optJSONArray("relatedEquipment").stringSet()
                        .ifEmpty { item.optJSONArray("equipmentRefs").stringSet() },
                    startNodeId = startNode,
                    nodes = nodes,
                    reportFields = projection.optJSONArray("reportFields").stringList(),
                    immediateActions = projection.optJSONArray("immediateActions").stringList(),
                    dangerSigns = projection.optJSONArray("dangerSigns").stringList(),
                    probableCauses = projection.optJSONArray("probableCauses").stringList(),
                    safeChecks = projection.optJSONArray("safeChecks").stringList(),
                    prohibited = projection.optJSONArray("prohibited").stringList(),
                    applicability = applicability,
                    informationConfidence = firstNonBlank(
                        projection.optString("informationConfidence"),
                        item.optString("informationConfidence")
                    ),
                    sourceAgeNote = item.optString("sourceAgeNote").trim(),
                    sourceRefs = item.optJSONArray("sourceRefs").sourceReferences()
                )
            )
        }
    }
}

private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun JSONArray?.stringSet(): Set<String> = stringList().toSet()

private fun JSONArray?.stringChoices(): List<ErmakDiagnosticChoice> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val label = firstNonBlank(item.optString("label"), item.optString("text"))
            val next = firstNonBlank(item.optString("nextNodeId"), item.optString("next"))
            if (label.isNotBlank() && next.isNotBlank()) {
                add(ErmakDiagnosticChoice(label, next))
            }
        }
    }
}

private fun JSONArray?.sourceReferences(): List<DiagnosticSourceReference> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                DiagnosticSourceReference(
                    sourceId = item.optString("sourceId").trim(),
                    document = item.optString("document").trim(),
                    locator = item.optString("locator").trim(),
                    role = item.optString("role").trim(),
                    kind = DiagnosticSourceKind.parse(
                        firstNonBlank(
                            item.stringValue("sourceKind"),
                            item.stringValue("kind")
                        )
                    ),
                    version = item.sourceVersion()
                )
            )
        }
    }
}

private fun JSONObject.sourceVersion(): DiagnosticSourceVersion {
    val nested = optJSONObject("version")
    return DiagnosticSourceVersion(
        versionLabel = firstNonBlank(
            nested?.stringValue("label").orEmpty(),
            nested?.stringValue("versionLabel").orEmpty(),
            stringValue("versionLabel"),
            stringValue("version")
        ),
        revision = firstNonBlank(
            nested?.stringValue("revision").orEmpty(),
            stringValue("revision")
        ),
        effectiveFrom = firstNonBlank(
            nested?.stringValue("effectiveFrom").orEmpty(),
            stringValue("effectiveFrom")
        ),
        effectiveTo = firstNonBlank(
            nested?.stringValue("effectiveTo").orEmpty(),
            stringValue("effectiveTo")
        ),
        verifiedAt = firstNonBlank(
            nested?.stringValue("verifiedAt").orEmpty(),
            stringValue("verifiedAt")
        ),
        status = DiagnosticSourceVersionStatus.parse(
            firstNonBlank(
                nested?.stringValue("status").orEmpty(),
                stringValue("versionStatus")
            )
        )
    )
}

private fun JSONObject.stringValue(key: String): String =
    (opt(key) as? String).orEmpty().trim()

private fun firstNonBlank(vararg values: String): String =
    values.firstOrNull { it.isNotBlank() }.orEmpty().trim()
