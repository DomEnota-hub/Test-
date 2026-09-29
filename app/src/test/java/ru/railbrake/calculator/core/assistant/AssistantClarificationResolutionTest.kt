package ru.railbrake.calculator.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.WorkingLocomotive

class AssistantClarificationResolutionTest {

    @Test
    fun seriesReplyContinuesOriginalMainBreakerFault() {
        val first = AssistantConversation.submit(engine(), "ГВ не включается")
        assertEquals(AssistantAmbiguity.SERIES_REQUIRED, first.pending?.missingParameter)
        assertTrue(first.pending!!.candidateIds.isNotEmpty())

        listOf("ВЛ", "ВЛ80", "восемьдесят эс", "на ВЛ80С").forEach { reply ->
            val next = AssistantConversation.submit(engine(), reply, first.pending)
            assertTrue("Expected matches for $reply", next.result is AssistantEngineResult.Matches)
            assertEquals(TechnicalFamily.VL80S, next.result!!.parsedQuery.family)
            assertEquals("vl80-gv-fault", (next.result as AssistantEngineResult.Matches).hits.first().document.canonicalId)
            assertNull(next.pending)
            assertTrue(next.continuedFromPending)
        }
    }

    @Test
    fun variantReplyRetainsItsNameInTheResultAndTemporaryNavigation() {
        val first = AssistantConversation.submit(engine(), "ГВ не включается")
        val next = AssistantConversation.submit(engine(), "на 3ЭС5К", first.pending)

        assertTrue(next.result is AssistantEngineResult.Matches)
        assertEquals(TechnicalFamily.ERMAK, next.result!!.parsedQuery.family)
        assertEquals(WorkingLocomotive.ERMAK_3ES5K,
            WorkingLocomotive.explicitlyNamed(next.result!!.parsedQuery.normalizedText))
        assertTrue(next.continuedFromPending)
    }

    @Test
    fun incompatibleSeriesAreFlaggedBeforeChoosingAnIndex() {
        assertTrue(AssistantQueryParser.hasConflictingSeries("ВЛ80С или на Ермаке ГВ не включается"))
        assertTrue(AssistantQueryParser.hasConflictingSeries("2ЭС5К и 3ЭС5К"))
        assertFalse(AssistantQueryParser.hasConflictingSeries("На 2ЭС5К ГВ не включается"))
        val result = engine().query("ВЛ80С или Ермак: ГВ не включается")
        assertTrue(result is AssistantEngineResult.Clarify)
        assertEquals(AssistantAmbiguity.SERIES_REQUIRED,
            (result as AssistantEngineResult.Clarify).clarification.reason)
    }

    @Test
    fun componentReplyContinuesErmakFault() {
        val first = AssistantConversation.submit(engine(), "не работает на Ермаке")
        assertEquals(AssistantAmbiguity.COMPONENT_REQUIRED, first.pending?.missingParameter)

        val next = AssistantConversation.submit(engine(), "компрессор", first.pending)
        assertTrue(next.result is AssistantEngineResult.Matches)
        assertEquals(TechnicalFamily.ERMAK, next.result!!.parsedQuery.family)
        assertEquals("COMPRESSOR", next.result!!.parsedQuery.componentKey)
        assertEquals(AssistantIntent.TROUBLESHOOT, next.result!!.parsedQuery.intent)
        assertEquals("ermak-compressor-fault", (next.result as AssistantEngineResult.Matches).hits.first().document.canonicalId)
    }

    @Test
    fun topicThenSeriesCanBeResolvedAcrossTwoReplies() {
        val first = AssistantConversation.submit(engine(), "ГВ")
        assertEquals(AssistantAmbiguity.TOPIC_SCOPE, first.pending?.missingParameter)

        val second = AssistantConversation.submit(engine(), "диагностика", first.pending)
        assertEquals(AssistantAmbiguity.SERIES_REQUIRED, second.pending?.missingParameter)

        val third = AssistantConversation.submit(engine(), "ВЛ", second.pending)
        assertTrue(third.result is AssistantEngineResult.Matches)
        assertEquals("vl80-gv-fault", (third.result as AssistantEngineResult.Matches).hits.first().document.canonicalId)
    }

