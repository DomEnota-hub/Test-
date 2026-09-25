package ru.railbrake.calculator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.railbrake.calculator.core.DiagnosticPolicyEngine
import ru.railbrake.calculator.core.DiagnosticProfileContext
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.ErmakDiagnosticRepository
import ru.railbrake.calculator.core.ErmakDiagnosticScenario
import ru.railbrake.calculator.core.requiresPolicyEvaluation
import ru.railbrake.calculator.data.DiagnosticSessionRecord
import ru.railbrake.calculator.data.DiagnosticSessionRepository
import ru.railbrake.calculator.data.LocomotiveProfileRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ermakDiagnosticCategories = listOf(
    "Все",
    "Высоковольтные цепи",
    "Тяга",
    "Вспомогательные машины",
    "Тормоза",
    "Пневматика",
    "Ходовая часть",
    "Управление и защита",
    "Прочее"
)

private fun ermakScenarioText(scenario: ErmakDiagnosticScenario): String = listOf(
    scenario.category,
    scenario.title,
    scenario.symptom,
    scenario.immediateActions.joinToString(" "),
    scenario.probableCauses.joinToString(" "),
    scenario.reportFields.joinToString(" ")
).joinToString(" ").lowercase().replace('ё', 'е')

private fun String.containsAny(vararg needles: String): Boolean = needles.any(::contains)

private fun ermakCategory(scenario: ErmakDiagnosticScenario): String {
    val text = ermakScenarioText(scenario)
    return when {
        text.containsAny(
            "токоприем", "главный выключател", "высоковольт", "ввк", "трансформатор",
            "перенапряж", "изоляц", "крышев", "силовая цеп"
        ) -> "Высоковольтные цепи"
        text.containsAny(
            "тяга", "тягов", "тэд", "экг", "позици", "боксован", "юз"
        ) -> "Тяга"
        text.containsAny(
            "вспомогатель", "вентилятор", "фазорасщеп", "компрессор", "масляный насос", "маслонасос"
        ) -> "Вспомогательные машины"
        text.containsAny(
            "тормоз", "квт", "кран машиниста", "тормозных цилиндр", "тормозного цилиндр", "эпт"
        ) -> "Тормоза"
        text.containsAny(
            "пневм", "давлен", "тормозная магистрал", "питательная магистрал", "главных резервуар",
            "утечк воздуха", "воздухораспредел"
        ) -> "Пневматика"
        text.containsAny(
            "ходов", "тележ", "колес", "букс", "рессор", "редуктор", "подвес", "стук", "вибрац"
        ) -> "Ходовая часть"
        text.containsAny(
            "защит", "блокиров", "сигнализац", "управлен", "контроллер", "бортовой"
        ) -> "Управление и защита"
        else -> "Прочее"
    }
}

private fun ermakMatchesQuery(scenario: ErmakDiagnosticScenario, query: String): Boolean =
    query.isBlank() || ermakScenarioText(scenario).contains(query.trim().lowercase().replace('ё', 'е'))

