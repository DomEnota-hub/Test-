package ru.railbrake.calculator.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ErmakDiagnosticChoice(val label: String, val nextNodeId: String)

data class ErmakDiagnosticNode(
    val id: String,
    val type: String,
    val text: String,
    val choices: List<ErmakDiagnosticChoice>,
    val nextNodeId: String?,
    val terminalStatus: String?
)

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
    val prohibited: List<String>
)

class ErmakDiagnosticRepository(private val context: Context) {
    fun scenarios(): List<ErmakDiagnosticScenario> = synchronized(cache) {
        if (cache.isEmpty()) cache.addAll(load())
        cache.toList()
    }

    private fun load(): List<ErmakDiagnosticScenario> {
        val root = TechnicalAssetReader.json(context, "technical/ermak_diagnostics.json")
        return root.array("scenarios").objects().mapNotNull { scenario ->
            val graph = scenario.obj("graph")
            val nodes = graph.array("nodes").objects().mapNotNull { node ->
                val id = node.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val text = sequenceOf("prompt", "text", "result")
                    .map(node::optString).firstOrNull(String::isNotBlank).orEmpty()
                id to ErmakDiagnosticNode(
                    id = id,
                    type = node.optString("type"),
                    text = text,
                    choices = node.array("choices").objects().mapNotNull { choice ->
                        val label = choice.optString("label")
                        val next = choice.optString("nextNodeId")
                        if (label.isBlank() || next.isBlank()) null else ErmakDiagnosticChoice(label, next)
                    },
                    nextNodeId = node.optString("nextNodeId").takeIf(String::isNotBlank),
                    terminalStatus = node.optString("terminalStatus").takeIf(String::isNotBlank)
                )
            }.toMap()
            val start = graph.optString("startNodeId")
            if (start.isBlank() || start !in nodes) return@mapNotNull null
            val projection = scenario.obj("vl80sUiProjection")
            ErmakDiagnosticScenario(
                id = scenario.optString("id"),
                title = scenario.optString("title"),
                symptom = scenario.optString("symptom"),
                severity = scenario.optString("severity"),
                category = scenario.optString("category"),
                equipmentIds = scenario.array("equipmentRefs").strings().toSet(),
                startNodeId = start,
                nodes = nodes,
                reportFields = projection.array("reportFields").strings(),
                immediateActions = projection.array("immediateActions").strings(),
                dangerSigns = projection.array("dangerSigns").strings(),
                probableCauses = projection.array("probableCauses").strings(),
                safeChecks = projection.array("checks").objects().map { check ->
                    listOf(check.optString("title"), check.optString("action"), check.optString("expected"))
                        .filter(String::isNotBlank).joinToString(": ")
                },
                prohibited = projection.array("prohibited").strings()
            )
        }
    }

    private companion object {
        val cache = mutableListOf<ErmakDiagnosticScenario>()
    }
}

private fun JSONObject.array(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
private fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()
private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}
private fun JSONArray.strings(): List<String> = buildList {
    for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
}