    @Test
    fun procedureReplyUsesExistingCanonicalMaterial() {
        val first = AssistantConversation.submit(engine(), "проба тормозов")
        assertEquals(AssistantAmbiguity.PROCEDURE_TYPE_REQUIRED, first.pending?.missingParameter)

        val next = AssistantConversation.submit(engine(), "полная", first.pending)
        assertTrue(next.result is AssistantEngineResult.Matches)
        assertEquals("brakes-full-test", (next.result as AssistantEngineResult.Matches).hits.first().document.canonicalId)
    }

    @Test
    fun unknownShortReplyKeepsQuestionButNewRequestEscapesContext() {
        val first = AssistantConversation.submit(engine(), "ГВ не включается")
        val unknown = AssistantConversation.submit(engine(), "непонятно", first.pending)
        assertNotNull(unknown.pending)
        assertEquals(AssistantAmbiguity.SERIES_REQUIRED, unknown.pending?.missingParameter)

        val replacement = AssistantConversation.submit(engine(), "Ермак компрессор не работает", unknown.pending)
        assertFalse(replacement.continuedFromPending)
        assertTrue(replacement.result is AssistantEngineResult.Matches)
        assertEquals("ermak-compressor-fault", (replacement.result as AssistantEngineResult.Matches).hits.first().document.canonicalId)
    }

    @Test
    fun cancellationClearsPendingContext() {
        val first = AssistantConversation.submit(engine(), "ГВ не включается")
        val cancelled = AssistantConversation.submit(engine(), "отмена", first.pending)

        assertTrue(cancelled.cancelled)
        assertNull(cancelled.pending)
        assertNull(cancelled.result)
    }

    @Test
    fun explicitDiagnosticsChoiceBecomesTroubleshootIntent() {
        val parsed = AssistantQueryParser.parse("диагностика ГВ ВЛ80С")

        assertEquals(AssistantIntent.TROUBLESHOOT, parsed.intent)
        assertEquals(TechnicalFamily.VL80S, parsed.family)
        assertEquals(TechnicalSection.DIAGNOSTICS, parsed.preferredSection)
        assertNull(parsed.ambiguity)
    }

    @Test
    fun explicitDescriptionChoiceBecomesReferenceIntent() {
        val parsed = AssistantQueryParser.parse("описание ГВ ВЛ80С")

        assertEquals(AssistantIntent.DEFINE_TERM, parsed.intent)
        assertEquals(TechnicalSection.EQUIPMENT, parsed.preferredSection)
        assertNull(parsed.ambiguity)
    }

    @Test
    fun explicitBrakeTestTypeDoesNotAskTypeAgain() {
        val parsed = AssistantQueryParser.parse("полная проба тормозов")

        assertEquals(AssistantIntent.PROCEDURE, parsed.intent)
        assertNull(parsed.ambiguity)
    }

    private fun engine(): AssistantEngine = AssistantEngine(
        InMemoryAssistantIndex(
            listOf(
                document("vl80-gv-fault", "Главный выключатель не включается", setOf("ГВ не включается"), TechnicalFamily.VL80S, TechnicalSection.DIAGNOSTICS),
                document("ermak-gv-fault", "Главный выключатель не включается", setOf("ГВ не включается"), TechnicalFamily.ERMAK, TechnicalSection.DIAGNOSTICS),
                document("ermak-compressor-fault", "Компрессор не работает", setOf("компрессор не работает"), TechnicalFamily.ERMAK, TechnicalSection.DIAGNOSTICS),
                document("brakes-full-test", "Полное опробование тормозов", setOf("полная проба тормозов"), null, TechnicalSection.KNOWLEDGE)
            )
        )
    )

    private fun document(
        id: String,
        title: String,
        aliases: Set<String>,
        family: TechnicalFamily?,
        section: TechnicalSection
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
        symptomTerms = aliases,
        tags = emptySet(),
        relatedIds = emptySet(),
        safetyCritical = section == TechnicalSection.DIAGNOSTICS,
        target = AssistantTarget.Technical(family ?: TechnicalFamily.VL80S, section, id)
    )
}
