package ru.railbrake.calculator.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.DiagnosticActionLevel
import ru.railbrake.calculator.core.DiagnosticCheck
import ru.railbrake.calculator.core.DiagnosticScenario
import ru.railbrake.calculator.core.DiagnosticSeverity
import ru.railbrake.calculator.core.ErmakDiagnosticNode
import ru.railbrake.calculator.core.ErmakDiagnosticScenario
import ru.railbrake.calculator.core.KnowledgeArticle
import ru.railbrake.calculator.core.KnowledgeSource
import ru.railbrake.calculator.core.TechnicalBlock
import ru.railbrake.calculator.core.TechnicalEntry
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

class AssistantIndexTest {

    @Test
    fun technicalEntryPreservesCanonicalIdAndNativeSearchText() {
        val entry = technicalEntry(
            id = "VL-EQ-GV",
            section = TechnicalSection.EQUIPMENT,
            title = "Главный выключатель",
            searchText = "главный выключатель гв высоковольтный аппарат"
        )

        val document = TechnicalEntryAssistantAdapter.adapt(entry)

        assertEquals("VL-EQ-GV", document.canonicalId)
        assertEquals(AssistantTarget.Technical(TechnicalFamily.VL80S, TechnicalSection.EQUIPMENT, "VL-EQ-GV"), document.target)
        assertTrue("гв" in document.searchText)
        assertTrue("высоковольтный аппарат" in document.searchText)
    }

    @Test
    fun vl80DiagnosticRanksAboveReferenceForFaultQuery() {
        val diagnostic = Vl80DiagnosticAssistantAdapter.adapt(vl80Diagnostic())
        val reference = TechnicalEntryAssistantAdapter.adapt(
            technicalEntry(
                id = "VL-EQ-GV",
                section = TechnicalSection.EQUIPMENT,
                title = "Главный выключатель",
                searchText = "главный выключатель гв"
            )
        )
        val index = InMemoryAssistantIndex(listOf(reference, diagnostic))

        val top = index.search(
            AssistantSearchRequest(
                query = "ГВ не включается",
                family = TechnicalFamily.VL80S,
                preferredSection = TechnicalSection.DIAGNOSTICS
            )
        ).first()

        assertEquals("gv-no-close", top.document.canonicalId)
        assertEquals(AssistantDocumentKind.VL80_DIAGNOSTIC, top.document.kind)
    }

    @Test
    fun ermakDiagnosticUsesCanonicalEquipmentIdForReranking() {
        val diagnostic = ErmakDiagnosticAssistantAdapter.adapt(ermakDiagnostic())
        val reference = TechnicalEntryAssistantAdapter.adapt(
            TechnicalEntry(
                id = "ER-EQ-GV",
                family = TechnicalFamily.ERMAK,
                section = TechnicalSection.EQUIPMENT,
                title = "Главный выключатель",
                subtitle = "Высоковольтный аппарат",
                status = "VERIFIED",
                blocks = listOf(TechnicalBlock("Назначение", listOf("Коммутация силовой цепи"))),
                searchText = "главный выключатель гв"
            )
        )
        val index = InMemoryAssistantIndex(listOf(reference, diagnostic))

        val top = index.search(
            AssistantSearchRequest(
                query = "главный выключатель не принимает команду",
                family = TechnicalFamily.ERMAK,
                preferredSection = TechnicalSection.DIAGNOSTICS,
                componentId = "ER-EQ-GV"
            )
        ).first()

        assertEquals("ER-DIAG-GV", top.document.canonicalId)
        assertEquals(AssistantTarget.ErmakDiagnostic("ER-DIAG-GV"), top.document.target)
    }

    @Test
    fun acceptanceAndSafetyEntriesAreMarkedCritical() {
        val acceptance = TechnicalEntryAssistantAdapter.adapt(
            technicalEntry(
                id = "VL80-ACC-TEST",
                section = TechnicalSection.ACCEPTANCE,
                title = "Проверка при приёмке",
                searchText = "приемка проверка"
            )
        )
        val safety = TechnicalEntryAssistantAdapter.adapt(
            technicalEntry(
                id = "SAFETY-VL80S-TEST",
                section = TechnicalSection.SAFETY,
                title = "Охрана труда",
                searchText = "охрана труда безопасность"
            )
        )

        assertTrue(acceptance.safetyCritical)
        assertTrue(safety.safetyCritical)
    }

    @Test
    fun knowledgeArticleInfersErmakFamilyFromSeriesTag() {
        val article = KnowledgeArticle(
            id = "ermak-test",
            title = "Оборудование 3ЭС5К",
            category = "Оборудование",
            status = "Учебный материал",
            summary = "Краткая справка",
            body = listOf("Описание оборудования"),
            tags = listOf("3ЭС5К", "Ермак"),
            source = KnowledgeSource("Тестовый источник")
        )

        val document = KnowledgeArticleAssistantAdapter.adapt(article)

        assertEquals(TechnicalFamily.ERMAK, document.family)
        assertEquals("ermak-test", document.canonicalId)
    }

    private fun technicalEntry(
        id: String,
        section: TechnicalSection,
        title: String,
        searchText: String
    ) = TechnicalEntry(
        id = id,
        family = TechnicalFamily.VL80S,
        section = section,
        title = title,
        subtitle = "",
        status = "VERIFIED",
        blocks = listOf(TechnicalBlock("Описание", listOf(title))),
        searchText = searchText
    )

    private fun vl80Diagnostic() = DiagnosticScenario(
        id = "gv-no-close",
        category = "Высоковольтные цепи",
        title = "Главный выключатель не включается или отключается",
        summary = "ГВ не принимает команду или не удерживается",
        immediateActions = listOf("Зафиксировать индикацию"),
        dangerSigns = listOf("Дым"),
        questions = emptyList(),
        probableCauses = listOf("Цепь управления"),
        checks = listOf(
            DiagnosticCheck(
                title = "Индикация",
                action = "Проверить индикацию",
                expected = "Причина определена",
                ifAbnormal = "Не повторять включение",
                level = DiagnosticActionLevel.CAB
            )
        ),
        prohibited = listOf("Не обходить блокировки"),
        stopConditions = listOf("Дуга"),
        reportFields = listOf("Индикация"),
        relatedEquipment = listOf("ГВ"),
        sourceNote = "Тест",
        severity = DiagnosticSeverity.ATTENTION,
        observableSigns = listOf("ГВ не включается")
    )

    private fun ermakDiagnostic(): ErmakDiagnosticScenario {
        val node = ErmakDiagnosticNode(
            id = "start",
            type = "question",
            text = "Есть команда включения?",
            choices = emptyList(),
            nextNodeId = null,
            terminalStatus = null
        )
        return ErmakDiagnosticScenario(
            id = "ER-DIAG-GV",
            title = "ГВ не включается",
            symptom = "Главный выключатель не принимает команду",
            severity = "ATTENTION",
            category = "Высоковольтные цепи",
            equipmentIds = setOf("ER-EQ-GV"),
            startNodeId = "start",
            nodes = mapOf("start" to node),
            reportFields = listOf("Индикация"),
            immediateActions = listOf("Зафиксировать индикацию"),
            dangerSigns = listOf("Дым"),
            probableCauses = listOf("Блокировка"),
            safeChecks = listOf("Проверить разрешающие условия"),
            prohibited = listOf("Не шунтировать блокировки")
        )
    }
}
