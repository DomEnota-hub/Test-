package ru.railbrake.calculator.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCanonicalGraphTest {
    @Test
    fun vl80sParserPreservesCanonicalIdentityAndRawEdges() {
        val root = JSONObject(
            """
            {
              "schemaVersion": "1.0",
              "status": "FINAL",
              "scenarios": [
                {
                  "id": "scenario-a",
                  "runtimeStatus": "VISIBLE",
                  "originLayer": "DiagnosticRepository.kt",
                  "title": "Сценарий А",
                  "relatedScenarioIds": ["scenario-b"],
                  "equipmentIds": ["VL-EQ-001"],
                  "systemIds": ["VL-SYS-01"],
                  "articleTargets": [{"id": "vl80-article"}]
                },
                {"id": "scenario-b", "title": "Сценарий Б"}
              ],
              "edges": [
                {
                  "from": "scenario-a",
                  "to": "VL-EQ-001",
                  "type": "diagnostic_to_equipment",
                  "provenance": "explicit",
                  "gate": "profile"
                }
              ]
            }
            """.trimIndent()
        )

        val graph = DiagnosticCanonicalGraphLoader.parse(DiagnosticCanonicalFamily.VL80S, root)
        val scenario = graph.scenarioById.getValue("scenario-a")

        assertEquals(setOf("scenario-a", "scenario-b"), graph.scenarioIds)
        assertEquals(setOf("scenario-b"), scenario.relatedScenarioIds)
        assertEquals(setOf("VL-EQ-001"), scenario.equipmentIds)
        assertEquals(setOf("VL-SYS-01"), scenario.systemIds)
        assertEquals(setOf("vl80-article"), scenario.knowledgeIds)
        assertEquals("diagnostic_to_equipment", graph.links.single().relation)
        assertEquals("explicit", graph.links.single().provenance)
        assertEquals("profile", graph.links.single().gate)
    }

    @Test
    fun ermakParserNormalizesExplicitScenarioReferenceFields() {
        val root = JSONObject(
            """
            {
              "schemaVersion": "1.0",
              "catalogVersion": "1.0-final",
              "status": "FINAL",
              "scenarios": [
                {
                  "id": "ER-DIAG-001",
                  "title": "Сценарий Ермака",
                  "status": "BASE_CONFIRMED",
                  "relatedScenarioIds": ["ER-DIAG-002"],
                  "equipmentRefs": ["ER-EQ-001"],
                  "systemIds": ["SYS-10"],
                  "knowledgeRefs": ["ER-KB-001"],
                  "graph": {
                    "nodes": [{"id": "start"}, {"id": "result"}]
                  }
                },
                {"id": "ER-DIAG-002", "title": "Связанный сценарий", "graph": {"nodes": []}}
              ]
            }
            """.trimIndent()
        )

        val graph = DiagnosticCanonicalGraphLoader.parse(DiagnosticCanonicalFamily.ERMAK, root)
        val scenario = graph.scenarioById.getValue("ER-DIAG-001")

        assertEquals(setOf("start", "result"), scenario.flowNodeIds)
        assertEquals(setOf("ER-DIAG-002"), scenario.relatedScenarioIds)
        assertEquals(setOf("ER-EQ-001"), scenario.equipmentIds)
        assertEquals(setOf("SYS-10"), scenario.systemIds)
        assertEquals(setOf("ER-KB-001"), scenario.knowledgeIds)
        assertTrue(graph.links.any { it.to == "ER-DIAG-002" && it.sourceField == "relatedScenarioIds" })
        assertTrue(graph.links.any { it.to == "ER-EQ-001" && it.relation == "diagnostic_to_equipment" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateCanonicalScenarioIdsFailClosed() {
        val root = JSONObject(
            """
            {
              "scenarios": [
                {"id": "duplicate", "title": "Первый"},
                {"id": "duplicate", "title": "Второй"}
              ],
              "edges": []
            }
            """.trimIndent()
        )

        DiagnosticCanonicalGraphLoader.parse(DiagnosticCanonicalFamily.VL80S, root)
    }
}
