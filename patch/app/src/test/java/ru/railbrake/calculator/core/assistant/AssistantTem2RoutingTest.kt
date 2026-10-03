package ru.railbrake.calculator.core.assistant

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.LocomotiveProfileRegistry
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.parseErmakDiagnostics

class AssistantTem2RoutingTest {
    private fun asset(name: String): JSONObject {
        val path = listOf(File("src/main/assets/technical/$name"), File("app/src/main/assets/technical/$name"),
            File("../app/src/main/assets/technical/$name")).first(File::isFile)
        return JSONObject(path.readText())
    }

    private fun documents(family: TechnicalFamily): List<AssistantDocument> {
        val name = if (family == TechnicalFamily.TEM2) "tem2_tem2" else "tem2_tem2u"
        val diagnostics = parseErmakDiagnostics(asset("${name}_diagnostics.json"))
            .map { Chme3DiagnosticAssistantAdapter.adapt(it, family) }
        val distractor = AssistantDocument(
            key = "atlas:${family.name}:diesel", canonicalId = "${family.name}-EQ-DIESEL",
            kind = AssistantDocumentKind.TECHNICAL_ENTRY, family = family,
            section = TechnicalSection.EQUIPMENT, title = "Дизель и компрессор",
            summary = "Двигатель, компрессор и система давления", body = "Дизель может глохнуть, компрессор не выключается",
            aliases = setOf("двигатель", "компрессор"), componentIds = emptySet(),
            symptomTerms = emptySet(), tags = emptySet(), relatedIds = emptySet(), safetyCritical = false,
            target = AssistantTarget.Technical(family, TechnicalSection.EQUIPMENT, "${family.name}-EQ-DIESEL")
        )
        return diagnostics + distractor
    }

    @Test fun profilesAndFaultsRouteToDiagnosticsWithoutSiblingLeakage() {
        val documents = documents(TechnicalFamily.TEM2) + documents(TechnicalFamily.TEM2U)
        val index = InMemoryAssistantIndex(documents)
        for (family in listOf(TechnicalFamily.TEM2, TechnicalFamily.TEM2U)) {
            val engine = AssistantEngine(index, family)
            for (query in listOf("дизель сам заглох", "компрессор не выключается", "не набирает давление")) {
                val result = engine.query(query)
                assertTrue("$family $query: $result", result is AssistantEngineResult.Matches)
                result as AssistantEngineResult.Matches
                assertEquals(family, result.hits.first().document.family)
                assertEquals(TechnicalSection.DIAGNOSTICS, result.hits.first().document.section)
            }
            assertTrue(engine.query("реостатный тормоз ${family.title} не работает") is AssistantEngineResult.NoResult)
        }
        assertEquals("tem2-base", LocomotiveProfileRegistry.fromTechnicalFamily(TechnicalFamily.TEM2).profileId)
        assertEquals("tem2u-improved", LocomotiveProfileRegistry.fromTechnicalFamily(TechnicalFamily.TEM2U).profileId)
    }

    @Test fun namedOtherFamilyOverridesWorkingScopeWithoutMutation() {
        val engine = AssistantEngine(InMemoryAssistantIndex(documents(TechnicalFamily.TEM2) +
            documents(TechnicalFamily.TEM2U)), TechnicalFamily.TEM2)
        val result = engine.query("ТЭМ2У управление одним лицом не работает") as AssistantEngineResult.Matches
        assertEquals(TechnicalFamily.TEM2U, result.hits.first().document.family)
        assertEquals("TEM2-DIAG-022", result.hits.first().document.canonicalId)
        assertEquals(TechnicalFamily.TEM2, AssistantQueryParser.parse("двигатель не работает ТЭМ2").family)
    }
}
