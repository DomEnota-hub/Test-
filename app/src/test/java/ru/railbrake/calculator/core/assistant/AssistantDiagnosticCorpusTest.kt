package ru.railbrake.calculator.core.assistant

import java.io.File
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.parseErmakDiagnostics

/**
 * Regression audit over the real diagnostic catalogs, not a hand-picked list.
 * Any new scenario added to either locomotive automatically becomes part of
 * this corpus on the next unit-test run.
 */
class AssistantDiagnosticCorpusTest {

    @Test
    fun everyVl80ScenarioCanBeRecoveredFromItsTitleAndSummary() {
        val scenarios = DiagnosticRepository.scenarios
        val documents = scenarios.map(Vl80DiagnosticAssistantAdapter::adapt)
        val probes = buildList {
            scenarios.forEach { scenario ->
                if (scenario.title.isNotBlank()) add(scenario.id to scenario.title)
                if (scenario.summary.isNotBlank()) add(scenario.id to scenario.summary)
            }
        }

        assertCorpus(
            family = TechnicalFamily.VL80S,
            familyCue = "ВЛ80С",
            documents = documents,
            probes = probes
        )
    }

    @Test
    fun everyErmakScenarioCanBeRecoveredFromItsTitleAndSymptom() {
        val scenarios = loadErmakScenarios()
        val documents = scenarios.map(ErmakDiagnosticAssistantAdapter::adapt)
        val probes = buildList {
            scenarios.forEach { scenario ->
                if (scenario.title.isNotBlank()) add(scenario.id to scenario.title)
                if (scenario.symptom.isNotBlank()) add(scenario.id to scenario.symptom)
            }
        }

        assertCorpus(
            family = TechnicalFamily.ERMAK,
            familyCue = "Ермак",
            documents = documents,
            probes = probes
        )
    }

    private fun assertCorpus(
        family: TechnicalFamily,
        familyCue: String,
        documents: List<AssistantDocument>,
        probes: List<Pair<String, String>>
    ) {
        assertTrue("Diagnostic corpus for $family is empty", documents.isNotEmpty())
        val index = InMemoryAssistantIndex(documents)
        val misses = mutableListOf<String>()

        probes.forEach { (expectedId, probe) ->
            val parsed = AssistantQueryParser.parse("$familyCue $probe")
            val hits = index.search(
                AssistantSearchRequest(
                    query = parsed.searchText,
                    family = family,
                    preferredSection = TechnicalSection.DIAGNOSTICS,
                    failureModes = parsed.failureModes,
                    limit = 10
                )
            )
            if (hits.none { it.document.canonicalId == expectedId }) {
                misses += "$expectedId <- $probe :: ${hits.joinToString { it.document.canonicalId }}"
            }
        }

        assertTrue(
            buildString {
                append("Assistant diagnostic corpus misses for ").append(family).append(':')
                misses.take(30).forEach { append("\n - ").append(it) }
                if (misses.size > 30) append("\n ... and ").append(misses.size - 30).append(" more")
            },
            misses.isEmpty()
        )
    }

    private fun loadErmakScenarios() = parseErmakDiagnostics(
        JSONObject(
            GZIPInputStream(ermakAssetFile().inputStream())
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        )
    )

    private fun ermakAssetFile(): File {
        val candidates = listOf(
            File("src/main/assets/technical/ermak_diagnostics.json.gz"),
            File("app/src/main/assets/technical/ermak_diagnostics.json.gz"),
            File("../app/src/main/assets/technical/ermak_diagnostics.json.gz")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("ermak_diagnostics.json.gz not found; cwd=${File(".").absolutePath}")
    }
}
