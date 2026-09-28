package ru.railbrake.calculator.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.ui.firstAidTopics

class AssistantSafetyRoutingTest {
    private val noise = AssistantDocument(
        key = "diagnostic:impact", canonicalId = "impact",
        kind = AssistantDocumentKind.VL80_DIAGNOSTIC,
        family = TechnicalFamily.VL80S, section = TechnicalSection.DIAGNOSTICS,
        title = "Удар предмета", summary = "Человек увидел дым после удара",
        body = "Человек замерз, кровь из носа, удар током, нет сознания.",
        aliases = emptySet(), componentIds = emptySet(), symptomTerms = emptySet(),
        tags = emptySet(), relatedIds = emptySet(), safetyCritical = true,
        target = AssistantTarget.Vl80Diagnostic("impact")
    )
    private val engine = AssistantEngine(InMemoryAssistantIndex(firstAidTopics.map(FirstAidAssistantAdapter::adapt) + noise))

    @Test fun everyFirstAidTopicIsAnchoredBySpokenSymptoms() {
        val cases = mapOf(
            "у человека кровь из носа" to "nosebleed",
            "он не дышит" to "cpr",
            "человек без сознания" to "unconscious",
            "кровь не останавливается" to "bleeding",
            "подавился во время еды" to "airway",
            "сломал руку" to "trauma",
            "рана живота" to "chest_abdomen",
            "обжег руку" to "burn",
            "тепловой удар" to "heat",
            "человека ударило током" to "electric",
            "отравился газом" to "poisoning",
            "химический ожог" to "chemical",
            "переохлаждение" to "hypothermia",
            "обморозил пальцы" to "frostbite",
            "укусила собака" to "bites",
            "паническая атака" to "stress",
            "судороги" to "seizure"
        )
        cases.forEach { (phrase, id) ->
            val result = engine.query(phrase)
            assertTrue("$phrase: $result", result is AssistantEngineResult.Matches)
            result as AssistantEngineResult.Matches
            assertEquals("$phrase", AssistantIntent.SAFETY, result.parsedQuery.intent)
            assertEquals("$phrase", id, result.hits.first().document.canonicalId)
            assertTrue("$phrase: unrelated result", result.hits.all { it.document.canonicalId == id })
        }
    }

    @Test fun generalColdExposureOffersOnlyRelevantDistinctTopics() {
        listOf("человек замёрз", "сильно замерз", "человек замерзает на морозе").forEach { phrase ->
            val result = engine.query(phrase) as AssistantEngineResult.Matches
            assertEquals("$phrase", "hypothermia", result.hits.first().document.canonicalId)
            assertEquals("$phrase", setOf("hypothermia", "frostbite"), result.hits.map { it.document.canonicalId }.toSet())
        }
        assertEquals(setOf("hypothermia"), AssistantQueryParser.parse("переохлаждение").safetyTopicIds)
        assertEquals(setOf("frostbite"), AssistantQueryParser.parse("обморожение").safetyTopicIds)
    }

    @Test fun railwayCurrentAndImpactsDoNotBecomeInjuries() {
        listOf("датчик тока", "удар предмета по локомотиву", "замерзло реле").forEach { phrase ->
            assertTrue("$phrase", AssistantQueryParser.parse(phrase).safetyTopicIds.isEmpty())
        }
    }

    @Test fun firstAidInstructionWordsCannotMakeAnotherTopicRelevant() {
        val result = engine.query("человек замёрз") as AssistantEngineResult.Matches
        assertTrue(result.hits.none { it.document.canonicalId in setOf("nosebleed", "unconscious", "airway", "impact") })
    }

    @Test fun categoryNameWithoutSymptomsDoesNotShowAnArbitraryEmergencyCard() {
        listOf("ОПП", "первая помощь").forEach { phrase ->
            assertTrue("$phrase", engine.query(phrase) is AssistantEngineResult.NoResult)
        }
    }
}
