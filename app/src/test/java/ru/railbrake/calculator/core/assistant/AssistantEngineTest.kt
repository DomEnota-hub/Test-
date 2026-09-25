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

    @Test
    fun recognizesCommonRailwayComponentsAndAsrForms() {
        val cases = mapOf(
            "э ка гэ застрял на позиции" to "EKG",
            "на тэ дэ искрение" to "TRACTION_MOTOR",
            "вэ у греется" to "RECTIFIER",
            "тэ эм не заряжается" to "BRAKE_PIPE",
            "э пэ ка срабатывает самопроизвольно" to "EPK",
            "пантограф не поднимается" to "PANTOGRAPH",
            "ка эм триста девяносто пять не держит давление" to "DRIVER_BRAKE_VALVE"
        )

        cases.forEach { (query, componentKey) ->
            val parsed = AssistantQueryParser.parse(query)
            assertEquals("Wrong component for: $query", componentKey, parsed.componentKey)
            assertEquals("Wrong intent for: $query", AssistantIntent.TROUBLESHOOT, parsed.intent)
        }
    }

    @Test
    fun proceduralWordingIsNotMistakenForFault() {
        val parsed = AssistantQueryParser.parse("как проверить аккумуляторную батарею")

        assertEquals("BATTERY", parsed.componentKey)
        assertEquals(AssistantIntent.PROCEDURE, parsed.intent)
        assertEquals(TechnicalSection.KNOWLEDGE, parsed.preferredSection)
    }

    @Test
    fun recognizesInflectedComponentNames() {
        val cases = mapOf(
            "не включается главный выключатель" to "MAIN_BREAKER",
            "проверка главного выключателя" to "MAIN_BREAKER",
            "неисправность тягового трансформатора" to "TRANSFORMER",
            "осмотр аккумуляторной батареи" to "BATTERY",
            "нет давления в тормозной магистрали" to "BRAKE_PIPE"
        )

        cases.forEach { (query, componentKey) ->
            assertEquals("Wrong component for: $query", componentKey, AssistantQueryParser.parse(query).componentKey)
        }
    }

    @Test
    fun failureLanguagePrioritizesDiagnosticOverReferenceForSameComponent() {
        val engine = engine()
        val queries = listOf(
            "компрессор не выключается",
            "компрессор не работает",
            "ошибка компрессора",
            "отказ компрессора",
            "компрессор неисправен"
        )

        queries.forEach { query ->
            val result = engine.query(query)
            assertTrue("Expected matches for: $query", result is AssistantEngineResult.Matches)
            result as AssistantEngineResult.Matches
            assertEquals("Wrong intent for: $query", AssistantIntent.TROUBLESHOOT, result.parsedQuery.intent)
            assertEquals("Wrong section for: $query", TechnicalSection.DIAGNOSTICS, result.parsedQuery.preferredSection)
            assertEquals("Wrong top hit for: $query", "vl80-compressor-fault", result.hits.first().document.canonicalId)
        }
    }

    @Test
    fun definitionLanguageStillPrefersReferenceForComponent() {
        val result = engine().query("что такое компрессор")

        assertTrue(result is AssistantEngineResult.Matches)
        result as AssistantEngineResult.Matches
        assertEquals(AssistantIntent.DEFINE_TERM, result.parsedQuery.intent)
        assertEquals(TechnicalSection.EQUIPMENT, result.parsedQuery.preferredSection)
        assertEquals("vl80-compressor-reference", result.hits.first().document.canonicalId)
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
                    id = "vl80-compressor-fault",
                    family = TechnicalFamily.VL80S,
                    section = TechnicalSection.DIAGNOSTICS,
                    title = "Компрессор не запускается и не создаёт давление",
                    aliases = setOf("компрессор", "компрессор не работает", "ошибка компрессора", "отказ компрессора"),
                    critical = true,
                    target = AssistantTarget.Vl80Diagnostic("vl80-compressor-fault")
                ),
                document(
                    id = "vl80-compressor-reference",
                    family = TechnicalFamily.VL80S,
                    section = TechnicalSection.EQUIPMENT,
                    title = "Компрессор",
                    aliases = setOf("компрессор", "компрессор не выключается"),
                    target = AssistantTarget.Technical(
                        TechnicalFamily.VL80S,
                        TechnicalSection.EQUIPMENT,
                        "vl80-compressor-reference"
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
