package ru.railbrake.calculator.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class DiagnosticCanonicalFamily(val assetName: String) {
    VL80S("technical/vl80s_diagnostics.json"),
    ERMAK("technical/ermak_diagnostics.json")
}

data class DiagnosticCanonicalScenario(
    val id: String,
    val title: String,
    val status: String = "",
    val origin: String = "",
    val relatedScenarioIds: Set<String> = emptySet(),
    val equipmentIds: Set<String> = emptySet(),
    val systemIds: Set<String> = emptySet(),
    val knowledgeIds: Set<String> = emptySet(),
    val flowNodeIds: Set<String> = emptySet()
)

data class DiagnosticCanonicalLink(
    val from: String,
    val to: String,
    val relation: String,
    val sourceField: String = "",
    val provenance: String = "",
    val gate: String = ""
)

data class DiagnosticCanonicalGraph(
    val family: DiagnosticCanonicalFamily,
    val schemaVersion: String,
    val catalogVersion: String,
    val status: String,
    val scenarios: List<DiagnosticCanonicalScenario>,
    val links: List<DiagnosticCanonicalLink>
) {
    val scenarioIds: Set<String> = scenarios.mapTo(linkedSetOf()) { it.id }
    val scenarioById: Map<String, DiagnosticCanonicalScenario> = scenarios.associateBy { it.id }
}

object DiagnosticCanonicalGraphLoader {
    fun load(context: Context, family: DiagnosticCanonicalFamily): DiagnosticCanonicalGraph =
        parse(family, TechnicalAssetReader.json(context.applicationContext, family.assetName))

    internal fun parse(
        family: DiagnosticCanonicalFamily,
        root: JSONObject
    ): DiagnosticCanonicalGraph = when (family) {
        DiagnosticCanonicalFamily.VL80S -> parseVl80s(root)
        DiagnosticCanonicalFamily.ERMAK -> parseErmak(root)
    }

    private fun parseVl80s(root: JSONObject): DiagnosticCanonicalGraph {
        val scenarios = root.array("scenarios").objects().map { item ->
            DiagnosticCanonicalScenario(
                id = item.requiredId("scenario"),
                title = item.optString("title").trim(),
                status = item.optString("runtimeStatus").trim(),
                origin = item.optString("originLayer").trim(),
                relatedScenarioIds = item.array("relatedScenarioIds").strings().toSet(),
                equipmentIds = item.array("equipmentIds").strings().toSet(),
                systemIds = item.array("systemIds").strings().toSet(),
                knowledgeIds = item.array("articleTargets").objects()
                    .mapNotNull { it.optString("id").trim().takeIf(String::isNotBlank) }
                    .toSet()
            )
        }

        val links = root.array("edges").objects().map { edge ->
            DiagnosticCanonicalLink(
                from = edge.requiredString("from", "edge"),
                to = edge.requiredString("to", "edge"),
                relation = edge.requiredString("type", "edge"),
                provenance = edge.optString("provenance").trim(),
                gate = edge.optString("gate").trim()
            )
        }

        return buildGraph(
            family = DiagnosticCanonicalFamily.VL80S,
            root = root,
            scenarios = scenarios,
            links = links
        )
    }

    private fun parseErmak(root: JSONObject): DiagnosticCanonicalGraph {
        val scenarios = root.array("scenarios").objects().map { item ->
            val graph = item.optJSONObject("graph") ?: JSONObject()
            DiagnosticCanonicalScenario(
                id = item.requiredId("scenario"),
                title = item.optString("title").trim(),
                status = item.optString("status").trim(),
                relatedScenarioIds = item.array("relatedScenarioIds").strings().toSet(),
                equipmentIds = item.array("equipmentRefs").strings().toSet(),
                systemIds = item.array("systemIds").strings().toSet(),
                knowledgeIds = item.array("knowledgeRefs").strings().toSet(),
                flowNodeIds = graph.array("nodes").objects()
                    .mapNotNull { it.optString("id").trim().takeIf(String::isNotBlank) }
                    .toSet()
            )
        }

        val links = buildList {
            root.array("scenarios").objects().forEach { item ->
                val scenarioId = item.requiredId("scenario")
                addLinks(scenarioId, item.array("relatedScenarioIds").strings(), "diagnostic_to_diagnostic", "relatedScenarioIds")
                addLinks(scenarioId, item.array("equipmentRefs").strings(), "diagnostic_to_equipment", "equipmentRefs")
                addLinks(scenarioId, item.array("systemIds").strings(), "diagnostic_to_system", "systemIds")
                addLinks(scenarioId, item.array("knowledgeRefs").strings(), "diagnostic_to_knowledge", "knowledgeRefs")
            }
        }

        return buildGraph(
            family = DiagnosticCanonicalFamily.ERMAK,
            root = root,
            scenarios = scenarios,
            links = links
        )
    }

    private fun buildGraph(
        family: DiagnosticCanonicalFamily,
        root: JSONObject,
        scenarios: List<DiagnosticCanonicalScenario>,
        links: List<DiagnosticCanonicalLink>
    ): DiagnosticCanonicalGraph {
        require(scenarios.map { it.id }.distinct().size == scenarios.size) {
            "Canonical diagnostic scenario IDs must be unique for $family"
        }
        scenarios.forEach { scenario ->
            require(scenario.title.isNotBlank()) {
                "Canonical diagnostic scenario ${scenario.id} has no title"
            }
        }

        return DiagnosticCanonicalGraph(
            family = family,
            schemaVersion = root.optString("schemaVersion").trim(),
            catalogVersion = root.optString("catalogVersion").trim(),
            status = root.optString("status").trim(),
            scenarios = scenarios,
            links = links
        )
    }
}

private fun MutableList<DiagnosticCanonicalLink>.addLinks(
    from: String,
    targets: List<String>,
    relation: String,
    sourceField: String
) {
    targets.forEach { target ->
        add(
            DiagnosticCanonicalLink(
                from = from,
                to = target,
                relation = relation,
                sourceField = sourceField
            )
        )
    }
}

private fun JSONObject.requiredId(kind: String): String =
    requiredString("id", kind)

private fun JSONObject.requiredString(key: String, kind: String): String =
    optString(key).trim().also { value ->
        require(value.isNotBlank()) { "Canonical $kind has blank $key" }
    }

private fun JSONObject.array(key: String): JSONArray =
    optJSONArray(key) ?: JSONArray()

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) {
        optJSONObject(index)?.let(::add)
    }
}

private fun JSONArray.strings(): List<String> = buildList {
    for (index in 0 until length()) {
        optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
    }
}
