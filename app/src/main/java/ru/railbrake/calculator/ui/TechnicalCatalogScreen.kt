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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.railbrake.calculator.core.TechnicalDataRepository
import ru.railbrake.calculator.core.TechnicalEntry
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.technicalEntrySubtitle
import ru.railbrake.calculator.core.technicalEntryTitle
import ru.railbrake.calculator.core.technicalPresentationLine
import ru.railbrake.calculator.core.technicalStatusPresentation
import ru.railbrake.calculator.data.AcceptanceCheckState
import ru.railbrake.calculator.data.AcceptanceStateRepository

@Composable
fun TechnicalCatalogScreen(
    initialFamily: TechnicalFamily = TechnicalFamily.VL80S,
    initialSection: TechnicalSection = TechnicalSection.EQUIPMENT,
    sectionBackLabel: String = "Локомотивы / атлас",
    onSectionBack: () -> Unit,
    lockFamily: Boolean = false,
    lockSection: Boolean = false,
    initialEntryId: String? = null
) {
    val context = LocalContext.current
    val repository = remember { TechnicalDataRepository(context.applicationContext) }
    var familyName by rememberSaveable { mutableStateOf(initialFamily.name) }
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable(initialEntryId) { mutableStateOf(initialEntryId) }
    val family = runCatching { TechnicalFamily.valueOf(familyName) }.getOrDefault(TechnicalFamily.VL80S)
    val availableSections = remember(family, lockSection, initialSection) { if (lockSection) listOf(initialSection) else repository.sections(family) }
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

    val visible by produceState<List<TechnicalEntry>?>(initialValue = null, family, selectedSection, query) {
        value = withContext(Dispatchers.Default) {
            val loaded = repository.entries(family, selectedSection, query)
            if (selectedSection == TechnicalSection.ACCEPTANCE && query.isBlank()) loaded.filter { it.status == "ROUTE" } else loaded
        }
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
                items(if (lockFamily) listOf(family) else listOf(TechnicalFamily.VL80S, TechnicalFamily.ERMAK)) { option ->
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
                    val accent = technicalSectionAccent(option, "")
                    FilterChip(
                        selected = selectedSection == option,
                        onClick = { sectionName = option.name; query = "" },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.22f), selectedLabelColor = accent),
                        label = { Text(option.title, color = if (selectedSection == option) accent else MaterialTheme.colorScheme.onSurfaceVariant) }
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
                if (visible == null) "${selectedSection.title}: загрузка…" else "${selectedSection.title}: ${visible!!.size}",
                style = MaterialTheme.typography.labelLarge,
                color = technicalSectionAccent(selectedSection, ""),
                fontWeight = FontWeight.Bold
            )
        }
        if (visible == null) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        } else if (visible!!.isEmpty()) {
            item {
                InfoCard(
                    "Ничего не найдено",
                    listOf("Измените запрос или выберите другой раздел."),
                    MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
        items(visible.orEmpty(), key = { it.id }) { entry ->
            val accent = technicalSectionAccent(entry.section, entry.status)
            Card(
                onClick = { selectedId = entry.id },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = technicalSectionContainer(entry.section)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.52f))
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    technicalStatusLabel(entry.status)?.let { status ->
                        Text(status, style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                    Text(technicalEntryTitle(entry), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    technicalEntrySubtitle(entry)?.let { subtitle ->
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Открыть карточку →", color = accent, fontWeight = FontWeight.Bold)
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
    val accent = technicalSectionAccent(entry.section, entry.status)
    val referencedIds = entry.blocks
        .flatMap { repository.referencedEntries(it.lines) }
        .map { it.id }
        .toSet()
    val relatedEntries = entry.relatedIds
        .distinct()
        .mapNotNull(repository::entry)
        .filterNot { it.id in referencedIds }
        .take(40)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ChildBackButton(entry.section.title, onBack)
            Text(technicalEntryTitle(entry), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            technicalEntrySubtitle(entry)?.let { subtitle ->
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            technicalStatusLabel(entry.status)?.let { status ->
                RailStatusPill(status, accent = accent)
            }
        }
        if (entry.sequence.isNotEmpty()) {
            item { TechnicalSequence(entry, repository, onOpen) }
        }
        items(entry.blocks, key = { it.title }) { block ->
            val displayLines = repository.displayLines(block.lines)
                .mapNotNull(::technicalPresentationLine)
                .distinct()
            val references = repository.referencedEntries(block.lines)
            if (displayLines.isEmpty() && references.isEmpty()) return@items
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = technicalBlockContainer(entry.section, block.title)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(block.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    displayLines.forEach { line -> Text("• $line") }
                    references.forEach { target ->
                        OutlinedButton(
                            onClick = { onOpen(target) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${technicalEntryTitle(target)} →") }
                    }
                }
            }
        }
        if (relatedEntries.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text("Связанные материалы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            items(relatedEntries, key = { "related-${it.id}" }) { target ->
                OutlinedButton(
                    onClick = { onOpen(target) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${technicalEntryTitle(target)} →")
                }
            }
        }
    }
}

@Composable
private fun TechnicalSequence(entry: TechnicalEntry, repository: TechnicalDataRepository, onOpen: (TechnicalEntry) -> Unit) {
    val acceptanceRepository = remember { AcceptanceStateRepository(LocalContext.current.applicationContext) }
    var step by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var stateVersion by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    val currentId=entry.sequence[step.coerceIn(entry.sequence.indices)]
    val target=repository.entry(currentId)
    val accent=technicalSectionAccent(entry.section,entry.status)
    val currentState = stateVersion.let { acceptanceRepository.state(currentId) }
    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),border=BorderStroke(1.dp,accent.copy(alpha=.45f)),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Пошаговая приёмка",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Black)
            Text("Шаг ${step+1} из ${entry.sequence.size}",color=accent)
            Text(target?.let(::technicalEntryTitle)?:"Пункт приёмки",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)
            target?.let { item ->
                technicalEntrySubtitle(item)?.let { Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                item.blocks.forEach { block ->
                    val lines=repository.displayLines(block.lines).mapNotNull(::technicalPresentationLine).distinct()
                    if(lines.isNotEmpty()) Card(colors=CardDefaults.cardColors(containerColor=technicalBlockContainer(item.section,block.title)),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) { Text(block.title,fontWeight=FontWeight.Black); lines.forEach { Text("• $it") } }
                    }
                }
                Text("Состояние: ${currentState.label}",color=accent,fontWeight=FontWeight.Bold)
                Text("Перед переходом выберите результат проверки этого пункта", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(AcceptanceCheckState.entries) { option ->
                        FilterChip(selected=currentState==option,onClick={ acceptanceRepository.setState(currentId, option); stateVersion++ },label={Text(option.label)})
                    }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={if(step>0)step--},enabled=step>0){Text("Назад")}; Button(onClick={if(step<entry.sequence.lastIndex)step++},enabled=step<entry.sequence.lastIndex && currentState != AcceptanceCheckState.NOT_CHECKED){Text("Далее")} }
            if(target!=null) OutlinedButton(onClick={onOpen(target)},modifier=Modifier.fillMaxWidth()){Text("Открыть полную карточку")}
        }
    }
}

@Composable
private fun technicalBlockContainer(section: TechnicalSection,title:String):Color=when {
    title.contains("Опас",true)->MaterialTheme.colorScheme.errorContainer
    title.contains("Неисправ",true)->MaterialTheme.colorScheme.secondaryContainer
    title.contains("Нормаль",true)->MaterialTheme.colorScheme.primaryContainer
    title.contains("Провер",true)->MaterialTheme.colorScheme.tertiaryContainer
    else->technicalSectionContainer(section)
}

@Composable
private fun technicalSectionContainer(section:TechnicalSection):Color=when(section){
    TechnicalSection.PROFILES->MaterialTheme.colorScheme.tertiaryContainer
    TechnicalSection.SYSTEMS->MaterialTheme.colorScheme.secondaryContainer
    TechnicalSection.EQUIPMENT->MaterialTheme.colorScheme.primaryContainer
    TechnicalSection.KNOWLEDGE->MaterialTheme.colorScheme.tertiaryContainer
    TechnicalSection.DIAGNOSTICS->MaterialTheme.colorScheme.secondaryContainer
    TechnicalSection.ELECTRICAL->MaterialTheme.colorScheme.tertiaryContainer
    TechnicalSection.PNEUMATIC->MaterialTheme.colorScheme.primaryContainer
    TechnicalSection.ACCEPTANCE->MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun technicalSectionAccent(section: TechnicalSection, status: String): Color {
    if (status.equals("STOP_AND_REPORT", true) || status.equals("RESTRICT_OPERATION", true)) {
        return MaterialTheme.colorScheme.error
    }
    return when (section) {
        TechnicalSection.PROFILES -> MaterialTheme.colorScheme.tertiary
        TechnicalSection.SYSTEMS -> MaterialTheme.colorScheme.secondary
        TechnicalSection.EQUIPMENT -> MaterialTheme.colorScheme.primary
        TechnicalSection.KNOWLEDGE -> MaterialTheme.colorScheme.tertiary
        TechnicalSection.DIAGNOSTICS -> MaterialTheme.colorScheme.secondary
        TechnicalSection.ELECTRICAL -> MaterialTheme.colorScheme.tertiary
        TechnicalSection.PNEUMATIC -> MaterialTheme.colorScheme.primary
        TechnicalSection.ACCEPTANCE -> MaterialTheme.colorScheme.primary
    }
}

internal fun technicalStatusLabel(status: String): String? = technicalStatusPresentation(status)
