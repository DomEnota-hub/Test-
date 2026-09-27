package ru.railbrake.calculator.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class ErmakDiagnosticRuntimeContractTest {
    private fun asset(): JSONObject =
        JSONObject(
            GZIPInputStream(
                File("src/main/assets/technical/ermak_diagnostics.json.gz").inputStream()
            ).bufferedReader().use { it.readText() }
        )

    @Test
    fun runtimeLoaderPreservesPoliciesApplicabilityAndSources() {
        val root = asset()
        val parsed = parseErmakDiagnostics(root)

        assertEquals(136, parsed.size)

        val rawPolicies = linkedMapOf<String, String>()
        val scenarios = root.getJSONArray("scenarios")
        for (scenarioIndex in 0 until scenarios.length()) {
            val scenario = scenarios.getJSONObject(scenarioIndex)
            val scenarioId = scenario.getString("id")
            val nodes = scenario.optJSONObject("graph")?.optJSONArray("nodes") ?: continue
            for (nodeIndex in 0 until nodes.length()) {
                val node = nodes.getJSONObject(nodeIndex)
                val policy = node.optString("userFacingPolicy").trim()
                if (policy.isNotBlank()) {
                    rawPolicies["$scenarioId/${node.getString("id")}"] = policy
                }
            }
        }

        val parsedPolicies = linkedMapOf<String, String>()
        parsed.forEach { scenario ->
            scenario.nodes.values.forEach { node ->
                node.actionMetadata.rawUserFacingPolicy.takeIf(String::isNotBlank)?.let { policy ->
                    parsedPolicies["${scenario.id}/${node.id}"] = policy
                }
            }
        }

        assertEquals(rawPolicies, parsedPolicies)
        assertTrue(parsed.any { it.applicability.variantSelectionRequired })
        assertTrue(parsed.any { it.sourceRefs.isNotEmpty() })
        assertTrue(
            parsed.flatMap { it.nodes.values }.any {
                it.actionMetadata.userFacingPolicy == DiagnosticUserFacingPolicy.SAFETY_GATE_REQUIRED
            }
        )
    }

    @Test
    fun profileRequiredSourceActionsAreBlockedWithoutConfirmedProfile() {
        val scenario = parseErmakDiagnostics(asset())
            .first { candidate ->
                candidate.nodes.values.any {
                    it.actionMetadata.userFacingPolicy in setOf(
                        DiagnosticUserFacingPolicy.SOURCE_AND_PROFILE_REQUIRED,
                        DiagnosticUserFacingPolicy.SAFETY_GATE_REQUIRED
                    )
                }
            }
        val action = scenario.nodes.values.first {
            it.actionMetadata.userFacingPolicy in setOf(
                DiagnosticUserFacingPolicy.SOURCE_AND_PROFILE_REQUIRED,
                DiagnosticUserFacingPolicy.SAFETY_GATE_REQUIRED
            )
        }

        val decision = DiagnosticPolicyEngine.evaluate(
            scenario.applicability,
            action.actionMetadata,
            DiagnosticPolicyContext()
        )

        assertFalse(decision.allowed)
    }

    @Test
    fun allProtectedActionNodesAreClassifiedForRuntimePolicyEvaluation() {
        val protected = parseErmakDiagnostics(asset())
            .flatMap { it.nodes.values }
            .filter { it.type == "source_action" || it.type == "emergency_action" }

        assertTrue(protected.isNotEmpty())
        assertTrue(protected.all { it.requiresPolicyEvaluation() })
        assertTrue(
            protected.all {
                it.actionMetadata.userFacingPolicy in setOf(
                    DiagnosticUserFacingPolicy.SOURCE_AND_PROFILE_REQUIRED,
                    DiagnosticUserFacingPolicy.SAFETY_GATE_REQUIRED,
                    DiagnosticUserFacingPolicy.EMERGENCY_SOURCE_BOUND
                )
            }
        )
        assertTrue(protected.all { it.actionMetadata.sourceBound })
    }

    @Test
    fun everyRuntimeActionFailsClosedWithoutSourceProvenance() {
        val scenarios = parseErmakDiagnostics(asset())
        val actions = scenarios.flatMap { scenario ->
            scenario.nodes.values
                .filter { it.requiresPolicyEvaluation() }
                .map { scenario to it }
        }

        assertTrue(actions.isNotEmpty())
        assertTrue(
            actions.all { (scenario, action) ->
                !DiagnosticPolicyEngine.evaluate(
                    scenario.applicability,
                    action.actionMetadata,
                    DiagnosticPolicyContext(),
                    emptyList()
                ).allowed
            }
        )
    }

    @Test
    fun emergencyActionNodeKindRequiresRuntimePolicyEvaluation() {
        val emergency = ErmakDiagnosticNode(
            id = "contract-emergency",
            type = "emergency_action",
            text = "",
            choices = emptyList(),
            nextNodeId = null,
            terminalStatus = null,
            actionMetadata = DiagnosticActionMetadata(
                riskClass = "fire_emergency",
                userFacingPolicy = DiagnosticUserFacingPolicy.EMERGENCY_SOURCE_BOUND,
                rawUserFacingPolicy = "EMERGENCY_SOURCE_BOUND",
                sourceBound = true
            )
        )

        assertTrue(emergency.requiresPolicyEvaluation())
    }

    @Test
    fun knownProfileMetadataSurvivesProjection() {
        val scenarios = parseErmakDiagnostics(asset())

        val interSection = scenarios.first { it.id == "ER-DIAG-041" }
        assertEquals("PROFILE_SOURCE_REQUIRED", interSection.applicability.lateProfiles)
        assertTrue(interSection.applicability.profiles.contains("base_early"))

        val legacy = scenarios.first { it.id == "ER-DIAG-001" }
        assertTrue(legacy.sourceAgeNote.contains("2010"))
        assertTrue(legacy.sourceRefs.isNotEmpty())
    }
}
