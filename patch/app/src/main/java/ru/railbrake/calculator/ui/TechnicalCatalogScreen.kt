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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.TechnicalDataRepository
import ru.railbrake.calculator.core.TechnicalEntry
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

@Composable
fun TechnicalCatalogScreen(
    initialFamily: TechnicalFamily = TechnicalFamily.VL80S,
    initialSection: TechnicalSection = TechnicalSection.EQUIPMENT,
    sectionBackLabel: String = "Локомотивы / атлас",
    onSectionBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { TechnicalDataRepository(context.applicationContext) }
    var familyName by rememberSaveable { mutableStateOf(initialFamily.name) }
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val family = runCatching { TechnicalFamily.valueOf(familyName) }.getOrDefault(TechnicalFamily.VL80S)
    val availableSections = remember(family) { repository.sections(family) }
    val selectedSection = runCatching { TechnicalSection.valueOf(sectionName) }.getOrNull()?.takeIf(availableSections::contains)
        ?: availableSections.first()
    val selected = selectedId?.let(repository::entry)

    BackHandler(enabled = selected != null) { selectedId = null }

    if (selected != null) {
        TechnicalEntryDetail(
            entry = selected,
            repository = repository,
            onBack = { selectedId = null },
            onOpen = { target -> selectedId = target.id }
        )
        return
    }

    val visible = remember(family, selectedSection, query) {
        repository.entries(family, selectedSection, query)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ChildBackButton(sectionBackLabel, onSectionBack)
            RailSectionHeader(
                "Техническая база ${family.title}",
                "Материалы и связи между разделами"
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(TechnicalFamily.VL80S, TechnicalFamily.ERMAK)) { option ->
                    FilterChip(
                        selected = family == option,
                        onClick = {
                            familyName = option.name
                            val sections = repository.sections(option)
                            if (runCatching { TechnicalSection.valueOf(sectionName) }.getOrNull() !in sections) sectionName = sections.first().name
                            query = ""
                        },
                        label = { Text(option.title) }
                    )
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableSections) { option ->
                    FilterChip(
                        selected = selectedSection == option,
                        onClick = { sectionName = option.name; query = "" },
                        label = { Text("${option.title} · ${repository.count(family, option)}") }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Поиск по названию, аппарату или признаку") },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Text(
                "${selectedSection.title}: ${visible.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        if (visible.isEmpty()) {
            item {
                InfoCard(
                    "Ничего не найдено",
                    listOf("Измените запрос или выберите другой раздел."),
                    MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
        items(visible, key = { it.id }) { entry ->
            val borderColor = if (entry.status.equals("STOP_AND_REPORT", true) || entry.status.equals("RESTRICT_OPERATION", true)) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            }
            Card(
                onClick = { selectedId = entry.id },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    technicalStatusLabel(entry.status)?.let { status ->
                        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    if (entry.subtitle.isNotBlank()) {
                        Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Открыть карточку →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TechnicalEntryDetail(
    entry: TechnicalEntry,
    repository: TechnicalDataRepository,
    onBack: () -> Unit,
    onOpen: (TechnicalEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ChildBackButton(entry.section.title, onBack)
            Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            if (entry.subtitle.isNotBlank()) {
                Text(entry.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            technicalStatusLabel(entry.status)?.let { status ->
                RailStatusPill(
                    status,
                    accent = if (entry.status.equals("STOP_AND_REPORT", true) || entry.status.equals("RESTRICT_OPERATION", true)) {
                        MaterialTheme.colorScheme.error
                    } else null
                )
            }
        }
        if (entry.sequence.isNotEmpty()) {
            item { TechnicalSequence(entry, repository, onOpen) }
        }
        items(entry.blocks, key = { it.title }) { block ->
            val displayLines = repository.displayLines(block.lines)
            val references = repository.referencedEntries(block.lines)
            if (displayLines.isEmpty() && references.isEmpty()) return@items
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(block.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    displayLines.forEach { line -> Text("• $line") }
                    references.forEach { target ->
                        OutlinedButton(
                            onClick = { onOpen(target) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${target.title} →") }
                    }
                }
            }
        }
        if (entry.relatedIds.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text("Связанные материалы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entry.relatedIds.distinct().mapNotNull(repository::entry).take(40), key = { it.id }) { target ->
                        AssistChip(
                            onClick = { onOpen(target) },
                            label = { Text(target.title) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalSequence(
    entry: TechnicalEntry,
    repository: TechnicalDataRepository,
    onOpen: (TechnicalEntry) -> Unit
) {
    var step by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    val currentId = entry.sequence[step.coerceIn(entry.sequence.indices)]
    val target = repository.entry(currentId)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Пошаговая цепь", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Шаг ${step + 1} из ${entry.sequence.size}", color = MaterialTheme.colorScheme.primary)
            Text(target?.title ?: "Элемент цепи", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (step > 0) step-- }, enabled = step > 0) { Text("Назад") }
                Button(onClick = { if (step < entry.sequence.lastIndex) step++ }, enabled = step < entry.sequence.lastIndex) { Text("Далее") }
            }
            if (target != null) {
                OutlinedButton(onClick = { onOpen(target) }) { Text("Карточка оборудования") }
            }
        }
    }
}

internal fun technicalStatusLabel(status: String): String? = when (status.trim().uppercase()) {
    "INFORMATION" -> "Справочно"
    "ATTENTION" -> "Внимание"
    "RESTRICT_OPERATION" -> "Ограничить эксплуатацию"
    "STOP_AND_REPORT" -> "Остановиться и доложить"
    "REQUIRED" -> "Обязательный параметр"
    "PROFILE_REQUIRED" -> "Требуется выбрать исполнение"
    "CONFLICT" -> "Требует уточнения"
    else -> null
}
