package ru.railbrake.calculator.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

class AssistantEngineTest {

    @Test
    fun asrStyleVl80FaultQueryRoutesToVl80Diagnostic() {
        val engine = engine()

        val result = engine.query("на вээл восемьдесят эс гэ вэ не включается")

        assertTrue(result is AssistantEngineResult.Matches)
        result as AssistantEngineResult.Matches
        assertEquals(AssistantIntent.TROUBLESHOOT, result.parsedQuery.intent)
        assertEquals(TechnicalFamily.VL80S, result.parsedQuery.family)
        assertEquals("vl80-gv-fault", result.hits.first().document.canonicalId)
    }

    @Test
    fun faultQueryWithoutSeriesAsksWhichFamily() {
        val result = engine().query("ГВ не включается")

        assertTrue(result is AssistantEngineResult.Clarify)
        result as AssistantEngineResult.Clarify
        assertEquals(AssistantAmbiguity.SERIES_REQUIRED, result.clarification.reason)
        assertTrue(result.provisionalHits.isNotEmpty())
    }

    @Test
    fun ermakSchemeQueryUsesSeriesAndElectricalSection() {
        val result = engine().query("покажи ГВ на схеме три эс пять ка")

        assertTrue(result is AssistantEngineResult.Matches)
        result as AssistantEngineResult.Matches
        assertEquals(AssistantIntent.OPEN_SCHEME, result.parsedQuery.intent)
        assertEquals(TechnicalFamily.ERMAK, result.parsedQuery.family)
        assertEquals(TechnicalSection.ELECTRICAL, result.parsedQuery.preferredSection)
        assertEquals("ermak-gv-scheme", result.hits.first().document.canonicalId)
    }

    @Test
    fun bareMainBreakerQueryRequiresScope() {
        val result = engine().query("ГВ")

        assertTrue(result is AssistantEngineResult.Clarify)
        result as AssistantEngineResult.Clarify
        assertEquals(AssistantAmbiguity.TOPIC_SCOPE, result.clarification.reason)
        assertEquals(3, result.clarification.options.size)
    }

    @Test
    fun genericBrakeTestRequiresProcedureType() {
        val result = engine().query("проба тормозов")

        assertTrue(result is AssistantEngineResult.Clarify)
        result as AssistantEngineResult.Clarify
        assertEquals(AssistantAmbiguity.PROCEDURE_TYPE_REQUIRED, result.clarification.reason)
    }

    @Test
    fun safetyQueryRoutesToSafetyAndNeverAutoOpensCriticalCard() {
        val result = engine().query("обморожение")

        assertTrue(result is AssistantEngineResult.Matches)
        result as AssistantEngineResult.Matches
        assertEquals(AssistantIntent.SAFETY, result.parsedQuery.intent)
        assertEquals("safety-frostbite", result.hits.first().document.canonicalId)
        assertNull(result.recommendedTarget)
    }

    @Test
    fun unrelatedQueryDoesNotGetInventedResultFromSectionOrFamilyBoosts() {
        val result = engine().query("абракадабра неизвестный объект")

        assertTrue(result is AssistantEngineResult.NoResult)
    }

    @Test
    fun normalizationPreservesTechnicalSeriesAndAliases() {
        val parsed = AssistantQueryParser.parse("На ДВА ЭС ПЯТЬ КА гэ-вэ не держится")

        assertEquals(TechnicalFamily.ERMAK, parsed.family)
        assertEquals("MAIN_BREAKER", parsed.componentKey)
        assertEquals(AssistantIntent.TROUBLESHOOT, parsed.intent)
        assertTrue("2эс5к" in parsed.normalizedText)
        assertTrue("гв" in parsed.normalizedText)
    }

    private fun engine(): AssistantEngine = AssistantEngine(
        InMemoryAssistantIndex(
            listOf(
                document(
                    id = "vl80-gv-fault",
                    family = TechnicalFamily.VL80S,
                    section = TechnicalSection.DIAGNOSTICS,
                    title = "Главный выключатель не включается",
                    aliases = setOf("ГВ не включается", "ГВ не держится"),
                    critical = true,
                    target = AssistantTarget.Vl80Diagnostic("vl80-gv-fault")
                ),
                document(
                    id = "ermak-gv-fault",
                    family = TechnicalFamily.ERMAK,
                    section = TechnicalSection.DIAGNOSTICS,
                    title = "Главный выключатель не включается",
                    aliases = setOf("ГВ не включается", "ГВ не держится"),
                    critical = true,
                    target = AssistantTarget.ErmakDiagnostic("ermak-gv-fault")
                ),
                document(
                    id = "vl80-gv-reference",
                    family = TechnicalFamily.VL80S,
                    section = TechnicalSection.EQUIPMENT,
                    title = "Главный выключатель",
                    aliases = setOf("ГВ"),
                    target = AssistantTarget.Technical(
                        TechnicalFamily.VL80S,
                        TechnicalSection.EQUIPMENT,
                        "vl80-gv-reference"
                    )
                ),
                document(
                    id = "ermak-gv-scheme",
                    family = TechnicalFamily.ERMAK,
                    section = TechnicalSection.ELECTRICAL,
                    title = "Главный выключатель на электрической схеме",
                    aliases = setOf("ГВ", "главный выключатель"),
                    target = AssistantTarget.Technical(
                        TechnicalFamily.ERMAK,
                        TechnicalSection.ELECTRICAL,
                        "ermak-gv-scheme"
                    )
                ),
                document(
                    id = "safety-frostbite",
                    family = TechnicalFamily.VL80S,
                    section = TechnicalSection.SAFETY,
                    title = "Обморожение",
                    aliases = setOf("обморожение"),
                    critical = true,
                    target = AssistantTarget.Technical(
                        TechnicalFamily.VL80S,
                        TechnicalSection.SAFETY,
                        "safety-frostbite"
                    )
                )
            )
        )
    )

    private fun document(
        id: String,
        family: TechnicalFamily,
        section: TechnicalSection,
        title: String,
        aliases: Set<String>,
        critical: Boolean = false,
        target: AssistantTarget
    ) = AssistantDocument(
        key = "test:$id",
        canonicalId = id,
        kind = AssistantDocumentKind.TECHNICAL_ENTRY,
        family = family,
        section = section,
        title = title,
        summary = "",
        body = "",
        aliases = aliases,
        componentIds = emptySet(),
        symptomTerms = emptySet(),
        tags = emptySet(),
        relatedIds = emptySet(),
        safetyCritical = critical,
        target = target
    )
}
