package ru.railbrake.calculator.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

/** User-shaped requests across the separately indexed application sections. */
class AssistantSectionRoutingTest {
    private fun card(id: String, title: String, section: TechnicalSection, family: TechnicalFamily,
                     body: String = "", aliases: Set<String> = emptySet()) = AssistantDocument(
        key = "technical:$id", canonicalId = id, kind = AssistantDocumentKind.TECHNICAL_ENTRY,
        family = family, section = section, title = title, summary = "", body = body,
        aliases = aliases, componentIds = emptySet(), symptomTerms = emptySet(),
        tags = emptySet(), relatedIds = emptySet(), safetyCritical = section == TechnicalSection.SAFETY,
        target = AssistantTarget.Technical(family, section, id)
    )

    @Test fun sectionsDoNotWinMerelyByMentioningAWordInInstructions() {
        val vl = TechnicalFamily.VL80S
        val er = TechnicalFamily.ERMAK
        val engine = AssistantEngine(InMemoryAssistantIndex(listOf(
            card("acceptance", "Приемка ВЛ80С снаружи", TechnicalSection.ACCEPTANCE, vl),
            card("electrical", "Электросхема главного выключателя ВЛ80С", TechnicalSection.ELECTRICAL, vl),
            card("pneumatic", "Пневмосхема тормозной магистрали ВЛ80С", TechnicalSection.PNEUMATIC, vl),
            card("knowledge", "Порядок полной пробы тормозов", TechnicalSection.KNOWLEDGE, vl),
            card("equipment", "Компрессор Ермака", TechnicalSection.EQUIPMENT, er),
            card("safety", "Охрана труда в кабине", TechnicalSection.SAFETY, vl),
            card("fault", "Компрессор Ермака не запускается", TechnicalSection.DIAGNOSTICS, er),
            card("noise", "Случайная карточка", TechnicalSection.KNOWLEDGE, vl,
                body = "Приемка, схема, проба тормозов, компрессор, охрана труда, человек замерз")
        )))
        val cases = mapOf(
            "приемка ВЛ80С снаружи" to "acceptance",
            "покажи электросхему главного выключателя ВЛ80С" to "electrical",
            "пневмосхема тормозной магистрали ВЛ80С" to "pneumatic",
            "порядок полной пробы тормозов" to "knowledge",
            "назначение компрессора Ермака" to "equipment",
            "охрана труда в кабине ВЛ80С" to "safety",
            "Ермак компрессор не запускается" to "fault"
        )
        cases.forEach { (query, expected) ->
            val result = engine.query(query)
            assertTrue("$query: $result", result is AssistantEngineResult.Matches)
            assertEquals("$query", expected, (result as AssistantEngineResult.Matches).hits.first().document.canonicalId)
        }
    }

    @Test fun occupationalSafetyHeadingsAndSpokenNamesDoNotAskForLocomotive() {
        val documents = TechnicalFamily.entries.flatMap { family ->
            listOf(
                "FACTORS" to "Опасные и вредные производственные факторы",
                "RISK" to "Выявление опасностей и оценка риска",
                "PROTECTION" to "Меры защиты: технические, организационные и СИЗ",
                "ELECTRICAL" to "Электробезопасность и границы допуска",
                "ROLLING-STOCK" to "Безопасность рядом с подвижным составом и на путях",
                "STOP" to "Когда работу нужно прекратить и сообщить",
                "TRAINING" to "Обучение, инструктаж и первая помощь"
            ).map { (id, title) ->
                TechnicalEntryAssistantAdapter.adapt(ru.railbrake.calculator.core.TechnicalEntry(
                    id = "SAFETY-${family.name}-$id", family = family, section = TechnicalSection.SAFETY,
                    title = title, subtitle = "", status = "INFORMATION", blocks = emptyList(),
                    searchText = title
                ))
            }
        }
        val engine = AssistantEngine(InMemoryAssistantIndex(documents))
        val cases = mapOf(
            "вредные факторы" to "FACTORS",
            "производственные вредности" to "FACTORS",
            "опасные факторы на работе" to "FACTORS",
            "профессиональные риски" to "RISK",
            "оценка профрисков" to "RISK",
            "чем защититься на работе" to "PROTECTION"
        )
        cases.forEach { (phrase, suffix) ->
            val result = engine.query(phrase)
            assertTrue("$phrase: $result", result is AssistantEngineResult.Matches)
            val hits = (result as AssistantEngineResult.Matches).hits
            assertTrue("$phrase: ${hits.map { it.document.canonicalId }}",
                hits.first().document.canonicalId.endsWith(suffix))
            assertEquals("$phrase: duplicated series", 1,
                hits.count { it.document.canonicalId.endsWith(suffix) })
        }
    }
}