private fun ermakQuickCandidate(scenario: ErmakDiagnosticScenario): Boolean {
    val severity = scenario.severity.trim().uppercase()
    val text = ermakScenarioText(scenario)
    return severity in setOf("ATTENTION", "WARNING", "RESTRICT_OPERATION", "RESTRICT", "STOP_AND_REPORT", "STOP") ||
        text.containsAny(
            "не включ", "не запуска", "отключ", "нет тяги", "тормоз", "давлен", "дым", "огонь",
            "нагрев", "стук", "вибрац", "защит", "токоприем"
        )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErmakDiagnosticsScreen(initialScenarioId: String? = null, initialEquipmentId: String? = null) {
    val context = LocalContext.current
    val repository = remember { ErmakDiagnosticRepository(context.applicationContext) }
    val scenarios by produceState<List<ErmakDiagnosticScenario>?>(null) {
        value = withContext(Dispatchers.IO) { repository.scenarios() }
    }
    var selectedId by rememberSaveable(initialScenarioId, initialEquipmentId) { mutableStateOf(initialScenarioId) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Все") }
    var catalogMode by rememberSaveable { mutableStateOf("scenarios") }
    var historyVersion by remember { mutableIntStateOf(0) }
    val sessionRepository = remember { DiagnosticSessionRepository(context) }
    val sessions = remember(historyVersion) {
        sessionRepository.loadForProfile(DiagnosticSessionRepository.PROFILE_ERMAK)
    }
    val selected = scenarios?.firstOrNull { it.id == selectedId }

    BackHandler(enabled = selected != null) { selectedId = null }
    if (selected != null) {
        ErmakDiagnosticRoute(selected, onBack = { selectedId = null }, onSaved = { historyVersion++ })
        return
    }

    val baseScenarios = scenarios.orEmpty().filter { scenario ->
        (initialEquipmentId == null || initialEquipmentId in scenario.equipmentIds) && ermakMatchesQuery(scenario, query)
    }
    val visible = when (catalogMode) {
        "scenarios" -> baseScenarios.filter { category == "Все" || ermakCategory(it) == category }
        "observations" -> baseScenarios.sortedBy { it.symptom.lowercase() }
        "quick" -> baseScenarios.filter(::ermakQuickCandidate).sortedBy { ermakCategory(it) + it.title }
        else -> emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            RailSectionHeader("Диагностика Ермак", "Выберите неисправность или наблюдаемый симптом")
            DiagnosticSafetyNotice()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(catalogMode == "scenarios", { catalogMode = "scenarios" }, label = { Text("Неисправность") })
                FilterChip(catalogMode == "observations", { catalogMode = "observations" }, label = { Text("Что я вижу?") })
                FilterChip(catalogMode == "quick", { catalogMode = "quick" }, label = { Text("В пути") })
                FilterChip(catalogMode == "history", { catalogMode = "history" }, label = { Text("Журнал") })
            }
            if (catalogMode != "history") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            when (catalogMode) {
                                "observations" -> "Лампа, прибор, звук или симптом"
                                "quick" -> "Поиск во вкладке «В пути»"
                                else -> "Симптом или аппарат"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        if (catalogMode == "scenarios") {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ermakDiagnosticCategories) { item ->
                            FilterChip(
                                selected = category == item,
                                onClick = { category = item },
                                label = { Text(item) }
                            )
                        }
                    }
                    Text("→", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (catalogMode == "history") {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Локальный журнал Ермака", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    if (sessions.isNotEmpty()) {
                        TextButton(onClick = {
                            sessionRepository.clearProfile(DiagnosticSessionRepository.PROFILE_ERMAK)
                            historyVersion++
                        }) { Text("Очистить") }
                    }
                }
            }
            if (sessions.isEmpty()) {
                item {
                    InfoCard(
                        "Пока пусто",
                        listOf("Сохранённые результаты диагностики Ермака появятся здесь и останутся на устройстве."),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            items(sessions, key = { it.timestampMillis }) { session ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(session.scenarioTitle, fontWeight = FontWeight.Black)
                        Text(
                            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(session.timestampMillis)),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(session.severity)
                        Text(session.report, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (scenarios == null) {
            item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (catalogMode == "quick") {
            item {
                InfoCard(
                    "Быстрая оценка",
                    listOf(
                        "Здесь собраны сценарии, которые полезно быстро открыть в пути. Это только безопасное первичное направление поиска.",
                        "При дыме, огне, дуге, повреждении токоведущих частей или неясном срабатывании защиты прекратите диагностические действия и доложите."
                    ),
                    MaterialTheme.colorScheme.surfaceVariant
                )
            }
            if (visible.isEmpty()) {
                item { InfoCard("Ничего не найдено", listOf("Измените запрос или очистите строку поиска."), MaterialTheme.colorScheme.surfaceVariant) }
            }
            items(visible, key = { "quick-${it.id}" }) { scenario ->
                Card(
                    onClick = { selectedId = scenario.id },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(ermakCategory(scenario), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(ermakSeverityTitle(scenario.severity), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(scenario.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(scenario.symptom, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Открыть безопасный маршрут →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (catalogMode == "observations") {
            if (visible.isEmpty()) {
                item { InfoCard("Ничего не найдено", listOf("Попробуйте описать то, что видно или слышно: лампу, показание, звук, запах или поведение аппарата."), MaterialTheme.colorScheme.surfaceVariant) }
            }
            items(visible, key = { "observation-${it.id}" }) { scenario ->
                Card(
                    onClick = { selectedId = scenario.id },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(ermakCategory(scenario), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Наблюдаемый признак", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(scenario.symptom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(scenario.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Открыть безопасный алгоритм →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (visible.isEmpty()) {
            item { InfoCard("Сценарии не найдены", listOf("Измените запрос, выберите категорию «Все» или вернитесь к общему списку."), MaterialTheme.colorScheme.surfaceVariant) }
        } else {
            items(visible, key = { it.id }) { scenario ->
                Card(
                    onClick = { selectedId = scenario.id },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(ermakCategory(scenario), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(ermakSeverityTitle(scenario.severity), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(scenario.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(scenario.symptom, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Открыть алгоритм →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun ermakSeverityTitle(value: String): String = when (value.trim().uppercase()) {
    "INFORMATION", "INFO" -> "Информация"
    "ATTENTION", "WARNING" -> "Внимание"
    "RESTRICT_OPERATION", "RESTRICT" -> "Ограничить эксплуатацию"
    "STOP_AND_REPORT", "STOP" -> "Остановиться и доложить"
    else -> value.replace('_', ' ').trim().ifBlank { "Диагностическая запись" }
}

private fun buildErmakDiagnosticReport(
    scenario: ErmakDiagnosticScenario,
    terminalText: String?,
    uncertain: Boolean,
    history: List<String>,
    observations: String,
    report: String
): String = buildString {
    appendLine("Ермак — ${scenario.title}")
    appendLine("Симптом: ${scenario.symptom}")
    appendLine("Результат: ${if (uncertain) "Недостаточно данных" else terminalText.orEmpty().ifBlank { "Маршрут завершён" }}")
    if (history.isNotEmpty()) {
        appendLine("Пройденная ветка:")
        history.forEach { appendLine("• $it") }
    }
    if (observations.isNotBlank()) appendLine("Наблюдения: ${observations.trim()}")
    if (report.isNotBlank()) appendLine("Доклад / заметки: ${report.trim()}")
    if (scenario.reportFields.isNotEmpty()) {
        appendLine("Что зафиксировать: ${scenario.reportFields.joinToString()}")
    }
}.trim()

@Composable
private fun ErmakDiagnosticRoute(scenario: ErmakDiagnosticScenario, onBack: () -> Unit, onSaved: () -> Unit) {
    var nodeId by rememberSaveable(scenario.id) { mutableStateOf(scenario.startNodeId) }
    var history by rememberSaveable(scenario.id) { mutableStateOf(emptyList<String>()) }
    var observations by rememberSaveable(scenario.id) { mutableStateOf("") }
    var report by rememberSaveable(scenario.id) { mutableStateOf("") }
    var uncertain by rememberSaveable(scenario.id) { mutableStateOf(false) }
    var questionNumber by rememberSaveable(scenario.id) { mutableIntStateOf(1) }
    var savedLocally by rememberSaveable(scenario.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val sessionRepository = remember { DiagnosticSessionRepository(context) }
    val profileRepository = remember { LocomotiveProfileRepository(context.applicationContext) }
    var profileContext by remember(scenario.id) { mutableStateOf(profileRepository.diagnosticContext()) }
    val node = scenario.nodes[nodeId]
    val policyDecision = node?.takeIf { it.requiresPolicyEvaluation() }?.let {
        DiagnosticPolicyEngine.evaluate(
            applicability = scenario.applicability,
            action = it.actionMetadata,
            context = profileContext.toPolicyContext(scenario.applicability)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ChildBackButton("Диагностика Ермак", onBack)
            Text(scenario.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(scenario.symptom, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            InfoCard("Сначала", scenario.immediateActions.ifEmpty { listOf("Зафиксируйте наблюдаемые признаки до дальнейшей проверки.") }, MaterialTheme.colorScheme.tertiaryContainer)
        }
        item {
            ErmakProfileContextCard(
                profile = profileContext,
                applicability = scenario.applicability,
                onChange = { updated ->
                    profileContext = updated
                    profileRepository.saveDiagnosticContext(updated)
                },
                onConfirm = {
                    if (profileContext.canConfirm()) {
                        val confirmed = profileContext.copy(confirmed = true)
                        profileContext = confirmed
                        profileRepository.saveDiagnosticContext(confirmed)
                    }
                },
                onReset = {
                    profileRepository.clearDiagnosticContext()
                    profileContext = DiagnosticProfileContext()
                }
            )
        }
        if (scenario.applicability.variantSelectionRequired && !profileContext.toPolicyContext(scenario.applicability).profileConfirmed) item {
            InfoCard(
                "Требуется подтверждение исполнения",
                listOf("Сценарий зависит от профиля оборудования. Профильные и опасные действия скрыты до явного подтверждения совместимого исполнения."),
                MaterialTheme.colorScheme.primaryContainer
            )
        }
        if (scenario.dangerSigns.isNotEmpty()) item {
            InfoCard("Опасные признаки", scenario.dangerSigns, MaterialTheme.colorScheme.errorContainer)
        }
        if (uncertain) {
            item {
                InfoCard(
                    "Недостаточно данных",
                    listOf(
                        "Причина не подтверждена. Не выполняйте действия, основанные на предположении.",
                        "Зафиксируйте доступные показания и доложите установленным порядком."
                    ),
                    MaterialTheme.colorScheme.primaryContainer
                )
            }
        } else if (node == null) {
            item { InfoCard("Ошибка сценария", listOf("Узел маршрута не найден. Вернитесь к выбору неисправности."), MaterialTheme.colorScheme.errorContainer) }
        } else {
            item {
                if (node.type == "question") {
                    val uncertaintyChoice = node.choices.firstOrNull { choice ->
                        choice.label.contains("не уверен", true) ||
                            choice.label.contains("не знаю", true) ||
                            choice.label.contains("недостаточно", true)
                    }
                    val answerChoices = node.choices.filterNot { it == uncertaintyChoice }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f)),
                        shape = RoundedCornerShape(17.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Уточнение симптома", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text("Шаг $questionNumber; дальнейший вопрос зависит от ответа")
                            Text(node.text, fontWeight = FontWeight.Bold)
                            if (answerChoices.isNotEmpty()) {
                                val compactBinaryChoices = answerChoices.size == 2 &&
                                    answerChoices.all { it.label.length <= 14 }
                                if (compactBinaryChoices) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        answerChoices.getOrNull(0)?.let { choice ->
                                            Button(onClick = {
                                                history = history + "${node.text} — ${choice.label}"
                                                questionNumber += 1
                                                nodeId = choice.nextNodeId
                                            }) { Text(choice.label) }
                                        }
                                        answerChoices.getOrNull(1)?.let { choice ->
                                            OutlinedButton(onClick = {
                                                history = history + "${node.text} — ${choice.label}"
                                                questionNumber += 1
                                                nodeId = choice.nextNodeId
                                            }) { Text(choice.label) }
                                        }
                                    }
                                } else {
                                    answerChoices.forEachIndexed { index, choice ->
                                        if (index == 0) {
                                            Button(
                                                onClick = {
                                                    history = history + "${node.text} — ${choice.label}"
                                                    questionNumber += 1
                                                    nodeId = choice.nextNodeId
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text(choice.label) }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    history = history + "${node.text} — ${choice.label}"
                                                    questionNumber += 1
                                                    nodeId = choice.nextNodeId
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text(choice.label) }
                                        }
                                    }
                                }
                            }
                            TextButton(
                                onClick = {
                                    history = history + "${node.text} — ${uncertaintyChoice?.label ?: "Не уверен"}"
                                    if (uncertaintyChoice != null) {
                                        questionNumber += 1
                                        nodeId = uncertaintyChoice.nextNodeId
                                    } else {
                                        uncertain = true
                                    }
                                }
                            ) { Text("Не уверен — записать и завершить") }
                            history.filter { " — " in it }.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (policyDecision?.allowed == false) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Действие скрыто", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text(policyDecision.message.ifBlank { "Действие требует дополнительного подтверждения безопасности." })
                            Text("Текст заблокированного действия не показывается и не считается выполненным.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = {
                                    history = history + "Действие заблокировано политикой безопасности"
                                    uncertain = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Записать и завершить") }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (node.type == "terminal") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                when (node.type) {
                                    "finding" -> "Выявленный признак"
                                    "source_action", "emergency_action" -> "Подтверждённое действие"
                                    "terminal" -> "Результат"
                                    else -> "Шаг диагностики"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(node.text)
                            node.choices.forEach { choice ->
                                Button(
                                    onClick = {
                                        history = history + "${node.text} — ${choice.label}"
                                        nodeId = choice.nextNodeId
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(choice.label) }
                            }
                            if (node.choices.isEmpty() && node.nextNodeId != null) {
                                Button(
                                    onClick = {
                                        if (node.text.isNotBlank()) history = history + node.text
                                        nodeId = node.nextNodeId
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Продолжить") }
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = observations,
                onValueChange = { observations = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Фактические наблюдения и значения") },
                minLines = 3
            )
            OutlinedTextField(
                value = report,
                onValueChange = { report = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст доклада / заметки") },
                minLines = 3
            )
        }
        if (node?.type == "terminal" || uncertain) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Пройденная ветка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        history.forEach { Text("• $it") }
                        if (observations.isNotBlank()) Text("Наблюдения: $observations")
                        if (report.isNotBlank()) Text("Доклад: $report")
                        scenario.reportFields.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (scenario.probableCauses.isNotEmpty()) {
                    InfoCard("Возможные причины", scenario.probableCauses, MaterialTheme.colorScheme.secondaryContainer)
                }
                if (scenario.safeChecks.isNotEmpty()) {
                    InfoCard("Безопасные проверки", scenario.safeChecks, MaterialTheme.colorScheme.tertiaryContainer)
                }
                if (scenario.prohibited.isNotEmpty()) {
                    InfoCard("Запрещено", scenario.prohibited, MaterialTheme.colorScheme.errorContainer)
                }
                val savedReport = buildErmakDiagnosticReport(
                    scenario = scenario,
                    terminalText = node?.text,
                    uncertain = uncertain,
                    history = history,
                    observations = observations,
                    report = report
                )
                OutlinedButton(
                    onClick = {
                        sessionRepository.add(
                            DiagnosticSessionRecord(
                                timestampMillis = System.currentTimeMillis(),
                                profileId = DiagnosticSessionRepository.PROFILE_ERMAK,
                                variantId = DiagnosticSessionRepository.VARIANT_ERMAK_GENERAL,
                                scenarioId = scenario.id,
                                scenarioTitle = scenario.title,
                                severity = ermakSeverityTitle(scenario.severity),
                                report = savedReport
                            )
                        )
                        savedLocally = true
                        onSaved()
                    },
                    enabled = !savedLocally,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (savedLocally) "Сохранено в журнал" else "Сохранить в локальную историю") }
                if (savedLocally) {
                    Text("Сессия сохранена на устройстве.", color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        nodeId = scenario.startNodeId
                        history = emptyList()
                        uncertain = false
                        questionNumber = 1
                        savedLocally = false
                    }) { Text("Начать заново") }
                    Button(onClick = onBack) { Text("К списку неисправностей") }
                }
            }
        }
    }
}
