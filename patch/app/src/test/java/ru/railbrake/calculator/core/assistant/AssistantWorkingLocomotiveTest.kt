package ru.railbrake.calculator.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.WorkingLocomotive
import ru.railbrake.calculator.core.KnowledgeRepository
import ru.railbrake.calculator.ui.firstAidTopics

class AssistantWorkingLocomotiveTest {
    @Test fun threeDieselProfilesResolveIndependentlyAndEdbDoesNotLeakIntoChme3e() {
        assertEquals(WorkingLocomotive.CHME3, WorkingLocomotive.explicitlyNamed("чмэ три"))
        assertEquals(WorkingLocomotive.CHME3T, WorkingLocomotive.explicitlyNamed("чмэ три т"))
        assertEquals(WorkingLocomotive.CHME3E, WorkingLocomotive.explicitlyNamed("чмэ три э"))
        assertEquals(TechnicalFamily.CHME3E, AssistantQueryParser.parse("чмэ три э позиция есть тяги нет").family)

        val engine = AssistantEngine(InMemoryAssistantIndex(listOf(fault(TechnicalFamily.CHME3E))), TechnicalFamily.CHME3E)
        val mismatch = engine.query("На ЧМЭ3Э реостатный тормоз не работает")
        assertTrue(mismatch is AssistantEngineResult.NoResult)
        mismatch as AssistantEngineResult.NoResult
        assertTrue(mismatch.message.contains("ЧМЭ3Т"))
    }
    private fun fault(family: TechnicalFamily): AssistantDocument = AssistantDocument(
        key = "diagnostic:${family.name}:gv",
        canonicalId = "${family.name}-gv",
        kind = if (family == TechnicalFamily.VL80S) AssistantDocumentKind.VL80_DIAGNOSTIC
            else AssistantDocumentKind.ERMAK_DIAGNOSTIC,
        family = family,
        section = TechnicalSection.DIAGNOSTICS,
        title = "Главный выключатель не включается",
        summary = "ГВ не включается",
        body = "",
        aliases = setOf("ГВ не включается"),
        componentIds = emptySet(), symptomTerms = setOf("ГВ не включается"),
        tags = emptySet(), relatedIds = emptySet(), safetyCritical = true,
        target = if (family == TechnicalFamily.VL80S) AssistantTarget.Vl80Diagnostic("VL80S-gv")
            else AssistantTarget.ErmakDiagnostic("ERMAK-gv")
    )

    @Test fun workingChoiceIsNotInferredFromLegacyDefaults() {
        assertNull(WorkingLocomotive.fromStored(null))
        assertNull(WorkingLocomotive.fromStored("UNKNOWN"))
        assertEquals(TechnicalFamily.ERMAK, WorkingLocomotive.ERMAK_2ES5K.family)
        assertEquals(TechnicalFamily.ERMAK, WorkingLocomotive.ERMAK_3ES5K.family)
        assertEquals(WorkingLocomotive.ERMAK_3ES5K,
            WorkingLocomotive.explicitlyNamed("3эс5к ГВ не включается"))
    }

    @Test fun commonKnowledgeDoesNotBecomeSeriesSpecificFromMentioningALocomotive() {
        KnowledgeRepository.articles.forEach { article ->
            val family = KnowledgeArticleAssistantAdapter.adapt(article).family
            if (article.id.startsWith("vl80-")) assertEquals(TechnicalFamily.VL80S, family)
            else if (article.id.startsWith("ermak-")) assertEquals(TechnicalFamily.ERMAK, family)
            else assertNull("Common article ${article.id}", family)
        }
    }

    @Test fun chosenCatalogResolvesFaultWithoutSeriesButDoesNotContainTheOtherFamily() {
        val vl80 = fault(TechnicalFamily.VL80S)
        val engine = AssistantEngine(InMemoryAssistantIndex(listOf(vl80) +
            firstAidTopics.map(FirstAidAssistantAdapter::adapt)), TechnicalFamily.VL80S)
        val result = engine.query("ГВ не включается")
        assertTrue(result is AssistantEngineResult.Matches)
        result as AssistantEngineResult.Matches
        assertEquals(TechnicalFamily.VL80S, result.parsedQuery.family)
        assertEquals(vl80.key, result.hits.first().document.key)
        assertTrue(result.hits.all { it.document.family == null || it.document.family == TechnicalFamily.VL80S })
    }

    @Test fun explicitlyNamedOtherFamilyUsesItsOwnIndexAndCommonAidRemainsAvailable() {
        val ermak = fault(TechnicalFamily.ERMAK)
        val engine = AssistantEngine(InMemoryAssistantIndex(listOf(ermak) +
            firstAidTopics.map(FirstAidAssistantAdapter::adapt)), TechnicalFamily.ERMAK)
        val result = engine.query("На Ермаке ГВ не включается")
        assertTrue(result is AssistantEngineResult.Matches)
        result as AssistantEngineResult.Matches
        assertEquals(TechnicalFamily.ERMAK, result.parsedQuery.family)
        assertEquals(ermak.key, result.hits.first().document.key)
        val aid = engine.query("человек замёрз")
        assertTrue(aid is AssistantEngineResult.Matches)
        aid as AssistantEngineResult.Matches
        assertNull(aid.parsedQuery.family)
        assertTrue(aid.hits.all { it.document.kind == AssistantDocumentKind.FIRST_AID })
    }
}
