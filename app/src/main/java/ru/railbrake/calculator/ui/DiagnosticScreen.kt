package ru.railbrake.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.DiagnosticActionLevel
import ru.railbrake.calculator.core.DiagnosticCheck
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.DiagnosticResponse
import ru.railbrake.calculator.core.DiagnosticScenario
import ru.railbrake.calculator.core.DiagnosticSeverity
import ru.railbrake.calculator.core.EquipmentReference
import ru.railbrake.calculator.core.ExamQuestion
import ru.railbrake.calculator.core.ExamQuestionRepository
import ru.railbrake.calculator.core.LocomotiveProfiles
import ru.railbrake.calculator.core.Vl80sObservationCatalog
import ru.railbrake.calculator.core.Vl80sNormalValues
import ru.railbrake.calculator.data.DiagnosticSessionRecord
import ru.railbrake.calculator.data.DiagnosticSessionRepository
import ru.railbrake.calculator.data.LocomotiveProfileRepository
import ru.railbrake.calculator.data.SecretAccessRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class QuickRouteItem(
    val title: String,
    val subtitle: String,
    val scenarioId: String
)

private val quickRouteItems = listOf(
    QuickRouteItem("Токоприёмник / ГВ", "Не поднимается токоприёмник, не включается или отключается ГВ.", "gv-no-close"),
    QuickRouteItem("Тяга и ЭКГ", "Нет тяги, не набираются позиции, различается ток секций или групп.", "traction-no-assemble"),
    QuickRouteItem("Вспомогательные машины", "Не запускаются фазорасщепитель, вентиляторы или компрессор.", "aux-machines"),
    QuickRouteItem("Тормоза и давление", "Падает ТМ, не отпускает тормоз, не набирается давление ГР.", "brake-pipe-leak"),
    QuickRouteItem("Безопасность движения", "АЛСН/ЭПК, внезапное торможение, срабатывание контроля бдительности.", "alsn-epk"),
    QuickRouteItem("Нагрев, дым, запах", "Признаки пожара, пробоя или опасного нагрева оборудования.", "smoke-fire-flashover")
)

@Composable
fun DiagnosticScreen() {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEquipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = DiagnosticRepository.scenarios.firstOrNull { it.id == selectedId }
    val selectedEquipment = Vl80sObservationCatalog.equipment(selectedEquipmentId.orEmpty())

    when {
        selectedEquipment != null -> EquipmentDetails(
            equipment = selectedEquipment,
            onBack = { selectedEquipmentId = null },
            onOpenScenario = { selectedId = it }
        )
        selected == null -> DiagnosticCatalog(
            onOpen = { selectedId = it.id },
            onOpenEquipment = { selectedEquipmentId = it.id }
        )
        else -> DiagnosticDetails(
            scenario = selected,
            onBack = { selectedId = null },
            onOpenRelated = { selectedId = it },
            onOpenEquipment = { selectedEquipmentId = it }
        )
    }
}

