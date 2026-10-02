package ru.railbrake.calculator.core.assistant

import java.io.File
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.parseErmakDiagnostics

/** The spoken phrase must find the real scenario even when the atlas repeats its subject. */
class AssistantDieselStallRoutingTest {
    private fun diagnostics(family: TechnicalFamily): List<AssistantDocument> {
        val asset = "chme3_${family.name.lowercase()}_diagnostics.json.gz"
        val file = listOf(File("src/main/assets/technical/$asset"),
            File("app/src/main/assets/technical/$asset"),
            File("../app/src/main/assets/technical/$asset")).first(File::isFile)
        val source = JSONObject(GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() })
        return parseErmakDiagnostics(source).map { Chme3DiagnosticAssistantAdapter.adapt(it, family) }
    }

    private fun atlas(family: TechnicalFamily, section: TechnicalSection): AssistantDocument = AssistantDocument(
        key = "test:${family.name}:${section.name}", canonicalId = "test-${section.name}",
        kind = AssistantDocumentKind.TECHNICAL_ENTRY, family = family, section = section,
        title = "Дизель K6S310DR", summary = "Первичный двигатель дизель-генераторной установки",
        body = "Дизель запускается и после пуска может глохнуть",
        aliases = setOf("двигатель", "дизель"), componentIds = emptySet(),
        symptomTerms = emptySet(), tags = emptySet(), relatedIds = emptySet(),
        safetyCritical = false,
        target = AssistantTarget.Technical(family, section, "test-${section.name}")
    )

    @Test fun stalledDieselRoutesToDiagnosisAcrossChmeFamilies() {
        val families = listOf(TechnicalFamily.CHME3, TechnicalFamily.CHME3T, TechnicalFamily.CHME3E)
        val docs = families.flatMap { family ->
            diagnostics(family) + listOf(TechnicalSection.EQUIPMENT, TechnicalSection.KNOWLEDGE,
                TechnicalSection.ACCEPTANCE).map { atlas(family, it) }
        }
        val index = InMemoryAssistantIndex(docs)
        families.forEach { family ->
            val engine = AssistantEngine(index, family)
            for (query in listOf("глохнет двигатель", "дизель заглох", "дизель глохнет после запуска")) {
                val result = engine.query(query)
                assertTrue("${family.title}: $query => $result", result is AssistantEngineResult.Matches)
                result as AssistantEngineResult.Matches
                assertEquals("$query intent", AssistantIntent.TROUBLESHOOT, result.parsedQuery.intent)
                assertEquals("$query section", TechnicalSection.DIAGNOSTICS, result.hits.first().document.section)
                assertEquals("$query family", family, result.hits.first().document.family)
                assertEquals("$query scenario", "CHME3-DIAG-002", result.hits.first().document.canonicalId)
            }
            val specific = engine.query("при трогании дизель глохнет") as AssistantEngineResult.Matches
            assertTrue(specific.hits.take(3).any { "Просадка оборотов под нагрузкой" in it.document.title })
        }
    }

    @Test fun engineContextDoesNotTurnReferenceAndTractionMotorIntoDieselFaults() {
        assertEquals(AssistantIntent.DEFINE_TERM, AssistantQueryParser.parse("что такое дизель").intent)
        assertEquals(AssistantIntent.ACCEPTANCE, AssistantQueryParser.parse("приемка дизеля ЧМЭ3Э").intent)
        assertEquals("TRACTION_MOTOR", AssistantQueryParser.parse("ЧМЭ3Э тяговый двигатель греется").componentKey)
        assertEquals("DIESEL", AssistantQueryParser.parse("ЧМЭ3Э двигатель глохнет").componentKey)
        assertTrue(AssistantFailureMode.ENGINE_STALL in AssistantFailureModeDetector.detect("дизель заглох"))
        assertFalse(AssistantFailureMode.ENGINE_STALL in AssistantFailureModeDetector.detect("дизель не глохнет"))
    }
}
