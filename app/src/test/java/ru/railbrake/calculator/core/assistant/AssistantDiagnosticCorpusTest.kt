package ru.railbrake.calculator.core.assistant

import java.io.File
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.parseErmakDiagnostics

/**
 * Regression audit over the real diagnostic catalogs, not a hand-picked list.
 * Any new scenario added to either locomotive automatically becomes part of
 * this corpus on the next unit-test run.
 *
 * The assistant UI exposes Top-3, so recovery outside the first three results
 * is considered a failure rather than a technical success hidden from the user.
 */
class AssistantDiagnosticCorpusTest {

    @Test
    fun brakePipeLeakAsksSeriesAndRoutesToEachRealCatalog() {
        val documents = DiagnosticRepository.scenarios.map(Vl80DiagnosticAssistantAdapter::adapt) +
            loadErmakScenarios().map(ErmakDiagnosticAssistantAdapter::adapt)
        val engine = AssistantEngine(InMemoryAssistantIndex(documents))
        val initial = engine.query("утечка в тормозной")
        assertTrue(initial is AssistantEngineResult.Clarify)
        assertEquals(
            AssistantAmbiguity.SERIES_REQUIRED,
            (initial as AssistantEngineResult.Clarify).clarification.reason
        )

        val vl = AssistantConversation.submit(engine, "ВЛ", AssistantConversation.submit(engine, "утечка в тормозной").pending)
        assertTrue(vl.result is AssistantEngineResult.Matches)
        assertEquals(TechnicalFamily.VL80S, (vl.result as AssistantEngineResult.Matches).hits.first().document.family)
        assertTrue(
            "VL80S hits: ${(vl.result as AssistantEngineResult.Matches).hits.joinToString { "${it.document.canonicalId} ${it.score} ${it.reasons}" }}",
            (vl.result as AssistantEngineResult.Matches).hits.first().document.title.contains("тормозн", ignoreCase = true)
        )

        val ermak = AssistantConversation.submit(engine, "Ермак", AssistantConversation.submit(engine, "утечка в тормозной").pending)
        assertTrue(ermak.result is AssistantEngineResult.Matches)
        val top = (ermak.result as AssistantEngineResult.Matches).hits.first().document
        assertEquals(TechnicalFamily.ERMAK, top.family)
        assertEquals("ER-DIAG-090", top.canonicalId)
    }

    @Test
    fun ermakFeedAndBrakePipeFaultsDoNotSwap() {
        val engine = AssistantEngine(
            InMemoryAssistantIndex(loadErmakScenarios().map(ErmakDiagnosticAssistantAdapter::adapt))
        )
        val cases = mapOf(
            "Ермак утечка в питательной магистрали" to "ER-DIAG-079",
            "Ермак падение давления в тормозной магистрали" to "ER-DIAG-090"
        )
        cases.forEach { (query, expected) ->
            val result = engine.query(query)
            assertTrue("Expected matches for $query", result is AssistantEngineResult.Matches)
            val hits = (result as AssistantEngineResult.Matches).hits
            assertEquals("$query -> ${hits.joinToString { "${it.document.canonicalId}:${it.score}" }}", expected, hits.first().document.canonicalId)
        }
    }

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
                    limit = 3
                )
            )
            if (hits.none { it.document.canonicalId == expectedId }) {
                misses += "$expectedId <- $probe :: ${hits.joinToString { it.document.canonicalId }}"
            }
        }

        assertTrue(
            buildString {
                append("Assistant diagnostic Top-3 corpus misses for ").append(family).append(':')
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
