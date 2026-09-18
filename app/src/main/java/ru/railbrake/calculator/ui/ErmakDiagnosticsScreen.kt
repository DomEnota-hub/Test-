package ru.railbrake.calculator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import ru.railbrake.calculator.core.ErmakDiagnosticRepository
import ru.railbrake.calculator.core.ErmakDiagnosticScenario

@Composable
fun ErmakDiagnosticsScreen(initialScenarioId: String? = null, initialEquipmentId: String? = null) {
    val context = LocalContext.current
    val repository = remember { ErmakDiagnosticRepository(context.applicationContext) }
    val scenarios by produceState<List<ErmakDiagnosticScenario>?>(null) {
        value = withContext(Dispatchers.IO) { repository.scenarios() }
    }
    var selectedId by rememberSaveable(initialScenarioId, initialEquipmentId) { mutableStateOf(initialScenarioId) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = scenarios?.firstOrNull { it.id == selectedId }

    BackHandler(enabled = selected != null) { selectedId = null }
    if (selected != null) {
        ErmakDiagnosticRoute(selected, onBack = { selectedId = null })
        return
    }

    val visible = scenarios.orEmpty().filter { scenario ->
        (initialEquipmentId == null || initialEquipmentId in scenario.equipmentIds) &&
            (query.isBlank() || listOf(scenario.title, scenario.symptom, scenario.category)
                .any { it.contains(query, ignoreCase = true) })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            RailSectionHeader("Диагностика Ермак", "Выберите неисправность или наблюдаемый симптом")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск по симптому") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (scenarios == null) {
            item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (visible.isEmpty()) {
            item { InfoCard("Сценарии не найдены", listOf("Измените запрос или вернитесь к общему списку."), MaterialTheme.colorScheme.surfaceVariant) }
        }
        items(visible, key = { it.id }) { scenario ->
            Card(
                onClick = { selectedId = scenario.id },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.48f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(scenario.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(scenario.symptom, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Начать диагностику →", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ErmakDiagnosticRoute(scenario: ErmakDiagnosticScenario, onBack: () -> Unit) {
    var nodeId by rememberSaveable(scenario.id) { mutableStateOf(scenario.startNodeId) }
    var history by rememberSaveable(scenario.id) { mutableStateOf(emptyList<String>()) }
    var observations by rememberSaveable(scenario.id) { mutableStateOf("") }
    var report by rememberSaveable(scenario.id) { mutableStateOf("") }
    val node = scenario.nodes[nodeId]

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ChildBackButton("Диагностика Ермак", onBack)
            Text(scenario.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(scenario.symptom, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (node == null) {
            item { InfoCard("Ошибка сценария", listOf("Узел маршрута не найден. Вернитесь к выбору неисправности."), MaterialTheme.colorScheme.errorContainer) }
        } else {
            item {
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
                                "question" -> "Уточнение"
                                "finding" -> "Выявленный признак"
                                "source_action" -> "Подтверждённое действие"
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
        if (node?.type == "terminal") {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        nodeId = scenario.startNodeId
                        history = emptyList()
                    }) { Text("Начать заново") }
                    Button(onClick = onBack) { Text("К списку неисправностей") }
                }
            }
        }
    }
}
