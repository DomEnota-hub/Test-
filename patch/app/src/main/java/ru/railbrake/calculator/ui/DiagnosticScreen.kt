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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.DiagnosticActionLevel
import ru.railbrake.calculator.core.DiagnosticCheck
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.DiagnosticScenario

@Composable
fun DiagnosticScreen() {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = DiagnosticRepository.scenarios.firstOrNull { it.id == selectedId }

    if (selected == null) {
        DiagnosticCatalog(onOpen = { selectedId = it.id })
    } else {
        DiagnosticDetails(selected, onBack = { selectedId = null })
    }
}

@Composable
private fun DiagnosticCatalog(onOpen: (DiagnosticScenario) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Все") }
    val results = remember(query, category) { DiagnosticRepository.search(query, category) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Диагностика ВЛ80С", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "Поиск причины, безопасная проверка и подготовка доклада",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { SafetyNotice() }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Симптом или аппарат") },
                placeholder = { Text("Например: ЭКГ, №395, БУРТ, АЛСН") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
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
        if (results.isEmpty()) {
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
private fun DiagnosticDetails(scenario: DiagnosticScenario, onBack: () -> Unit) {
    var questionIndex by rememberSaveable(scenario.id) { mutableIntStateOf(0) }
    var answers by rememberSaveable(scenario.id) { mutableStateOf(emptyList<String>()) }
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Все неисправности") }
            Text(scenario.category, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(scenario.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(scenario.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SafetyNotice() }
        item { InfoCard("Сначала", scenario.immediateActions, MaterialTheme.colorScheme.primaryContainer) }
        item { InfoCard("Опасные признаки", scenario.dangerSigns, MaterialTheme.colorScheme.errorContainer) }
        item {
            TriageCard(
                scenario = scenario,
                questionIndex = questionIndex,
                answers = answers,
                onAnswer = { yes ->
                    val question = scenario.questions[questionIndex]
                    val meaning = if (yes) question.yesMeaning else question.noMeaning
                    answers = answers + "${question.text} — ${if (yes) "Да" else "Нет"}. $meaning"
                    questionIndex++
                },
                onReset = {
                    answers = emptyList()
                    questionIndex = 0
                }
            )
        }
        item { InfoCard("Вероятные причины", scenario.probableCauses, MaterialTheme.colorScheme.surfaceVariant) }
        item {
            Text("Проверки по уровню допуска", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Уровень указан для каждой проверки. Он не расширяет допуск конкретного работника.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(scenario.checks) { check -> DiagnosticCheckCard(check) }
        item { InfoCard("Запрещено", scenario.prohibited, MaterialTheme.colorScheme.errorContainer) }
        item { InfoCard("Прекратить диагностику", scenario.stopConditions, MaterialTheme.colorScheme.errorContainer) }
        item {
            val report = buildDiagnosticReport(scenario, answers)
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
                    Button(onClick = { clipboard.setText(AnnotatedString(report)) }) {
                        Text("Скопировать шаблон доклада")
                    }
                }
            }
        }
        item {
            InfoCard(
                "Применимость и источник",
                listOf(scenario.applicability, scenario.sourceNote),
                MaterialTheme.colorScheme.surfaceVariant
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun TriageCard(
    scenario: DiagnosticScenario,
    questionIndex: Int,
    answers: List<String>,
    onAnswer: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Уточнение симптома", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            if (questionIndex < scenario.questions.size) {
                Text("Вопрос ${questionIndex + 1} из ${scenario.questions.size}")
                Text(scenario.questions[questionIndex].text, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onAnswer(true) }) { Text("Да") }
                    OutlinedButton(onClick = { onAnswer(false) }) { Text("Нет") }
                }
            } else {
                Text("Вопросы пройдены. Выводы включены в шаблон доклада.", fontWeight = FontWeight.Bold)
            }
            answers.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            if (answers.isNotEmpty()) TextButton(onClick = onReset) { Text("Начать заново") }
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
    InfoCard(
        title = "Важно: это не допуск к работам",
        lines = listOf(DiagnosticRepository.safetyNotice),
        tone = MaterialTheme.colorScheme.errorContainer
    )
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

internal fun buildDiagnosticReport(scenario: DiagnosticScenario, answers: List<String>): String = buildString {
    appendLine("ВЛ80С — ${scenario.title}")
    appendLine("Зафиксировать: ${scenario.reportFields.joinToString()}.")
    if (answers.isNotEmpty()) {
        appendLine("Ответы:")
        answers.forEach { appendLine("- $it") }
    }
    appendLine("Применимость: ${scenario.applicability}")
}
