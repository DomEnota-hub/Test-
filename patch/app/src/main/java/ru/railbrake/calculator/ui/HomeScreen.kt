package ru.railbrake.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.LocomotiveCatalogRegistry
import ru.railbrake.calculator.core.WorkingLocomotive
import ru.railbrake.calculator.data.HistoryRecord

/**
 * Second native Compose concept: the approved hierarchy is unchanged.
 * Only presentation changes. All callbacks route into the existing Framework v2 engine.
 */
@Composable
internal fun HomeScreen(
    latestHistory: HistoryRecord?,
    workingLocomotive: WorkingLocomotive?,
    onWorkingLocomotiveChange: (WorkingLocomotive?) -> Unit,
    onDiagnostics: () -> Unit,
    onKnowledge: () -> Unit,
    onLocomotives: () -> Unit,
    onCalculations: () -> Unit,
    onWorkRecords: () -> Unit
) {
    var selectorOpen by remember { mutableStateOf(false) }
    if (selectorOpen) {
        WorkingLocomotivePicker(
            selected = workingLocomotive,
            onSelect = {
                onWorkingLocomotiveChange(it)
                selectorOpen = false
            },
            onDismiss = { selectorOpen = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        WorkingSeriesPanel(
            title = workingLocomotive?.title ?: "Не выбран",
            onClick = { selectorOpen = true }
        )

        // The original AssistantRuntime and voice pipeline remain active.
        key(workingLocomotive?.name) {
            AssistantHomePanel(workingLocomotive)
        }

        SectionNavigationPanel(
            title = "Расчёты",
            description = "По массе · ИДП №12",
            motif = TechnicalMotif.CALCULATOR,
            accent = DesignAmber,
            prominent = true,
            onClick = onCalculations,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionNavigationPanel(
                title = "Диагностика",
                description = "Поиск неисправностей",
                motif = TechnicalMotif.EQUIPMENT,
                accent = DesignRose,
                compact = true,
                onClick = onDiagnostics,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            SectionNavigationPanel(
                title = "Атлас",
                description = "Оборудование и схемы",
                motif = TechnicalMotif.CIRCUIT,
                accent = DesignCyan,
                compact = true,
                onClick = onLocomotives,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        SectionNavigationPanel(
            title = "Рабочие записи",
            description = "Замечания, история и личные заметки",
            motif = TechnicalMotif.NOTES,
            accent = DesignViolet,
            modifier = Modifier.fillMaxWidth(),
            onClick = onWorkRecords
        )

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun WorkingSeriesPanel(title: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    TechnicalPanel(
        accent = DesignBlue,
        motif = TechnicalMotif.TRAIN,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            TechnicalIcon(TechnicalMotif.TRAIN, DesignBlue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "РАБОЧИЙ ЛОКОМОТИВ",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", color = scheme.primary, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun SectionNavigationPanel(
    title: String,
    description: String,
    motif: TechnicalMotif,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    prominent: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    TechnicalPanel(
        accent = accent,
        motif = motif,
        prominent = prominent,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 133.dp).padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TechnicalIcon(motif, accent)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 3
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = if (prominent) 111.dp else 84.dp)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TechnicalIcon(motif, accent)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (prominent) {
                        Text(
                            "ОТКРЫТЬ РАСЧЁТЫ  →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
                if (!prominent) {
                    Text("›", color = accent, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Composable
private fun WorkingLocomotivePicker(
    selected: WorkingLocomotive?,
    onSelect: (WorkingLocomotive?) -> Unit,
    onDismiss: () -> Unit
) {
    val options = LocomotiveCatalogRegistry.workingOptions
    val groups = listOf(
        "Электровозы" to options.filter { it == WorkingLocomotive.VL80S || it == WorkingLocomotive.ERMAK_2ES5K || it == WorkingLocomotive.ERMAK_3ES5K },
        "Тепловозы ЧМЭ3" to options.filter { it == WorkingLocomotive.CHME3 || it == WorkingLocomotive.CHME3T || it == WorkingLocomotive.CHME3E },
        "Тепловозы ТЭМ2" to options.filter { it == WorkingLocomotive.TEM2 || it == WorkingLocomotive.TEM2U }
    ).let { established ->
        // Future profiles added to the Test registry must remain selectable.
        val known = established.flatMap { it.second }.toSet()
        val others = options.filterNot { it in known }
        if (others.isEmpty()) established else established + ("Другие серии" to others)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Рабочий локомотив") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == null, onClick = { onSelect(null) })
                    Text("Все материалы", modifier = Modifier.padding(start = 8.dp))
                }
                groups.forEach { (name, entries) ->
                    if (entries.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 5.dp)
                        )
                        entries.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(option) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option == selected,
                                    onClick = { onSelect(option) }
                                )
                                Text(option.title, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
