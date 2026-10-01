package ru.railbrake.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.railbrake.calculator.core.LocomotiveCatalogRegistry
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
import ru.railbrake.calculator.data.TechnicalRecentRepository
import ru.railbrake.calculator.data.acceptanceReportText
import ru.railbrake.calculator.data.acceptanceSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TechnicalCatalogScreen(
    initialFamily: TechnicalFamily = TechnicalFamily.VL80S,
    initialSection: TechnicalSection = TechnicalSection.EQUIPMENT,
    sectionBackLabel: String = "Локомотивы / атлас",
    onSectionBack: () -> Unit,
    lockFamily: Boolean = false,
    lockSection: Boolean = false,
    initialEntryId: String? = null,
    onOpenLegacyArticle: ((String) -> Unit)? = null,
    onOpenDiagnosticScenario: ((String, String, TechnicalSection) -> Unit)? = null,
    onFamilyChange: (TechnicalFamily) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { TechnicalDataRepository(context.applicationContext) }
    val recentRepository = remember { TechnicalRecentRepository(context.applicationContext) }
    var familyName by rememberSaveable(initialFamily) { mutableStateOf(initialFamily.name) }
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable(initialEntryId) { mutableStateOf(initialEntryId) }
    var navigationPath by rememberSaveable(initialEntryId) { mutableStateOf("") }
    var acceptanceStartChoice by rememberSaveable { mutableStateOf(false) }
    val acceptanceRepository = remember(context) { AcceptanceStateRepository(context.applicationContext) }
    var acceptanceMode by rememberSaveable { mutableStateOf("ACTIVE") }
    var acceptanceToolbarVersion by rememberSaveable { mutableIntStateOf(0) }
    var acceptanceDisabledQuery by rememberSaveable { mutableStateOf("") }
    var acceptanceHelpVisible by rememberSaveable { mutableStateOf(false) }
    var recentVersion by rememberSaveable { mutableIntStateOf(0) }
    val family = runCatching { TechnicalFamily.valueOf(familyName) }.getOrDefault(TechnicalFamily.VL80S)
    val availableSections = remember(family, lockSection, initialSection) { if (lockSection) listOf(initialSection) else repository.sections(family) }
    val selectedSection = runCatching { TechnicalSection.valueOf(sectionName) }.getOrNull()?.takeIf(availableSections::contains)
        ?: availableSections.first()
    val dedicatedAcceptance = lockSection && initialSection == TechnicalSection.ACCEPTANCE
    val acceptanceFamilyKey = family.name
    val acceptanceSaveParameters = acceptanceToolbarVersion.let { acceptanceRepository.saveParameters(acceptanceFamilyKey) }
    val acceptanceDisabledIds = acceptanceToolbarVersion.let { acceptanceRepository.disabledIds(acceptanceFamilyKey) }
    val acceptanceDisabledEntries by produceState<List<Pair<String, TechnicalEntry>>?>(
        initialValue = if (dedicatedAcceptance) null else emptyList(),
        dedicatedAcceptance,
        family,
        acceptanceToolbarVersion,
        acceptanceMode,
        acceptanceDisabledQuery,
        repository
    ) {
        value = if (!dedicatedAcceptance || acceptanceMode != "DISABLED") {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                acceptanceRepository.disabledIds(acceptanceFamilyKey)
                    .mapNotNull { id -> repository.entry(id, family)?.let { id to it } }
                    .filter { (_, item) ->
                        acceptanceDisabledQuery.isBlank() ||
                            technicalEntryTitle(item).contains(acceptanceDisabledQuery, ignoreCase = true) ||
                            technicalEntrySubtitle(item)?.contains(acceptanceDisabledQuery, ignoreCase = true) == true
                    }
                    .sortedBy { (_, item) -> technicalEntryTitle(item) }
            }
        }
    }
    val selectedState by produceState<Pair<Boolean, TechnicalEntry?>>(
        initialValue = (selectedId == null) to null,
        selectedId,
        repository
    ) {
        val id = selectedId
        value = if (id == null) {
            true to null
        } else {
            withContext(Dispatchers.Default) { true to repository.entry(id, family) }
        }
    }
    val selectedLoaded = selectedState.first
    val selected = selectedState.second?.takeIf { it.id == selectedId }
    val recentEntries = recentVersion.let {
        recentRepository.ids(family.name)
            .mapNotNull { repository.entry(it, family) }
            .filter { entry -> entry.family == family && entry.section != TechnicalSection.ACCEPTANCE }
    }

    LaunchedEffect(selected?.id) {
        selected
            ?.takeIf { it.section != TechnicalSection.ACCEPTANCE }
            ?.let { entry ->
                recentRepository.record(entry.family.name, entry.id)
                recentVersion++
            }
    }

    fun openTarget(target: TechnicalEntry) {
        val source = selected
        if (
            (target.family == TechnicalFamily.ERMAK || target.family.isChme3) &&
            target.section == TechnicalSection.DIAGNOSTICS &&
            source != null &&
            onOpenDiagnosticScenario != null
        ) {
            onOpenDiagnosticScenario(target.id, source.id, source.section)
            return
        }
        source?.id?.let { navigationPath = technicalNavigationPush(navigationPath, it) }
        selectedId = target.id
    }

    fun backFromSelected() {
        val (previousId, remainingPath) = technicalNavigationPop(navigationPath)
        navigationPath = remainingPath
        selectedId = previousId
    }

    fun openAcceptanceRoute(routeId: String) {
        val route = repository.entry(routeId, family)
        acceptanceRepository.startOrContinueSession(
            familyKey = family.name,
            routeId = routeId,
            routeTitle = route?.title ?: "Приёмка"
        )
        navigationPath = ""
        selectedId = routeId
        acceptanceToolbarVersion++
    }

    BackHandler(
        enabled = selectedId != null || acceptanceStartChoice || (dedicatedAcceptance && acceptanceMode == "DISABLED")
    ) {
        when {
            selectedId != null -> backFromSelected()
            acceptanceStartChoice -> acceptanceStartChoice = false
            dedicatedAcceptance && acceptanceMode == "DISABLED" -> acceptanceMode = "ACTIVE"
        }
    }

    if (acceptanceStartChoice) {
        AcceptanceStartChoice(
            family = family,
            onBack = { acceptanceStartChoice = false },
            onSelect = {
                openAcceptanceRoute(it)
                acceptanceStartChoice = false
            }
        )
        return
    }

    if (selectedId != null && !selectedLoaded) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChildBackButton(selectedSection.title, ::backFromSelected)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Карточка загружается…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    if (selected != null) {
        val breadcrumbEntries = technicalNavigationIds(navigationPath)
            .mapNotNull { repository.entry(it, family) } + selected
        TechnicalEntryDetail(
            entry = selected,
            repository = repository,
            backLabel = if (navigationPath.isBlank()) selected.section.title else "Назад",
            onBack = ::backFromSelected,
            breadcrumbs = breadcrumbEntries,
            onOpenBreadcrumb = { targetId ->
                if (targetId == null) {
                    sectionName = selected.section.name
                    navigationPath = ""
                    selectedId = null
                } else {
                    technicalBreadcrumbSelection(navigationPath, selected.id, targetId)?.let { (target, remainingPath) ->
                        selectedId = target
                        navigationPath = remainingPath
                    }
                }
            },
            onOpen = ::openTarget
        )
        return
    }

    if (selectedId != null && selectedLoaded) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChildBackButton(selectedSection.title, ::backFromSelected)
            Text("Карточка не найдена", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Вернитесь к списку и выберите другой элемент.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val visible by produceState<List<TechnicalEntry>?>(initialValue = null, family, selectedSection, query) {
        value = withContext(Dispatchers.Default) {
            val loaded = repository.entries(family, selectedSection, query)
            val filtered = if (selectedSection == TechnicalSection.ACCEPTANCE && query.isBlank()) {
                loaded.filter { it.status == "ROUTE" }
            } else loaded
            filtered.sortedWith(
                compareByDescending<TechnicalEntry> { isErmakLayoutEntry(it.id) }
                    .thenByDescending { it.sequence.isNotEmpty() }
            )
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
                items(if (lockFamily) listOf(family) else LocomotiveCatalogRegistry.families) { option ->
                    FilterChip(
                        selected = family == option,
                        onClick = {
                            familyName = option.name
                            onFamilyChange(option)
                            if (!lockSection) {
                                val sections = repository.sections(option)
                                if (runCatching { TechnicalSection.valueOf(sectionName) }.getOrNull() !in sections) sectionName = sections.first().name
                            }
                            query = ""
                            acceptanceDisabledQuery = ""
                        },
                        label = { Text(option.title) }
                    )
                }
            }
        }
        item {
            if (dedicatedAcceptance) {
                val accent = technicalSectionAccent(TechnicalSection.ACCEPTANCE, "")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                            selected = acceptanceMode == "ACTIVE",
                            onClick = { acceptanceMode = "ACTIVE" },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                selectedLabelColor = accent
                            ),
                            label = { Text("Приёмка") }
                        )
                    FilterChip(
                            selected = acceptanceMode == "DISABLED",
                            onClick = { acceptanceMode = "DISABLED" },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                selectedLabelColor = accent
                            ),
                            label = {
                                Text(
                                    if (acceptanceDisabledIds.isEmpty()) "Отключено"
                                    else "Отключено · ${acceptanceDisabledIds.size}"
                                )
                            }
                        )
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Сохранять", style = MaterialTheme.typography.labelMedium)
                            Switch(
                                checked = acceptanceSaveParameters,
                                onCheckedChange = { enabled ->
                                    acceptanceRepository.setSaveParameters(acceptanceFamilyKey, enabled)
                                    acceptanceToolbarVersion++
                                }
                            )
                        }
                    Card(
                            onClick = { acceptanceHelpVisible = true },
                            modifier = Modifier.width(44.dp).height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("?", fontWeight = FontWeight.Black)
                            }
                        }
                }
            } else {
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
        }
        if (!lockSection && recentEntries.isNotEmpty()) {
            item {
                Text("Недавние материалы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(
                    "Последние открытые карточки для ${family.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentEntries, key = { "recent-${it.id}" }) { entry ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                sectionName = entry.section.name
                                query = ""
                                navigationPath = ""
                                selectedId = entry.id
                            },
                            label = {
                                Text(
                                    "${entry.section.title}: ${technicalEntryTitle(entry)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
        if (dedicatedAcceptance && acceptanceMode == "DISABLED") {
            item {
                Text(
                    "Отключённые шаги ${family.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (acceptanceSaveParameters) {
                        "Изменения сохраняются и будут восстановлены при следующем запуске."
                    } else {
                        "Изменения временные и не перезаписывают ранее сохранённый набор."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = acceptanceDisabledQuery,
                    onValueChange = { acceptanceDisabledQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Поиск в отключённых") },
                    trailingIcon = {
                        if (acceptanceDisabledQuery.isNotEmpty()) {
                            IconButton(onClick = { acceptanceDisabledQuery = "" }) {
                                Text("×", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }
            if (acceptanceDisabledIds.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            acceptanceRepository.restoreAllDisabled(acceptanceFamilyKey)
                            acceptanceToolbarVersion++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Вернуть все") }
                }
            }
            when {
                acceptanceDisabledEntries == null -> item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                acceptanceDisabledIds.isEmpty() -> item {
                    InfoCard(
                        "Отключённых шагов нет",
                        listOf("Все пункты приёмки ${family.title} сейчас активны."),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                acceptanceDisabledEntries!!.isEmpty() -> item {
                    InfoCard(
                        "Ничего не найдено",
                        listOf("Измените запрос в списке отключённых шагов."),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                else -> items(acceptanceDisabledEntries!!, key = { it.first }) { (id, item) ->
                    val note = acceptanceRepository.note(id)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, technicalSectionAccent(TechnicalSection.ACCEPTANCE, "").copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(
                                technicalEntryTitle(item),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            technicalEntrySubtitle(item)?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (note.isNotBlank()) {
                                Text("Сохранённое замечание: $note", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    acceptanceRepository.setDisabled(acceptanceFamilyKey, id, false)
                                    acceptanceToolbarVersion++
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Вернуть") }
                        }
                    }
                }
            }
        } else {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Поиск по названию, аппарату или признаку") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Text("×", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                },
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
        interactiveLegacyShortcuts(family, selectedSection).forEach { shortcut ->
            item(key = "interactive-${shortcut.articleId}") {
                Card(
                    onClick = { onOpenLegacyArticle?.invoke(shortcut.articleId) },
                    enabled = onOpenLegacyArticle != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = InteractiveSchemeContainer, contentColor = InteractiveSchemeOnSurface),
                    border = BorderStroke(2.dp, InteractiveSchemeAccent)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("ИНТЕРАКТИВНАЯ СХЕМА", style = MaterialTheme.typography.labelSmall, color = InteractiveSchemeAccent, fontWeight = FontWeight.Black)
                        Text(shortcut.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(shortcut.subtitle, color = InteractiveSchemeMuted)
                        Text("Открыть интерактивную схему →", color = InteractiveSchemeAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        items(visible.orEmpty(), key = { it.id }) { entry ->
            val accent = technicalSectionAccent(entry.section, entry.status)
            val interactive = entry.sequence.isNotEmpty() && entry.section != TechnicalSection.ACCEPTANCE
            Card(
                onClick = {
                    if (entry.section == TechnicalSection.ACCEPTANCE && entry.id.endsWith("-ROUTE-route_canonical")) acceptanceStartChoice = true
                    else if (entry.section == TechnicalSection.ACCEPTANCE) {
                        openAcceptanceRoute(entry.id)
                    } else {
                        navigationPath = ""
                        selectedId = entry.id
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (interactive) InteractiveSchemeContainer else technicalSectionContainer(entry.section),
                    contentColor = if (interactive) InteractiveSchemeOnSurface else MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(if (interactive) 2.dp else 1.dp, if (interactive) InteractiveSchemeAccent else accent.copy(alpha = 0.52f))
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (interactive) {
                        Text("ИНТЕРАКТИВНАЯ СХЕМА", style = MaterialTheme.typography.labelSmall, color = InteractiveSchemeAccent, fontWeight = FontWeight.Black)
                    } else technicalStatusLabel(entry.status)?.let { status ->
                        Text(status, style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                    Text(technicalEntryTitle(entry), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    technicalEntrySubtitle(entry)?.let { subtitle ->
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (interactive) InteractiveSchemeMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (entry.family == TechnicalFamily.ERMAK) 2 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (entry.section == TechnicalSection.ACCEPTANCE && entry.sequence.isNotEmpty()) {
                        val activeIds = entry.sequence.filterNot(acceptanceDisabledIds::contains)
                        val routeSummary = acceptanceSummary(activeIds.map(acceptanceRepository::state))
                        val handled = routeSummary.total - routeSummary.notChecked
                        if (routeSummary.total > 0) {
                            LinearProgressIndicator(
                                progress = handled.toFloat() / routeSummary.total,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "$handled из ${routeSummary.total} пунктов обработано",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (handled > 0) "Продолжить →" else "Начать →",
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(if (interactive) "Открыть интерактивную схему →" else "Открыть карточку →", color = if (interactive) InteractiveSchemeAccent else accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }
    if (dedicatedAcceptance && acceptanceHelpVisible) {
        AcceptanceHelpDialog(onDismiss = { acceptanceHelpVisible = false })
    }
}

@Composable
private fun AcceptanceHelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.padding(vertical = 24.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        "Настройка приёмки",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Вы можете отключать отдельные шаги проверки, если они не требуются по местным инструкциям.")
                        Text("Отключённые пункты не участвуют в пошаговой приёмке и не учитываются как непроверенные в итоговом результате.")
                        Text("В разделе «Отключено» можно вернуть отдельный пункт или восстановить все шаги сразу.")
                        Text("Незавершённая приёмка сохраняется на устройстве: на карточке маршрута виден прогресс и можно продолжить с прежними отметками.")
                        Text("Обязательная часть и полный осмотр используют общие отметки для одинаковых пунктов — повторно отмечать их не нужно.")
                        Text("Кнопка «Начать новую приёмку» удаляет отметки и замечания текущего сеанса, но сохраняет настройку отключённых шагов.")
                        Text("Переключатель «Сохранять»: включён — выбранные отключения сохраняются для текущего локомотива; выключен — изменения действуют временно и не изменяют ранее сохранённый набор.")
                        Text("Настройки ВЛ80С и Ермака хранятся отдельно.")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Понятно") }
                    }
                }
            }
        }
    }
}

private val InteractiveSchemeContainer = Color(0xFF17363A)
private val InteractiveSchemeAccent = Color(0xFF65E3D2)
private val InteractiveSchemeOnSurface = Color(0xFFF0F6F5)
private val InteractiveSchemeMuted = Color(0xFFB8CCC9)

internal data class InteractiveLegacyShortcut(val articleId: String, val title: String, val subtitle: String)

internal fun interactiveLegacyShortcuts(family: TechnicalFamily, section: TechnicalSection): List<InteractiveLegacyShortcut> = when {
    family == TechnicalFamily.VL80S && section == TechnicalSection.EQUIPMENT -> listOf(
        InteractiveLegacyShortcut(
            articleId = "vl80-layout",
            title = "Расположение оборудования ВЛ80С",
            subtitle = "Схема секции с кликабельными зонами, краткой сводкой и переходом к подробному описанию."
        )
    )
    family == TechnicalFamily.VL80S && section == TechnicalSection.PNEUMATIC -> listOf(
        InteractiveLegacyShortcut(
            articleId = "vl80-pneumatic-simulator",
            title = "Интерактивная пневмосхема ВЛ80С",
            subtitle = "Пошаговое движение воздуха, указатели маршрута и кликабельные приборы."
        )
    )
    family == TechnicalFamily.VL80S && section == TechnicalSection.ELECTRICAL -> listOf(
        InteractiveLegacyShortcut(
            articleId = "vl80-electrical-simulator",
            title = "Интерактивная электросхема ВЛ80С",
            subtitle = "Пошаговый разбор цепи с переходами между связанными элементами."
        )
    )
    else -> emptyList()
}

@Composable
private fun AcceptanceStartChoice(
    family: TechnicalFamily,
    onBack: () -> Unit,
    onSelect: (String) -> Unit
) {
    val routePrefix = when {
        family.isChme3 -> "${family.name}-CHME3"
        family == TechnicalFamily.ERMAK -> "ER"
        else -> "VL80"
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChildBackButton("Приёмка", onBack)
        RailSectionHeader("Полный осмотр ${family.title}", "Обязательные пункты сохраняют уже отмеченное состояние")
        Card(
  onClick = { onSelect("$routePrefix-ROUTE-route_from_outside") },
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  shape = RoundedCornerShape(18.dp)
        ) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text("Начать снаружи", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
      Text("Обязательные пункты, затем расширенный маршрут от наружных зон к оборудованию внутри локомотива и кабине.")
  }
        }
        Card(
  onClick = { onSelect("$routePrefix-ROUTE-route_from_cab") },
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
  shape = RoundedCornerShape(18.dp)
        ) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text("Начать из кабины", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
      Text("Обязательные пункты, затем расширенный маршрут от кабины к внутренним и наружным зонам.")
  }
        }
    }
}

@Composable
private fun TechnicalEntryDetail(
    entry: TechnicalEntry,
    repository: TechnicalDataRepository,
    backLabel: String,
    onBack: () -> Unit,
    breadcrumbs: List<TechnicalEntry>,
    onOpenBreadcrumb: (String?) -> Unit,
    onOpen: (TechnicalEntry) -> Unit
) {
    val accent = technicalSectionAccent(entry.section, entry.status)
    val isErmakLayout = isErmakLayoutEntry(entry.id)
    val compactErmak = entry.family == TechnicalFamily.ERMAK && entry.section != TechnicalSection.ACCEPTANCE
    val detailBlocks = remember(entry.id) {
        if (isErmakLayout) entry.blocks.filterNot { it.title == "Оборудование" } else entry.blocks
    }
    val renderableDetailBlocks = remember(entry.id, repository) {
        detailBlocks.filter { block ->
            repository.displayLines(block.lines).mapNotNull(::technicalPresentationLine).any() ||
                repository.referencedEntries(block.lines).isNotEmpty() ||
                (block.title.startsWith("Таблица —") && block.lines.any { it.split("¦", limit = 3).size == 3 })
        }
    }
    var expandedErmakBlocks by rememberSaveable(entry.id) {
        mutableStateOf(
            renderableDetailBlocks
                .filter { ermakCompactBlockStartsExpanded(it.title) }
                .joinToString("\n") { it.title }
        )
    }
    var selectedErmakRelatedSection by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    val expandedErmakBlockTitles = remember(expandedErmakBlocks) {
        ermakExpandedBlockTitles(expandedErmakBlocks)
    }
    val allErmakBlocksExpanded = compactErmak && renderableDetailBlocks.isNotEmpty() &&
        renderableDetailBlocks.all { it.title in expandedErmakBlockTitles }
    val referencedIds = remember(entry.id, repository) {
        if (isErmakLayout) {
            emptySet()
        } else {
            entry.blocks
                .flatMap { repository.referencedEntries(it.lines) }
                .map { it.id }
                .toSet()
        }
    }
    val relatedEntries = remember(entry.id, repository) {
        if (isErmakLayout) {
            emptyList()
        } else {
            entry.relatedIds
                .distinct()
                .mapNotNull { repository.entry(it, entry.family) }
                .filterNot { it.id == entry.id || it.id in referencedIds }
                .sortedWith(
                    compareBy<TechnicalEntry> { technicalRelatedSectionOrder(it.section) }
                        .thenBy { technicalEntryTitle(it).lowercase() }
                )
        }
    }
    val relatedGroups = remember(entry.id, repository) {
        relatedEntries.groupBy(TechnicalEntry::section)
            .toList()
            .sortedBy { (section, _) -> technicalRelatedSectionOrder(section) }
    }
    val schemeLinkContext by produceState<ru.railbrake.calculator.core.ErmakSchemeLinkContext?>(
        initialValue = null,
        entry.id,
        repository
    ) {
        value = withContext(Dispatchers.Default) {
            repository.ermakSchemeLinkContext(entry.id)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ChildBackButton(backLabel, onBack)
            TechnicalBreadcrumbs(entry, breadcrumbs, onOpenBreadcrumb)
            Text(technicalEntryTitle(entry), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            technicalEntrySubtitle(entry)?.let { subtitle ->
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compactErmak) 3 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
            }
            technicalStatusLabel(entry.status)?.let { status ->
                RailStatusPill(status, accent = accent)
            }
        }
        if ((entry.id.startsWith("ER-SCH-LAYOUT-") || entry.family.isChme3) && entry.hotspots.isNotEmpty()) {
            item { ErmakInteractiveAtlas(entry, repository, onOpen) }
        } else if (entry.section == TechnicalSection.ACCEPTANCE && entry.sequence.isNotEmpty()) {
            item { TechnicalSequence(entry, repository) }
        } else if (entry.sequence.isNotEmpty()) {
            item { TechnicalSequenceLinks(entry, repository, onOpen) }
        }
        schemeLinkContext
            ?.takeIf { it.diagnosticLinks.isNotEmpty() }
            ?.let { linkContext ->
                item(key = "scheme-diagnostics-${entry.id}") {
                    ErmakSchemeDiagnostics(entry, linkContext, repository, onOpen)
                }
            }
        if (compactErmak && renderableDetailBlocks.isNotEmpty()) {
            item(key = "ermak-details-control-${entry.id}") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Подробности", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(
                            "${renderableDetailBlocks.size} разделов • открывайте только нужное",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            expandedErmakBlocks = if (allErmakBlocksExpanded) {
                                ""
                            } else {
                                renderableDetailBlocks.joinToString("\n") { it.title }
                            }
                        }
                    ) {
                        Text(if (allErmakBlocksExpanded) "Свернуть всё" else "Показать всё")
                    }
                }
            }
        }
        items(renderableDetailBlocks, key = { it.title }) { block ->
            val displayLines = repository.displayLines(block.lines)
                .mapNotNull(::technicalPresentationLine)
                .distinct()
            val references = repository.referencedEntries(block.lines)
            val referenceTableRows = if (block.title.startsWith("Таблица —")) {
                block.lines.mapNotNull { line ->
                    line.split("¦", limit = 3).takeIf { it.size == 3 }
                }
            } else emptyList()
            val expanded = !compactErmak || block.title in expandedErmakBlockTitles
            val contentCount = if (referenceTableRows.isNotEmpty()) {
                referenceTableRows.size
            } else {
                displayLines.size + references.size
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = technicalBlockContainer(entry.section, block.title)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(block.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            if (compactErmak && !expanded) {
                                Text(
                                    "$contentCount пунктов",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (compactErmak) {
                            TextButton(
                                onClick = {
                                    expandedErmakBlocks = ermakToggleExpandedBlock(expandedErmakBlocks, block.title)
                                }
                            ) {
                                Text(if (expanded) "Свернуть" else "Раскрыть")
                            }
                        }
                    }
                    if (expanded) {
                        if (referenceTableRows.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Размер", modifier = Modifier.weight(0.75f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                                Text("Скорость", modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                                Text("Действие", modifier = Modifier.weight(1.55f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                            }
                            HorizontalDivider()
                            referenceTableRows.forEachIndexed { index, cells ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(cells[0], modifier = Modifier.weight(0.75f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(cells[1], modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.bodySmall)
                                    Text(cells[2], modifier = Modifier.weight(1.55f), style = MaterialTheme.typography.bodySmall)
                                }
                                if (index != referenceTableRows.lastIndex) HorizontalDivider()
                            }
                        } else {
                            displayLines.forEach { line -> Text("• $line") }
                            references.forEach { target ->
                                OutlinedButton(
                                    onClick = { onOpen(target) },
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) { Text("${technicalEntryTitle(target)} →") }
                            }
                        }
                    }
                }
            }
        }
        if (relatedGroups.isNotEmpty()) {
            if (compactErmak) {
                item(key = "compact-related-${entry.id}") {
                    val selectedGroup = relatedGroups.firstOrNull { (section, _) ->
                        section.name == selectedErmakRelatedSection
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Связанные материалы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                Text("${relatedEntries.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(relatedGroups) { (section, targets) ->
                                    FilterChip(
                                        selected = selectedErmakRelatedSection == section.name,
                                        onClick = {
                                            selectedErmakRelatedSection = if (selectedErmakRelatedSection == section.name) null else section.name
                                        },
                                        label = { Text("${section.title} · ${targets.size}") }
                                    )
                                }
                            }
                            if (selectedGroup == null) {
                                Text(
                                    "Выберите группу, чтобы открыть связанные карточки.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                selectedGroup.second.forEach { target ->
                                    OutlinedButton(
                                        onClick = { onOpen(target) },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Text("${technicalEntryTitle(target)} →")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    HorizontalDivider()
                    Text("Связанные материалы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        "Материалы сгруппированы по назначению; уже показанные внутри карточки ссылки здесь не повторяются.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                relatedGroups.forEach { (section, targets) ->
                    item(key = "related-header-${entry.id}-${section.name}") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(section.title, fontWeight = FontWeight.Bold, color = technicalSectionAccent(section, ""))
                            Text("${targets.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(targets, key = { "related-${section.name}-${it.id}" }) { target ->
                        OutlinedButton(
                            onClick = { onOpen(target) },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text("${technicalEntryTitle(target)} →")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalBreadcrumbs(
    current: TechnicalEntry,
    entries: List<TechnicalEntry>,
    onOpen: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            TextButton(onClick = { onOpen(null) }) {
                Text("${current.family.title} / ${current.section.title}", maxLines = 1)
            }
        }
        entries.forEachIndexed { index, entry ->
            item(key = "breadcrumb-separator-$index-${entry.id}") {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "breadcrumb-$index-${entry.id}") {
                if (index == entries.lastIndex) {
                    Text(
                        technicalEntryTitle(entry),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                } else {
                    TextButton(onClick = { onOpen(entry.id) }) {
                        Text(technicalEntryTitle(entry), maxLines = 1)
                    }
                }
            }
        }
    }
}

internal fun ermakCompactBlockStartsExpanded(title: String): Boolean {
    val normalized = title.trim().lowercase()
    return normalized.startsWith("назнач") ||
        normalized.startsWith("располож") ||
        normalized.startsWith("кратк") ||
        normalized.startsWith("важно") ||
        normalized.startsWith("главное") ||
        normalized.contains("опас") ||
        normalized.contains("предупреж")
}

internal fun ermakExpandedBlockTitles(serialized: String): Set<String> =
    serialized.lineSequence().map(String::trim).filter(String::isNotBlank).toSet()

internal fun ermakToggleExpandedBlock(serialized: String, title: String): String {
    val titles = ermakExpandedBlockTitles(serialized).toMutableSet()
    if (!titles.add(title)) titles.remove(title)
    return titles.sorted().joinToString("\n")
}

internal fun technicalNavigationIds(path: String): List<String> =
    path.lineSequence().map(String::trim).filter(String::isNotBlank).toList()

internal fun technicalNavigationPush(path: String, currentId: String): String =
    (technicalNavigationIds(path) + currentId).joinToString("\n")

internal fun technicalNavigationPop(path: String): Pair<String?, String> {
    val ids = technicalNavigationIds(path)
    return ids.lastOrNull() to ids.dropLast(1).joinToString("\n")
}

internal fun technicalBreadcrumbSelection(
    path: String,
    currentId: String,
    targetId: String
): Pair<String, String>? {
    val chain = technicalNavigationIds(path) + currentId
    val targetIndex = chain.indexOf(targetId)
    if (targetIndex < 0) return null
    return targetId to chain.take(targetIndex).joinToString("\n")
}

internal fun technicalRelatedSectionOrder(section: TechnicalSection): Int = when (section) {
    TechnicalSection.SYSTEMS -> 0
    TechnicalSection.EQUIPMENT -> 1
    TechnicalSection.KNOWLEDGE -> 2
    TechnicalSection.DIAGNOSTICS -> 3
    TechnicalSection.ELECTRICAL, TechnicalSection.PNEUMATIC -> 4
    TechnicalSection.SAFETY -> 5
    TechnicalSection.PROFILES -> 6
    TechnicalSection.ACCEPTANCE -> 7
}

internal fun isErmakLayoutEntry(id: String): Boolean = id.startsWith("ER-SCH-LAYOUT-")

internal val ErmakAtlasVariants = listOf(
    "ER-SCH-LAYOUT-2ES5K-BASE" to "2ЭС5К",
    "ER-SCH-LAYOUT-3ES5K-HEAD" to "3ЭС5К · головная",
    "ER-SCH-LAYOUT-3ES5K-BOOSTER" to "3ЭС5К · бустерная"
)

internal fun ermakAtlasHitTest(
    hotspots: List<ru.railbrake.calculator.core.TechnicalHotspot>,
    x: Float,
    y: Float
): ru.railbrake.calculator.core.TechnicalHotspot? = hotspots
    .asSequence()
    .filter { hotspot ->
        x >= hotspot.x && x <= hotspot.x + hotspot.width &&
            y >= hotspot.y && y <= hotspot.y + hotspot.height
    }
    .minByOrNull { hotspot -> hotspot.width * hotspot.height }

internal fun ermakAtlasWrapLabel(
    label: String,
    maxWidth: Float,
    maxLines: Int,
    measureText: (String) -> Float
): List<String> {
    val trimmed = label.trim()
    if (trimmed.isEmpty() || maxWidth <= 0f || maxLines <= 0) return emptyList()

    fun fittingPrefixLength(value: String, width: Float, suffix: String = ""): Int {
        if (value.isEmpty()) return 0
        var low = 1
        var high = value.length
        var best = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (measureText(value.take(mid) + suffix) <= width) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    val lines = mutableListOf<String>()
    var remaining = trimmed
    while (remaining.isNotEmpty() && lines.size < maxLines) {
        if (measureText(remaining) <= maxWidth) {
            lines += remaining
            break
        }

        val isLastLine = lines.size == maxLines - 1
        if (isLastLine) {
            val count = fittingPrefixLength(remaining, maxWidth, "…")
            val visible = remaining.take(count.coerceAtLeast(1)).trimEnd()
            lines += "$visible…"
            break
        }

        val fitted = fittingPrefixLength(remaining, maxWidth).coerceAtLeast(1)
        val prefix = remaining.take(fitted)
        val preferredBreak = sequenceOf(
            prefix.lastIndexOf(' ') + 1,
            prefix.lastIndexOf('-') + 1,
            prefix.lastIndexOf('/') + 1
        )
            .filter { it > fitted / 2 }
            .maxOrNull()
            ?: fitted

        lines += remaining.take(preferredBreak).trim()
        remaining = remaining.drop(preferredBreak).trimStart()
    }
    return lines
}

@Composable
private fun ErmakInteractiveAtlas(
    entry: TechnicalEntry,
    repository: TechnicalDataRepository,
    onOpen: (TechnicalEntry) -> Unit
) {
    var selectedId by rememberSaveable(entry.id) { mutableStateOf(entry.hotspots.firstOrNull()?.equipmentId) }
    var query by rememberSaveable(entry.id) { mutableStateOf("") }
    var detailsVisible by rememberSaveable(entry.id) { mutableStateOf(false) }
    val selectedHotspot = remember(entry.id, selectedId) {
        entry.hotspots.firstOrNull { it.equipmentId == selectedId }
    }
    val selectedEquipmentState by produceState<Pair<Boolean, TechnicalEntry?>>(
        initialValue = false to null,
        selectedId,
        repository
    ) {
        value = false to null
        val equipmentId = selectedId
        value = if (equipmentId == null) {
            true to null
        } else {
            withContext(Dispatchers.Default) {
                true to repository.entry(equipmentId, entry.family)
            }
        }
    }
    val selectedEquipmentLoaded = selectedEquipmentState.first
    val selectedEquipment = selectedEquipmentState.second
    val visible = remember(entry.id, query) {
        entry.hotspots.filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) || it.equipmentId.contains(query, ignoreCase = true)
        }
    }
    val maxX = remember(entry.id) { entry.hotspots.maxOfOrNull { it.x + it.width }?.coerceAtLeast(1) ?: 1 }
    val maxY = remember(entry.id) { entry.hotspots.maxOfOrNull { it.y + it.height }?.coerceAtLeast(1) ?: 1 }
    val scale = 0.62f
    val mapWidth = maxX * scale
    val mapHeight = maxY * scale
    val horizontal = rememberScrollState()
    val visibleIds = remember(visible) { visible.asSequence().map { it.equipmentId }.toSet() }
    val variantTargets by produceState<Map<String, TechnicalEntry?>>(
        initialValue = emptyMap(),
        repository
    ) {
        value = withContext(Dispatchers.Default) {
            ErmakAtlasVariants.associate { (id, _) -> id to repository.entry(id) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = InteractiveSchemeContainer, contentColor = InteractiveSchemeOnSurface),
        border = BorderStroke(2.dp, InteractiveSchemeAccent),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Интерактивный атлас «Ермак»", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Функциональная карта по встроенным данным компоновки. Зоны показывают принадлежность оборудования, но не заменяют заводской монтажный чертёж.",
                style = MaterialTheme.typography.bodySmall,
                color = InteractiveSchemeMuted
            )
            Text("Исполнение и секция", color = InteractiveSchemeAccent, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ErmakAtlasVariants) { (id, label) ->
                    val target = variantTargets[id]
                    FilterChip(
                        selected = entry.id == id,
                        onClick = { target?.let(onOpen) },
                        enabled = target != null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = InteractiveSchemeMuted,
                            selectedContainerColor = InteractiveSchemeAccent.copy(alpha = 0.18f),
                            selectedLabelColor = InteractiveSchemeOnSurface,
                            disabledContainerColor = Color.Transparent,
                            disabledLabelColor = InteractiveSchemeMuted.copy(alpha = 0.72f)
                        ),
                        label = { Text(label) }
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = InteractiveSchemeOnSurface),
                label = { Text("Найти аппарат на карте", color = InteractiveSchemeMuted) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = InteractiveSchemeOnSurface,
                    unfocusedTextColor = InteractiveSchemeOnSurface,
                    cursorColor = InteractiveSchemeAccent,
                    focusedBorderColor = InteractiveSchemeAccent,
                    unfocusedBorderColor = InteractiveSchemeMuted.copy(alpha = 0.62f),
                    focusedLabelColor = InteractiveSchemeAccent,
                    unfocusedLabelColor = InteractiveSchemeMuted
                )
            )
            Text("Элементов: ${entry.hotspots.size}", style = MaterialTheme.typography.labelMedium, color = InteractiveSchemeAccent)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontal)
                    .background(Color(0xFF0D1F22), RoundedCornerShape(14.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .width(mapWidth.dp)
                        .height(mapHeight.dp)
                        .pointerInput(entry.id, entry.hotspots) {
                            detectTapGestures { tap ->
                                val sourceX = tap.x / density / scale
                                val sourceY = tap.y / density / scale
                                ermakAtlasHitTest(entry.hotspots, sourceX, sourceY)?.let { hotspot ->
                                    selectedId = hotspot.equipmentId
                                    detailsVisible = true
                                }
                            }
                        }
                ) {
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 9.dp.toPx()
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    }
                    entry.hotspots.forEach { hotspot ->
                        val isSelected = hotspot.equipmentId == selectedId
                        val isMatched = hotspot.equipmentId in visibleIds
                        val left = (hotspot.x * scale).dp.toPx()
                        val top = (hotspot.y * scale).dp.toPx()
                        val width = (hotspot.width * scale).dp.toPx()
                        val height = (hotspot.height * scale).dp.toPx()
                        val fill = when {
                            isSelected -> InteractiveSchemeAccent.copy(alpha = 0.72f)
                            isMatched -> InteractiveSchemeAccent.copy(alpha = 0.20f)
                            else -> Color(0xFF243336)
                        }
                        drawRoundRect(
                            color = fill,
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            cornerRadius = CornerRadius(7.dp.toPx())
                        )
                        drawRoundRect(
                            color = if (isSelected) InteractiveSchemeAccent else Color(0xFF42585C),
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            cornerRadius = CornerRadius(7.dp.toPx()),
                            style = Stroke(1.dp.toPx())
                        )
                        labelPaint.color = (if (isSelected) Color(0xFF071313) else Color(0xFFE7F0F2)).toArgb()
                        val horizontalTextPadding = 5.dp.toPx()
                        val verticalTextPadding = 4.dp.toPx()
                        val lineHeight = labelPaint.fontSpacing
                        val maxTextWidth = (width - horizontalTextPadding * 2f).coerceAtLeast(1f)
                        val maxLines = ((height - verticalTextPadding * 2f) / lineHeight)
                            .toInt()
                            .coerceIn(1, 5)
                        val labelLines = ermakAtlasWrapLabel(
                            label = hotspot.label,
                            maxWidth = maxTextWidth,
                            maxLines = maxLines,
                            measureText = { value -> labelPaint.measureText(value) }
                        )
                        val metrics = labelPaint.fontMetrics
                        val textBlockHeight = lineHeight * labelLines.size
                        val firstBaseline = top + ((height - textBlockHeight) / 2f) - metrics.ascent
                        labelLines.forEachIndexed { index, line ->
                            drawContext.canvas.nativeCanvas.drawText(
                                line,
                                left + horizontalTextPadding,
                                firstBaseline + index * lineHeight,
                                labelPaint
                            )
                        }
                    }
                }
            }
            if (query.isNotBlank()) {
                Text("Результаты поиска", fontWeight = FontWeight.Bold)
                visible.take(20).forEach { hotspot ->
                    OutlinedButton(
                        onClick = {
                            selectedId = hotspot.equipmentId
                            detailsVisible = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) { Text(hotspot.label) }
                }
                if (visible.size > 20) Text("Показаны первые 20 из ${visible.size}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (detailsVisible) {
        selectedHotspot?.let { hotspot ->
            val equipment = selectedEquipment
            AlertDialog(
                onDismissRequest = { detailsVisible = false },
                title = {
                    Text(hotspot.label, fontWeight = FontWeight.Black)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            !selectedEquipmentLoaded -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    "Карточка оборудования загружается…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            equipment != null -> {
                                technicalEntrySubtitle(equipment)?.let { subtitle ->
                                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } ?: Text(
                                    "Для элемента доступна полная карточка оборудования.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> Text(
                                "Для выбранной зоны отдельная карточка оборудования пока отсутствует.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    if (selectedEquipmentLoaded) {
                        equipment?.let { target ->
                            TextButton(
                                onClick = {
                                    detailsVisible = false
                                    onOpen(target)
                                }
                            ) {
                                Text("Подробнее")
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { detailsVisible = false }) {
                        Text("Закрыть")
                    }
                }
            )
        }
    }
}

@Composable
private fun ErmakSchemeDiagnostics(
    entry: TechnicalEntry,
    linkContext: ru.railbrake.calculator.core.ErmakSchemeLinkContext,
    repository: TechnicalDataRepository,
    onOpen: (TechnicalEntry) -> Unit
) {
    var executionConfirmed by rememberSaveable(entry.id, "ermak-scheme-execution") { mutableStateOf(false) }
    var query by rememberSaveable(entry.id, "ermak-scheme-diagnostics-query") { mutableStateOf("") }
    val gatedCount = linkContext.diagnosticLinks.count { it.profileGateRequired }
    val directCount = linkContext.diagnosticLinks.size - gatedCount
    val availableState by produceState<Pair<Boolean, List<TechnicalEntry>>>(
        initialValue = false to emptyList(),
        entry.id,
        executionConfirmed,
        repository
    ) {
        value = withContext(Dispatchers.Default) {
            true to linkContext.diagnosticLinks
                .asSequence()
                .filter { !it.profileGateRequired || executionConfirmed }
                .mapNotNull { link -> repository.entry(link.diagnosticId) }
                .distinctBy(TechnicalEntry::id)
                .toList()
        }
    }
    val availableLoaded = availableState.first
    val available = availableState.second
    val visible = remember(available, query) {
        available.filter { target ->
            query.isBlank() || target.searchText.contains(query.trim(), ignoreCase = true)
        }
    }
    val modelText = linkContext.models.map(::ermakSchemeModelLabel).distinct().joinToString(" / ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = technicalBlockContainer(entry.section, "Диагностика")),
        border = BorderStroke(1.dp, InteractiveSchemeAccent.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Диагностика по схеме", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                "Сразу доступно: $directCount • после подтверждения исполнения: $gatedCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (gatedCount > 0) {
                if (!executionConfirmed) {
                    if (modelText.isNotBlank()) {
                        Text("Серия схемы: $modelText", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Часть переходов зависит от фактического исполнения. Подтверждайте только если открытая схема соответствует установленному на локомотиве оборудованию. Подтверждение действует только для этой схемы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { executionConfirmed = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, InteractiveSchemeAccent)
                    ) {
                        Text("Подтвердить соответствие исполнения")
                    }
                    Text(
                        "Скрыто до подтверждения: $gatedCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Исполнение подтверждено для этой схемы. Профильные диагностические переходы разблокированы.",
                        color = InteractiveSchemeAccent,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { executionConfirmed = false }) {
                        Text("Сбросить подтверждение")
                    }
                }
            }
            if (!availableLoaded) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Диагностические переходы загружаются…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (available.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Найти диагностический сценарий") }
                )
                visible.take(20).forEach { target ->
                    OutlinedButton(
                        onClick = { onOpen(target) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("${technicalEntryTitle(target)} →")
                    }
                }
                if (visible.size > 20) {
                    Text(
                        "Показаны первые 20 из ${visible.size}; уточните поиск.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "Для текущего состояния доступных диагностических переходов нет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun ermakSchemeModelLabel(value: String): String = when (value) {
    "2ES5K" -> "2ЭС5К"
    "3ES5K" -> "3ЭС5К"
    else -> value
}

@Composable
private fun TechnicalSequenceLinks(entry: TechnicalEntry, repository: TechnicalDataRepository, onOpen: (TechnicalEntry) -> Unit) {
    if (entry.sequence.isEmpty()) return
    var step by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var query by rememberSaveable(entry.id) { mutableStateOf("") }
    val steps = entry.sequence.map { id ->
        val target = repository.entry(id, entry.family)
        val label = target?.let(::technicalEntryTitle)
            ?: entry.sequenceLabels[id]?.let(::technicalPresentationLine)
            ?: "Узел схемы"
        Triple(id, label, target)
    }
    val visible = steps.filter { (_, label, _) -> query.isBlank() || label.contains(query, ignoreCase = true) }
    val safeStep = step.coerceIn(steps.indices)
    val current = steps[safeStep]
    val accent = technicalSectionAccent(entry.section, entry.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Интерактивная схема", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Узел ${safeStep + 1} из ${steps.size}", color = accent, fontWeight = FontWeight.Bold)
            Text(current.second, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            current.third?.let { target ->
                technicalEntrySubtitle(target)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedButton(
                    onClick = { onOpen(target) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("Открыть карточку оборудования")
                }
            } ?: Text(
                "Служебный узел маршрута; отдельная карточка оборудования не предусмотрена.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { step-- }, enabled = safeStep > 0) { Text("Назад") }
                Button(onClick = { step++ }, enabled = safeStep < steps.lastIndex) { Text("Далее") }
                TextButton(onClick = { step = 0 }) { Text("Сбросить") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Найти узел или аппарат") }
            )
            visible.forEach { (id, label, target) ->
                OutlinedButton(
                    onClick = {
                        step = steps.indexOfFirst { it.first == id }.coerceAtLeast(0)
                        query = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(if (target == null) label else "$label →")
                }
            }
        }
    }
}

@Composable
private fun TechnicalSequence(entry: TechnicalEntry, repository: TechnicalDataRepository) {
    val context = LocalContext.current
    val acceptanceRepository = remember(context) { AcceptanceStateRepository(context.applicationContext) }
    val familyKey = entry.family.name
    var step by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var stateVersion by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var settingsVersion by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var showFullChecklist by rememberSaveable(entry.id) { mutableStateOf(false) }
    var checklistQuery by rememberSaveable(entry.id) { mutableStateOf("") }
    var visibleLimit by rememberSaveable(entry.id) { mutableIntStateOf(30) }
    var resetConfirmationVisible by rememberSaveable(entry.id) { mutableStateOf(false) }

    val disabledIds = settingsVersion.let { acceptanceRepository.disabledIds(familyKey) }
    val disabledInRoute = entry.sequence.filter(disabledIds::contains)
    val activeSequence = entry.sequence.filterNot(disabledIds::contains)
    val effectiveStep = if (activeSequence.isEmpty()) 0 else step.coerceIn(0, activeSequence.lastIndex)
    val currentId = activeSequence.getOrNull(effectiveStep)
    val target = currentId?.let { repository.entry(it, entry.family) }
    val accent = technicalSectionAccent(entry.section, entry.status)
    val currentState = currentId?.let { id -> stateVersion.let { acceptanceRepository.state(id) } }
        ?: AcceptanceCheckState.NOT_CHECKED
    val currentNote = currentId?.let { id -> stateVersion.let { acceptanceRepository.note(id) } }.orEmpty()
    val checklistItems = activeSequence.mapNotNull { id -> repository.entry(id, entry.family)?.let { id to it } }
    val states = stateVersion.let { activeSequence.map(acceptanceRepository::state) }
    val summary = acceptanceSummary(states)
    val savedNotes = stateVersion.let {
        checklistItems.mapNotNull { (id, item) ->
            if (acceptanceRepository.state(id) == AcceptanceCheckState.NOTE) {
                technicalEntryTitle(item) to acceptanceRepository.note(id)
            } else null
        }
    }
    val openedSession = remember(entry.id) {
        acceptanceRepository.startOrContinueSession(familyKey, entry.id, entry.title)
    }
    val session = stateVersion.let { acceptanceRepository.session(familyKey) } ?: openedSession
    val allAcceptanceItemIds = remember(entry.family) {
        repository.entries(entry.family, TechnicalSection.ACCEPTANCE, "")
            .filter { it.sequence.isEmpty() }
            .map(TechnicalEntry::id)
            .distinct()
    }
    val visibleItems = checklistItems.filter { (_, item) ->
        checklistQuery.isBlank() || technicalEntryTitle(item).contains(checklistQuery, ignoreCase = true) ||
            technicalEntrySubtitle(item)?.contains(checklistQuery, ignoreCase = true) == true
    }

    fun updateItemState(itemId: String, option: AcceptanceCheckState) {
        acceptanceRepository.setState(itemId, option)
        val nextStates = activeSequence.map { id ->
            if (id == itemId) option else acceptanceRepository.state(id)
        }
        acceptanceRepository.updateSession(familyKey, acceptanceSummary(nextStates).complete)
        stateVersion++
    }

    fun saveItemNote(itemId: String, text: String) {
        acceptanceRepository.setNote(itemId, text)
        updateItemState(itemId, AcceptanceCheckState.NOTE)
    }

    fun disableItem(itemId: String) {
        acceptanceRepository.setDisabled(familyKey, itemId, true)
        val nextStates = activeSequence.filterNot { it == itemId }.map(acceptanceRepository::state)
        acceptanceRepository.updateSession(familyKey, acceptanceSummary(nextStates).complete)
        settingsVersion++
        stateVersion++
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, accent.copy(alpha = .45f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                "Активно: ${activeSequence.size} из ${entry.sequence.size} • Отключено: ${disabledInRoute.size}",
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${summary.checked + summary.notes + summary.notApplicable} из ${summary.total} активных пунктов обработано",
                color = accent
            )
            if (summary.total > 0) {
                LinearProgressIndicator(
                    progress = (summary.total - summary.notChecked).toFloat() / summary.total,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "Сеанс начат ${formatAcceptanceTime(session.startedAtMillis)} • изменения сохраняются на устройстве",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (activeSequence.isEmpty()) {
                Text(
                    "Все шаги этой приёмки отключены. Вернитесь к списку и откройте раздел «Отключено».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (showFullChecklist) {
                OutlinedTextField(
                    value = checklistQuery,
                    onValueChange = {
                        checklistQuery = it
                        visibleLimit = 30
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Поиск по названию") }
                )
                visibleItems.take(visibleLimit).forEach { (id, item) ->
                    val itemState = stateVersion.let { acceptanceRepository.state(id) }
                    val itemNote = stateVersion.let { acceptanceRepository.note(id) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                technicalEntryTitle(item),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            technicalEntrySubtitle(item)?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AcceptanceStatusSelector(
                                itemId = id,
                                itemTitle = technicalEntryTitle(item),
                                currentState = itemState,
                                note = itemNote,
                                onSelect = { option -> updateItemState(id, option) },
                                onSaveNote = { text -> saveItemNote(id, text) }
                            )
                            TextButton(
                                onClick = {
                                    disableItem(id)
                                }
                            ) { Text("Отключить шаг") }
                        }
                    }
                }
                if (visibleItems.isEmpty()) {
                    Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (visibleItems.size > visibleLimit) {
                    OutlinedButton(
                        onClick = { visibleLimit = (visibleLimit + 30).coerceAtMost(visibleItems.size) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Показать ещё (${visibleItems.size - visibleLimit})")
                    }
                }
                AcceptanceResultCard(entry.family.title, entry.title, session, summary, savedNotes)
                OutlinedButton(
                    onClick = { showFullChecklist = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Вернуться к пошаговой проверке") }
            } else {
                Text("Шаг ${effectiveStep + 1} из ${activeSequence.size}", color = accent)
                Text(
                    target?.let(::technicalEntryTitle) ?: "Пункт приёмки",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                target?.let { item ->
                    technicalEntrySubtitle(item)?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item.blocks.forEach { block ->
                        val lines = repository.displayLines(block.lines)
                            .mapNotNull(::technicalPresentationLine)
                            .distinct()
                        if (lines.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = technicalBlockContainer(item.section, block.title)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(block.title, fontWeight = FontWeight.Black)
                                    lines.forEach { Text("• $it") }
                                }
                            }
                        }
                    }
                    val activeId = currentId ?: return@let
                    AcceptanceStatusSelector(
                        itemId = activeId,
                        itemTitle = technicalEntryTitle(item),
                        currentState = currentState,
                        note = currentNote,
                        onSelect = { option -> updateItemState(activeId, option) },
                        onSaveNote = { text -> saveItemNote(activeId, text) }
                    )
                    OutlinedButton(
                        onClick = {
                            disableItem(activeId)
                            if (effectiveStep >= activeSequence.lastIndex && effectiveStep > 0) {
                                step = effectiveStep - 1
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Отключить этот шаг") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (effectiveStep > 0) step = effectiveStep - 1 },
                        enabled = effectiveStep > 0
                    ) { Text("Назад") }
                    Button(
                        onClick = { if (effectiveStep < activeSequence.lastIndex) step = effectiveStep + 1 },
                        enabled = effectiveStep < activeSequence.lastIndex && currentState != AcceptanceCheckState.NOT_CHECKED
                    ) { Text("Далее") }
                }
                OutlinedButton(
                    onClick = {
                        showFullChecklist = true
                        visibleLimit = 30
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Открыть полную карточку") }
                if (summary.complete) {
                    AcceptanceResultCard(entry.family.title, entry.title, session, summary, savedNotes)
                }
            }
            TextButton(
                onClick = { resetConfirmationVisible = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Начать новую приёмку") }
        }
    }
    if (resetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmationVisible = false },
            title = { Text("Начать новую приёмку?", fontWeight = FontWeight.Black) },
            text = {
                Text("Отметки и замечания текущего сеанса ${entry.family.title} будут удалены. Настройки отключённых шагов сохранятся.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        acceptanceRepository.resetSession(familyKey, allAcceptanceItemIds)
                        acceptanceRepository.startOrContinueSession(familyKey, entry.id, entry.title)
                        step = 0
                        showFullChecklist = false
                        checklistQuery = ""
                        stateVersion++
                        resetConfirmationVisible = false
                    }
                ) { Text("Начать заново") }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmationVisible = false }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcceptanceStatusSelector(
    itemId: String,
    itemTitle: String,
    currentState: AcceptanceCheckState,
    note: String,
    onSelect: (AcceptanceCheckState) -> Unit,
    onSaveNote: (String) -> Unit
) {
    var noteEditorVisible by rememberSaveable(itemId) { mutableStateOf(false) }
    var draftNote by rememberSaveable(itemId) { mutableStateOf(note) }

    Text("Статус: ${currentState.label}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text(itemTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AcceptanceCheckState.entries.forEach { option ->
  FilterChip(
      selected = currentState == option,
      onClick = {
          if (option == AcceptanceCheckState.NOTE) {
              draftNote = note
              noteEditorVisible = true
          } else {
              onSelect(option)
          }
      },
      label = { Text(option.label) }
  )
        }
    }

    if (note.isNotBlank()) {
        Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
  shape = RoundedCornerShape(12.dp)
        ) {
  Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Сохранённое замечание", fontWeight = FontWeight.Black)
      Text(note)
      TextButton(onClick = {
          draftNote = note
          noteEditorVisible = true
      }) { Text("Изменить замечание") }
  }
        }
    } else if (currentState == AcceptanceCheckState.NOTE) {
        Text(
  "Текст замечания не записан. Нажмите «Замечание», чтобы добавить его.",
  color = MaterialTheme.colorScheme.error,
  style = MaterialTheme.typography.bodySmall
        )
    }

    if (noteEditorVisible) {
        AlertDialog(
  onDismissRequest = { noteEditorVisible = false },
  title = { Text("Замечание: $itemTitle", fontWeight = FontWeight.Black) },
  text = {
      OutlinedTextField(
          value = draftNote,
          onValueChange = { draftNote = it },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
          maxLines = 7,
          label = { Text("Текст замечания") },
          placeholder = { Text("Что обнаружено при проверке") }
      )
  },
  confirmButton = {
      TextButton(
          onClick = {
              onSaveNote(draftNote.trim())
              noteEditorVisible = false
          },
          enabled = draftNote.isNotBlank()
      ) { Text("Сохранить") }
  },
  dismissButton = {
      TextButton(onClick = { noteEditorVisible = false }) { Text("Отмена") }
  }
        )
    }
}

@Composable
private fun AcceptanceResultCard(
    familyTitle: String,
    routeTitle: String,
    session: ru.railbrake.calculator.data.AcceptanceSession,
    summary: ru.railbrake.calculator.data.AcceptanceSummary,
    notes: List<Pair<String, String>> = emptyList()
) {
    val context = LocalContext.current
    val report = acceptanceReportText(
        familyTitle = familyTitle,
        routeTitle = routeTitle,
        startedAt = formatAcceptanceTime(session.startedAtMillis),
        updatedAt = formatAcceptanceTime(session.updatedAtMillis),
        summary = summary,
        notes = notes
    )
    val tone = if (summary.complete && summary.notes == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    InfoCard(
        summary.result,
        listOf(
  "Проверено: ${summary.checked}",
  "Замечания: ${summary.notes}",
  "Не применяется: ${summary.notApplicable}",
  "Не проверено: ${summary.notChecked}"
        ),
        tone
    )
    OutlinedButton(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Отчёт о приёмке", report))
            Toast.makeText(context, "Отчёт скопирован", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Скопировать отчёт") }
    if (notes.isNotEmpty()) {
        Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
  shape = RoundedCornerShape(14.dp)
        ) {
  Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Сохранённые замечания", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
      notes.forEach { (title, note) ->
          Text(title, fontWeight = FontWeight.Bold)
          Text(note.ifBlank { "Без описания" })
          HorizontalDivider()
      }
  }
        }
    }
}

private fun formatAcceptanceTime(timestampMillis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestampMillis))

@Composable
private fun technicalBlockContainer(section: TechnicalSection,title:String):Color=when {
    title.contains("Опас",true)->MaterialTheme.colorScheme.errorContainer
    title.contains("Неисправ",true)->MaterialTheme.colorScheme.secondaryContainer
    title.contains("Нормаль",true)->MaterialTheme.colorScheme.primaryContainer
    title.contains("Провер",true)->MaterialTheme.colorScheme.tertiaryContainer
    else->technicalSectionContainer(section)
}

@Composable
private fun technicalSectionContainer(section:TechnicalSection):Color =
    MaterialTheme.colorScheme.primaryContainer

@Composable
private fun technicalSectionAccent(section: TechnicalSection, status: String): Color {
    if (status.equals("STOP_AND_REPORT", true) || status.equals("RESTRICT_OPERATION", true)) {
        return MaterialTheme.colorScheme.error
    }
    return MaterialTheme.colorScheme.primary
}

internal fun technicalStatusLabel(status: String): String? = technicalStatusPresentation(status)
