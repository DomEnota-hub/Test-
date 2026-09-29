package ru.railbrake.calculator.core.assistant

import java.io.File
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.KnowledgeRepository
import ru.railbrake.calculator.core.TechnicalEntry
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.parseErmakDiagnostics
import ru.railbrake.calculator.ui.firstAidTopics

/** Queries the source catalog by its visible headings, never by internal IDs. */
class AssistantCatalogCoverageTest {
    private data class Source(val asset: String, val array: String, val family: TechnicalFamily,
                              val section: TechnicalSection, val nameField: String)

    private val sources = listOf(
        Source("vl80s_equipment", "records", TechnicalFamily.VL80S, TechnicalSection.EQUIPMENT, "name"),
        Source("ermak_equipment", "records", TechnicalFamily.ERMAK, TechnicalSection.EQUIPMENT, "name"),
        Source("vl80s_acceptance", "items", TechnicalFamily.VL80S, TechnicalSection.ACCEPTANCE, "title"),
        Source("ermak_knowledge", "articles", TechnicalFamily.ERMAK, TechnicalSection.KNOWLEDGE, "title"),
        Source("vl80s_electrical", "baseSchemes", TechnicalFamily.VL80S, TechnicalSection.ELECTRICAL, "title"),
        Source("vl80s_pneumatic", "views", TechnicalFamily.VL80S, TechnicalSection.PNEUMATIC, "title"),
        Source("ermak_schemes", "schemes", TechnicalFamily.ERMAK, TechnicalSection.ELECTRICAL, "title")
    )

    private fun asset(name: String): JSONObject {
        val file = listOf(
            File("src/main/assets/technical/$name.json.gz"),
            File("app/src/main/assets/technical/$name.json.gz"),
            File("../app/src/main/assets/technical/$name.json.gz")
        ).firstOrNull(File::isFile) ?: error("Missing $name")
        return JSONObject(GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() })
    }

    @Test fun everyCanonicalCardHasVisibleSearchWordsAndCanBeRetrievedWithoutItsId() {
        val technical = sources.flatMap { source ->
            val entries = asset(source.asset).getJSONArray(source.array)
            (0 until entries.length()).map { n ->
                val raw = entries.getJSONObject(n)
                val title = raw.optString(source.nameField)
                val aliases = raw.optJSONArray("aliases")?.let { list ->
                    (0 until list.length()).map { list.optString(it) }
                }.orEmpty()
                TechnicalEntryAssistantAdapter.adapt(TechnicalEntry(
                    id = raw.getString("id"), family = source.family,
                    section = if (source.asset == "ermak_schemes" &&
                        raw.optString("schemeType").let { it.contains("pneumatic", true) || it.contains("brake", true) })
                        TechnicalSection.PNEUMATIC else source.section,
                    title = title, subtitle = raw.optString("summary"), status = "INFORMATION",
                    blocks = emptyList(), searchText = (listOf(title) + aliases).joinToString(" ")
                ))
            }
        }
        val diagnostics = DiagnosticRepository.scenarios.map(Vl80DiagnosticAssistantAdapter::adapt) +
            parseErmakDiagnostics(asset("ermak_diagnostics")).map(ErmakDiagnosticAssistantAdapter::adapt)
        val knowledge = KnowledgeRepository.allArticles.map(KnowledgeArticleAssistantAdapter::adapt)
        val aid = firstAidTopics.map(FirstAidAssistantAdapter::adapt)
        // The actual Ermak acceptance checklist derives its 111 equipment
        // checkpoints from the same records at runtime.
        val ermakAcceptance = technical.filter { it.family == TechnicalFamily.ERMAK &&
            it.section == TechnicalSection.EQUIPMENT }.map { equipment ->
            TechnicalEntryAssistantAdapter.adapt(TechnicalEntry(
                id = "ER-ACC-${equipment.canonicalId.removePrefix("ER-EQ-")}",
                family = TechnicalFamily.ERMAK, section = TechnicalSection.ACCEPTANCE,
                title = equipment.title, subtitle = equipment.summary,
                status = "CHECK", blocks = emptyList(), searchText = equipment.searchText
            ))
        }
        val safetyTitles = listOf(
            "FACTORS" to "Опасные и вредные производственные факторы",
            "RISK" to "Выявление опасностей и оценка риска",
            "PROTECTION" to "Меры защиты: технические, организационные и СИЗ",
            "ELECTRICAL" to "Электробезопасность и границы допуска",
            "ROLLING-STOCK" to "Безопасность рядом с подвижным составом и на путях",
            "STOP" to "Когда работу нужно прекратить и сообщить",
            "TRAINING" to "Обучение, инструктаж и первая помощь"
        )
        val safety = TechnicalFamily.entries.flatMap { family -> safetyTitles.map { (suffix, title) ->
            TechnicalEntryAssistantAdapter.adapt(TechnicalEntry(
                id = "SAFETY-${family.name}-$suffix", family = family,
                section = TechnicalSection.SAFETY, title = title, subtitle = "",
                status = "INFORMATION", blocks = emptyList(), searchText = title
            ))
        } }
        val documents = (technical + ermakAcceptance + diagnostics + knowledge + aid + safety)
            .distinctBy(AssistantDocument::key)
        val index = InMemoryAssistantIndex(documents)
        val missed = mutableListOf<String>()
        val relevant = documents.filter { it.section in setOf(
            TechnicalSection.ACCEPTANCE, TechnicalSection.EQUIPMENT, TechnicalSection.ELECTRICAL,
            TechnicalSection.PNEUMATIC, TechnicalSection.DIAGNOSTICS, TechnicalSection.KNOWLEDGE,
            TechnicalSection.SAFETY
        ) }
        relevant.forEach { card ->
            if (card.title.isBlank() || card.title.normalizeAssistantText().none(Char::isLetter)) {
                missed += "${card.canonicalId}: empty visible vocabulary"
                return@forEach
            }
            val cue = when (card.section) {
                TechnicalSection.ACCEPTANCE -> "приемка"
                TechnicalSection.DIAGNOSTICS -> "неисправность"
                TechnicalSection.SAFETY -> if (card.kind == AssistantDocumentKind.FIRST_AID) "первая помощь" else "охрана труда"
                TechnicalSection.EQUIPMENT -> "атлас"
                TechnicalSection.ELECTRICAL -> "электросхема"
                TechnicalSection.PNEUMATIC -> "пневмосхема"
                else -> "справочник"
            }
            val family = when (card.family) {
                TechnicalFamily.VL80S -> "ВЛ80С"
                TechnicalFamily.ERMAK -> "Ермак"
                null -> ""
            }
            val phrase = "$cue $family ${card.title}"
            val parsed = AssistantQueryParser.parse(phrase)
            val hits = index.search(AssistantSearchRequest(
                query = parsed.searchText, literalQuery = parsed.normalizedText,
                family = card.family, preferredSection = card.section,
                failureModes = parsed.failureModes, limit = 5
            ))
            if (hits.none { it.document.key == card.key }) {
                missed += "${card.canonicalId}: $phrase -> ${hits.joinToString { it.document.canonicalId }}"
            }
        }
        assertTrue("Visible-card coverage ${relevant.size - missed.size}/${relevant.size}:\n" +
            missed.take(40).joinToString("\n"), missed.isEmpty())
    }
}
