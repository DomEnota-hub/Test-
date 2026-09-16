package ru.railbrake.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import ru.railbrake.calculator.core.ExamQuestion
import ru.railbrake.calculator.core.ExamQuestionRepository
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.Vl80sObservationCatalog

private fun examBlockDisplayTitle(sourceTitle: String): String = when (sourceTitle) {
    "Тест 1" -> "Блок 1"
    "Тест 2 - блок 1" -> "Блок 2"
    "Тест 2 - блок 2" -> "Блок 3"
    "Тест 2 - блок 3" -> "Блок 4"
    else -> sourceTitle
}

@Composable
fun ExamQuestionScreen(onHide: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ExamQuestionRepository(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var block by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val results = remember(query, block, category) { repository.search(query, block, category) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("База вопросов", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("329 вопросов с правильными ответами", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onHide) { Text("Скрыть раздел") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск по вопросу или ответу") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = block == null, onClick = { block = null }, label = { Text("Все блоки") }) }
            items(repository.blocks) { value ->
                FilterChip(selected = block == value, onClick = { block = value }, label = { Text(examBlockDisplayTitle(value)) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Все темы") }) }
            items(repository.categories) { value ->
                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value) })
            }
        }
        Text("Найдено: ${results.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = ExamQuestion::id) { item -> ExamQuestionCard(item) }
        }
    }
}

@Composable
private fun ExamQuestionCard(item: ExamQuestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${examBlockDisplayTitle(item.blockTitle)} • №${item.sourceNumber} • ${item.category}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(item.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Правильный ответ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.correctAnswer, style = MaterialTheme.typography.bodyLarge)
            if (item.requiresImage) {
                Text("Вопрос требует исходного изображения", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            val linkedScenarios = item.diagnosticScenarioIds.mapNotNull(DiagnosticRepository::scenario)
            val linkedEquipment = item.equipmentIds.mapNotNull(Vl80sObservationCatalog::equipment)
            if (linkedScenarios.isNotEmpty() || linkedEquipment.isNotEmpty()) {
                Text("Связано с приложением", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                linkedScenarios.forEach { Text("Диагностика: ${it.title}", style = MaterialTheme.typography.bodySmall) }
                linkedEquipment.forEach { Text("Оборудование: ${it.title}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