@Composable
private fun DiagnosticCatalog(
    onOpen: (DiagnosticScenario) -> Unit,
    onOpenEquipment: (EquipmentReference) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Все") }
    var catalogMode by rememberSaveable { mutableStateOf("scenarios") }
    val context = LocalContext.current
    val profileRepository = remember { LocomotiveProfileRepository(context) }
    val sessionRepository = remember { DiagnosticSessionRepository(context) }
    var selectedVariantId by rememberSaveable { mutableStateOf(profileRepository.selectedVariantId()) }
    var historyVersion by remember { mutableStateOf(0) }
    val sessions = remember(historyVersion) { sessionRepository.load() }
    var trainingScenarioId by rememberSaveable { mutableStateOf("gv-no-close") }
    val trainingScenario = DiagnosticRepository.scenario(trainingScenarioId)
    var trainingAnswer by rememberSaveable(trainingScenarioId) { mutableStateOf<DiagnosticResponse?>(null) }
    val results = remember(query, category) { DiagnosticRepository.search(query, category) }
    val observationResults = remember(query) { Vl80sObservationCatalog.search(query) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Диагностика ВЛ80С", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "${DiagnosticRepository.scenarios.size} сценариев: поиск причины, безопасная проверка и подготовка доклада",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { SafetyNotice() }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                FilterChip(
                    selected = catalogMode == "scenarios",
                    onClick = { catalogMode = "scenarios" },
                    label = { Text("Неисправность") }
                )
                }
                item {
                FilterChip(
                    selected = catalogMode == "observations",
                    onClick = { catalogMode = "observations" },
                    label = { Text("Что я вижу?") }
                )
                }
                item {
                FilterChip(
                    selected = catalogMode == "quick",
                    onClick = { catalogMode = "quick" },
                    label = { Text("В пути") }
                )
                }
                item { FilterChip(catalogMode == "training", { catalogMode = "training" }, label = { Text("Тренажёр") }) }
                item { FilterChip(catalogMode == "profile", { catalogMode = "profile" }, label = { Text("Исполнение") }) }
                item { FilterChip(catalogMode == "history", { catalogMode = "history" }, label = { Text("Журнал") }) }
            }
        }
        if (catalogMode == "scenarios" || catalogMode == "observations") item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(if (catalogMode == "scenarios") "Симптом или аппарат" else "Лампа, прибор, звук или аппарат") },
                placeholder = { Text(if (catalogMode == "scenarios") "Например: ЭКГ, №395, БУРТ, АЛСН" else "Например: выбило ГВ, ТМ падает, стук, боксование") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (catalogMode == "scenarios") item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DiagnosticRepository.categories) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item) }
                    )
                }
            }
        }
        if (catalogMode == "profile") {
            item {
                InfoCard(
                    "Профиль ВЛ80С",
                    listOf(LocomotiveProfiles.vl80s.description, LocomotiveProfiles.vl80s.sourcePolicy),
                    MaterialTheme.colorScheme.secondaryContainer
                )
            }
            items(LocomotiveProfiles.vl80s.variants, key = { it.id }) { variant ->
                Card(
                    onClick = {
                        selectedVariantId = variant.id
                        profileRepository.select(LocomotiveProfiles.VL80S_ID, variant.id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = if (selectedVariantId == variant.id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(variant.title, fontWeight = FontWeight.Black)
                        Text(variant.applicability)
                        Text("${variant.confidence.title}: ${variant.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (catalogMode == "history") {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Локальный журнал", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    if (sessions.isNotEmpty()) TextButton(onClick = { sessionRepository.clear(); historyVersion++ }) { Text("Очистить") }
                }
            }
            if (sessions.isEmpty()) item { InfoCard("Пока пусто", listOf("Сохранённые результаты диагностики появятся здесь и останутся на устройстве."), MaterialTheme.colorScheme.surfaceVariant) }
            items(sessions, key = { it.timestampMillis }) { session ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(session.scenarioTitle, fontWeight = FontWeight.Black)
                        Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(session.timestampMillis)), color = MaterialTheme.colorScheme.primary)
                        Text(session.severity)
                        Text(session.report, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (catalogMode == "training") {
            item {
                InfoCard(
                    "Учебный симулятор",
                    listOf("Ситуации используют те же безопасные деревья, что и рабочий режим. Ответ не является разрешением на вмешательство."),
                    MaterialTheme.colorScheme.tertiaryContainer
                )
            }
            trainingScenario?.let { scenario ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ситуация", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(scenario.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text(scenario.summary)
                            Text(scenario.questions.first().text, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { trainingAnswer = DiagnosticResponse.YES }) { Text("Да") }
                                OutlinedButton(onClick = { trainingAnswer = DiagnosticResponse.NO }) { Text("Нет") }
                                TextButton(onClick = { trainingAnswer = DiagnosticResponse.UNKNOWN }) { Text("Не знаю") }
                            }
                            trainingAnswer?.let { answer ->
                                Text(DiagnosticRepository.meaning(scenario.questions.first(), answer), color = MaterialTheme.colorScheme.primary)
                                Button(onClick = {
                                    val all = DiagnosticRepository.scenarios
                                    val index = all.indexOfFirst { it.id == scenario.id }.coerceAtLeast(0)
                                    trainingScenarioId = all[(index + 1) % all.size].id
                                }) { Text("Следующая ситуация") }
                            }
                        }
                    }
                }
            }
        } else if (catalogMode == "quick") {
            item {
                BorderedCautionCard(
                    "Быстрая оценка",
                    listOf(
                        "Выберите наблюдаемое явление. Здесь только безопасное первичное направление; оно не заменяет действующие инструкции.",
                        "При дыме, огне, дуге, повреждении контактной сети, опасном нагреве или неясной высоковольтной защите прекратите диагностические действия и доложите."
                    )
                )
            }
            items(quickRouteItems, key = { it.scenarioId }) { item ->
                val scenario = DiagnosticRepository.scenario(item.scenarioId)
                Card(
                    onClick = { scenario?.let(onOpen) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Открыть безопасный маршрут →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (catalogMode == "observations") {
            if (observationResults.first.isEmpty() && observationResults.second.isEmpty()) {
                item {
                    InfoCard(
                        title = "Ничего не найдено",
                        lines = listOf("Попробуйте разговорную формулировку: «главник», «ТМ падает», «шипит», «бокс» или «дым"),
                        tone = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            items(observationResults.first, key = { "observation-${it.id}" }) { observation ->
                val scenario = observation.scenarioIds.firstNotNullOfOrNull(DiagnosticRepository::scenario)
                Card(
                    onClick = { scenario?.let(onOpen) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(observation.kind.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(observation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(observation.description)
                        Text("Связано: ${observation.equipmentIds.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        Text("Открыть безопасный алгоритм →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(observationResults.second, key = { "equipment-${it.id}" }) { equipment ->
                Card(
                    onClick = { onOpenEquipment(equipment) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Аппарат", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(equipment.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(equipment.purpose)
                        Text("Где находится: ${equipment.location}", style = MaterialTheme.typography.bodySmall)
                        Text("Открыть аппарат →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (results.isEmpty()) {
            item {
                InfoCard(
                    title = "Ничего не найдено",
                    lines = listOf("Попробуйте название аппарата, общий симптом или выберите категорию «Все»."),
                    tone = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        } else {
            items(results, key = { it.id }) { scenario ->
                Card(
                    onClick = { onOpen(scenario) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(scenario.category, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(scenario.severity.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(scenario.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(scenario.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Связано: ${scenario.relatedEquipment.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        Text("Открыть алгоритм →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun DiagnosticDetails(
    scenario: DiagnosticScenario,
    onBack: () -> Unit,
    onOpenRelated: (String) -> Unit,
    onOpenEquipment: (String) -> Unit
) {
    var currentQuestionKey by rememberSaveable(scenario.id) {
        mutableStateOf(scenario.questions.firstOrNull()?.key)
    }
    var answers by rememberSaveable(scenario.id) { mutableStateOf(emptyList<String>()) }
    var sectionNote by rememberSaveable(scenario.id) { mutableStateOf("") }
    var modeNote by rememberSaveable(scenario.id) { mutableStateOf("") }
    var instrumentNote by rememberSaveable(scenario.id) { mutableStateOf("") }
    var feedbackNote by rememberSaveable(scenario.id) { mutableStateOf("") }
    var candidateScores by remember(scenario.id) { mutableStateOf(emptyMap<String, Int>()) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val sessionRepository = remember { DiagnosticSessionRepository(context) }
    val examQuestionRepository = remember { ExamQuestionRepository(context) }
    val examQuestionsUnlocked = remember { SecretAccessRepository(context).isUnlocked() }
    val relatedQuestions = remember(scenario.id) {
        if (examQuestionsUnlocked) examQuestionRepository.questions.filter { scenario.id in it.diagnosticScenarioIds } else emptyList()
    }
    val profileRepository = remember { LocomotiveProfileRepository(context) }
    val profileId = profileRepository.selectedProfileId()
    val variantId = profileRepository.selectedVariantId()
    val selectedVariant = LocomotiveProfiles.variant(variantId)
    var savedLocally by rememberSaveable(scenario.id) { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Все неисправности") }
            Text(scenario.category, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            SeverityLabel(scenario.severity)
            Text(scenario.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(scenario.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Профиль: ${selectedVariant?.title ?: "ВЛ80С — проверка исполнения обязательна"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { SafetyNotice() }
        item { InfoCard("Сначала", scenario.immediateActions, MaterialTheme.colorScheme.primaryContainer) }
        if (scenario.observableSigns.isNotEmpty()) {
            item { InfoCard("Что наблюдать", scenario.observableSigns, MaterialTheme.colorScheme.surfaceVariant) }
        }
        item { InfoCard("Опасные признаки", scenario.dangerSigns, MaterialTheme.colorScheme.errorContainer) }
        item {
            TriageCard(
                scenario = scenario,
                currentQuestionKey = currentQuestionKey,
                answers = answers,
                onAnswer = { response ->
                    val question = scenario.questions.first { it.key == currentQuestionKey }
                    val meaning = DiagnosticRepository.meaning(question, response)
                    answers = answers + "${question.text} — ${response.title}. $meaning"
                    candidateScores = candidateScores.toMutableMap().also { scores ->
                        DiagnosticRepository.candidateCauseIds(question, response).forEach { causeId ->
                            scores[causeId] = (scores[causeId] ?: 0) + 1
                        }
                    }
                    currentQuestionKey = DiagnosticRepository.nextQuestion(scenario, question.key, response)?.key
                },
                onReset = {
                    answers = emptyList()
                    candidateScores = emptyMap()
                    currentQuestionKey = scenario.questions.firstOrNull()?.key
                }
            )
        }
        if (scenario.systemExplanation.isNotEmpty()) {
            item { InfoCard("Как связана система", scenario.systemExplanation, MaterialTheme.colorScheme.secondaryContainer) }
        }
        val leadingCauses = scenario.diagnosticCauses
            .filter { (candidateScores[it.id] ?: 0) > 0 }
            .sortedByDescending { candidateScores[it.id] ?: 0 }
        if (leadingCauses.isNotEmpty()) {
            item {
                InfoCard(
                    "Наиболее подходящие ветви по ответам",
                    leadingCauses.take(3).map { "${it.title}: ${it.explanation}" },
                    MaterialTheme.colorScheme.tertiaryContainer
                )
            }
        }
        item { InfoCard("Вероятные причины", scenario.probableCauses, MaterialTheme.colorScheme.surfaceVariant) }
        if (scenario.operationalConsequences.isNotEmpty()) {
            item { InfoCard("К чему может привести", scenario.operationalConsequences, MaterialTheme.colorScheme.errorContainer) }
        }
        item {
            Text("Проверки по уровню допуска", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Уровень указан для каждой проверки. Он не расширяет допуск конкретного работника.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(scenario.checks) { check -> DiagnosticCheckCard(check) }
        if (scenario.trainingNotes.isNotEmpty()) {
            item { InfoCard("Почему алгоритм спрашивает именно это", scenario.trainingNotes, MaterialTheme.colorScheme.tertiaryContainer) }
        }
        item { InfoCard("Запрещено", scenario.prohibited, MaterialTheme.colorScheme.errorContainer) }
        item { InfoCard("Прекратить диагностику", scenario.stopConditions, MaterialTheme.colorScheme.errorContainer) }
        item {
            SessionJournal(
                sectionNote = sectionNote,
                onSectionNote = { sectionNote = it },
                modeNote = modeNote,
                onModeNote = { modeNote = it },
                instrumentNote = instrumentNote,
                onInstrumentNote = { instrumentNote = it },
                feedbackNote = feedbackNote,
                onFeedbackNote = { feedbackNote = it },
                prompts = scenario.feedbackPrompts
            )
        }
        item {
            val sessionLines = listOfNotNull(
                sectionNote.takeIf { it.isNotBlank() }?.let { "Секция/место: $it" },
                modeNote.takeIf { it.isNotBlank() }?.let { "Режим: $it" },
                instrumentNote.takeIf { it.isNotBlank() }?.let { "Приборы и индикация: $it" },
                feedbackNote.takeIf { it.isNotBlank() }?.let { "Что изменилось: $it" }
            )
            val report = buildDiagnosticReport(scenario, answers, sessionLines)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Доклад", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Запишите: ${scenario.reportFields.joinToString()}")
                    if (answers.isNotEmpty()) {
                        HorizontalDivider()
                        answers.forEach { Text("• $it") }
                    }
                    sessionLines.forEach { Text("• $it") }
                    Button(onClick = { clipboard.setText(AnnotatedString(report)) }) {
                        Text("Скопировать шаблон доклада")
                    }
                    OutlinedButton(
                        onClick = {
                            sessionRepository.add(
                                DiagnosticSessionRecord(
                                    timestampMillis = System.currentTimeMillis(),
                                    profileId = profileId,
                                    variantId = variantId,
                                    scenarioId = scenario.id,
                                    scenarioTitle = scenario.title,
                                    severity = scenario.severity.title,
                                    report = report
                                )
                            )
                            savedLocally = true
                        }
                    ) { Text("Сохранить в локальную историю") }
                    if (savedLocally) Text("Сессия сохранена на устройстве.", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (scenario.relatedScenarioIds.isNotEmpty()) {
            item {
                RelatedScenarios(scenario.relatedScenarioIds, onOpenRelated)
            }
        }
        val linkedEquipment = Vl80sObservationCatalog.equipment.filter { equipment ->
            equipment.scenarioIds.contains(scenario.id)
        }
        if (linkedEquipment.isNotEmpty()) {
            item { RelatedEquipment(linkedEquipment, onOpenEquipment) }
        }
        if (relatedQuestions.isNotEmpty()) {
            item { RelatedExamQuestions(relatedQuestions) }
        }
        item {
            InfoCard(
                "Применимость и источник",
                listOf(
                    "Уровень доверия: ${scenario.informationConfidence.title}.",
                    scenario.applicability,
                    scenario.sourceNote
                ),
                MaterialTheme.colorScheme.surfaceVariant
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun EquipmentDetails(
    equipment: EquipmentReference,
    onBack: () -> Unit,
    onOpenScenario: (String) -> Unit
) {
    val context = LocalContext.current
    val examQuestionRepository = remember { ExamQuestionRepository(context) }
    val examQuestionsUnlocked = remember { SecretAccessRepository(context).isUnlocked() }
    val relatedScenarios = equipment.scenarioIds.mapNotNull(DiagnosticRepository::scenario)
    val relatedQuestions = remember(equipment.id) {
        if (examQuestionsUnlocked) examQuestionRepository.questions.filter { equipment.id in it.equipmentIds } else emptyList()
    }
    val observations = Vl80sObservationCatalog.observations.filter { equipment.id in it.equipmentIds }
    val normalValues = Vl80sNormalValues.forEquipment(equipment.id)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Диагностика") }
            Text("Аппарат", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(equipment.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(equipment.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SafetyNotice() }
        item {
            InfoCard(
                "Назначение и связи",
                listOf("Связано с: ${equipment.connections.joinToString()}"),
                MaterialTheme.colorScheme.secondaryContainer
            )
        }
        item {
            InfoCard(
                "Где искать",
                listOf(equipment.location, equipment.variantNote),
                MaterialTheme.colorScheme.surfaceVariant
            )
        }
        if (observations.isNotEmpty()) {
            item {
                InfoCard(
                    "Что может быть видно бригаде",
                    observations.map { "${it.kind.title}: ${it.title}" },
                    MaterialTheme.colorScheme.tertiaryContainer
                )
            }
        }
        if (normalValues.isNotEmpty()) {
            item {
                InfoCard(
                    "Опорные параметры",
                    normalValues.flatMap { value ->
                        listOf(
                            "${value.title}: ${value.normalValue}",
                            "Применимость: ${value.applicability}",
                            "Источник: ${value.source}"
                        )
                    },
                    MaterialTheme.colorScheme.secondaryContainer
                )
            }
        }
        item {
            Text("Связанная диагностика", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Переход открывает безопасный маршрут, а не инструкцию по ремонту.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(relatedScenarios, key = { it.id }) { scenario ->
            Card(
                onClick = { onOpenScenario(scenario.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(scenario.severity.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(scenario.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(scenario.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (relatedQuestions.isNotEmpty()) {
            item { RelatedExamQuestions(relatedQuestions) }
        }
        item {
            InfoCard(
                "Применимость",
                listOf("Уровень доверия: ${equipment.confidence.title}.", equipment.variantNote),
                MaterialTheme.colorScheme.surfaceVariant
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun TriageCard(
    scenario: DiagnosticScenario,
    currentQuestionKey: String?,
    answers: List<String>,
    onAnswer: (DiagnosticResponse) -> Unit,
    onReset: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Уточнение симптома", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            val question = scenario.questions.firstOrNull { it.key == currentQuestionKey }
            if (question != null) {
                Text("Шаг ${answers.size + 1}; дальнейший вопрос зависит от ответа")
                Text(question.text, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onAnswer(DiagnosticResponse.YES) }) { Text("Да") }
                    OutlinedButton(onClick = { onAnswer(DiagnosticResponse.NO) }) { Text("Нет") }
                }
                TextButton(onClick = { onAnswer(DiagnosticResponse.UNKNOWN) }) { Text("Не знаю — записать и идти дальше") }
            } else {
                Text("Вопросы пройдены. Выводы включены в шаблон доклада.", fontWeight = FontWeight.Bold)
            }
            answers.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            if (answers.isNotEmpty()) TextButton(onClick = onReset) { Text("Начать заново") }
        }
    }
}

@Composable
private fun SeverityLabel(severity: DiagnosticSeverity) {
    val color = when (severity) {
        DiagnosticSeverity.INFORMATION -> MaterialTheme.colorScheme.primary
        DiagnosticSeverity.ATTENTION -> Color(0xFF9A6700)
        DiagnosticSeverity.RESTRICT_OPERATION -> Color(0xFFC55200)
        DiagnosticSeverity.STOP_AND_REPORT -> MaterialTheme.colorScheme.error
    }
    Text(severity.title.uppercase(), color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun SessionJournal(
    sectionNote: String,
    onSectionNote: (String) -> Unit,
    modeNote: String,
    onModeNote: (String) -> Unit,
    instrumentNote: String,
    onInstrumentNote: (String) -> Unit,
    feedbackNote: String,
    onFeedbackNote: (String) -> Unit,
    prompts: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Журнал диагностической сессии", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Записи сохраняются при переходах внутри приложения и попадут в шаблон доклада.")
            OutlinedTextField(sectionNote, onSectionNote, label = { Text("Секция, тележка или место") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(modeNote, onModeNote, label = { Text("Скорость, позиция и режим") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(instrumentNote, onInstrumentNote, label = { Text("Приборы, лампы и защита") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(feedbackNote, onFeedbackNote, label = { Text("Что изменилось после действия") }, modifier = Modifier.fillMaxWidth())
            prompts.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun RelatedScenarios(ids: List<String>, onOpen: (String) -> Unit) {
    val related = ids.mapNotNull(DiagnosticRepository::scenario)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Связанные неисправности", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Один симптом может относиться сразу к нескольким системам.")
            related.forEach { item ->
                TextButton(onClick = { onOpen(item.id) }) { Text("${item.title} →") }
            }
        }
    }
}

@Composable
private fun RelatedEquipment(items: List<EquipmentReference>, onOpen: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Связанные аппараты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Назначение и расположение отмечены с учётом варианта исполнения.")
            items.forEach { equipment ->
                TextButton(onClick = { onOpen(equipment.id) }) { Text("${equipment.title} →") }
            }
        }
    }
}

@Composable
private fun RelatedExamQuestions(items: List<ExamQuestion>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Связанные проверочные сведения", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Формулировки ниже взяты из экзаменационной базы и не заменяют эксплуатационный документ.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            items.take(8).forEach { item ->
                Text("• ${item.question}", fontWeight = FontWeight.SemiBold)
                Text(item.correctAnswer, style = MaterialTheme.typography.bodySmall)
            }
            if (items.size > 8) {
                Text("Ещё связано: ${items.size - 8}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DiagnosticCheckCard(check: DiagnosticCheck) {
    val (label, color) = when (check.level) {
        DiagnosticActionLevel.CAB -> "ИЗ КАБИНЫ" to Color(0xFF2E7D32)
        DiagnosticActionLevel.SAFE_STOP -> "ПОСЛЕ БЕЗОПАСНОЙ ОСТАНОВКИ" to Color(0xFF9A6700)
        DiagnosticActionLevel.AUTHORIZED_ONLY -> "ТОЛЬКО ДОПУЩЕННЫЙ ПЕРСОНАЛ" to Color(0xFFB3261E)
        DiagnosticActionLevel.STOP -> "ПРЕКРАТИТЬ ДЕЙСТВИЯ" to Color(0xFFB3261E)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, color),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
            Text(check.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Действие: ${check.action}")
            Text("Норма: ${check.expected}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Если не так: ${check.ifAbnormal}", color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SafetyNotice() {
    BorderedCautionCard(
        title = "Важно: это не допуск к работам",
        lines = listOf(DiagnosticRepository.safetyNotice)
    )
}

@Composable
private fun BorderedCautionCard(title: String, lines: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            lines.forEach { Text("• $it") }
        }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>, tone: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = tone),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            lines.forEach { Text("• $it") }
        }
    }
}

internal fun buildDiagnosticReport(
    scenario: DiagnosticScenario,
    answers: List<String>,
    sessionLines: List<String> = emptyList()
): String = buildString {
    appendLine("ВЛ80С — ${scenario.title}")
    appendLine("Уровень: ${scenario.severity.title}.")
    appendLine("Зафиксировать: ${scenario.reportFields.joinToString()}.")
    if (answers.isNotEmpty()) {
        appendLine("Ответы:")
        answers.forEach { appendLine("- $it") }
    }
    if (sessionLines.isNotEmpty()) {
        appendLine("Наблюдения:")
        sessionLines.forEach { appendLine("- $it") }
    }
    appendLine("Применимость: ${scenario.applicability}")
}
