package ru.railbrake.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.LocomotiveCatalogRegistry
import ru.railbrake.calculator.core.WorkingLocomotive
import ru.railbrake.calculator.data.HistoryRecord

/**
 * Design iteration 01. Keeps the framework-v2 Assistant runtime, navigation handlers and
 * locomotive selection repository intact. Cards are visual navigation, not mock calculations.
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.055f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeLocomotiveCard(
            title = workingLocomotive?.title ?: "Не выбран",
            onClick = { selectorOpen = true }
        )

        // This is the real offline assistant; do not replace with a demo search field.
        key(workingLocomotive?.name) {
            AssistantHomePanel(workingLocomotive)
        }

        // Calculations are the main work tool, so this card gets the strongest accent.
        HomeFeatureCard(
            title = "Расчёты",
            description = "Тормозные расчёты и проверка исходных данных",
            glyph = "▦",
            accent = Color(0xFFE3A33F),
            emphasized = true,
            onClick = onCalculations,
            modifier = Modifier.fillMaxWidth()
        )

        // Pair is intentional: diagnostics must not dominate the main screen.
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeFeatureCard(
                title = "Диагностика",
                description = "Поиск неисправностей",
                glyph = "⌁",
                accent = Color(0xFFCC6974),
                onClick = onDiagnostics,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                compact = true
            )
            HomeFeatureCard(
                title = "Атлас",
                description = "Оборудование и схемы",
                glyph = "▤",
                accent = Color(0xFF32B7BF),
                onClick = onLocomotives,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                compact = true
            )
        }

        HomeFeatureCard(
            title = "Рабочие записи",
            description = "Замечания, история и личные заметки",
            glyph = "≡",
            accent = Color(0xFF957AD8),
            onClick = onWorkRecords,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HomeLocomotiveCard(title: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.42f)),
        colors = CardDefaults.cardColors(containerColor = scheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(scheme.primary.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeGlyph("▣", scheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "РАБОЧИЙ ЛОКОМОТИВ",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = scheme.primary)
        }
    }
}

@Composable
private fun HomeGlyph(symbol: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeFeatureCard(
    title: String,
    description: String,
    glyph: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(
            if (emphasized) 1.4.dp else 1.dp,
            accent.copy(alpha = if (emphasized) 0.78f else 0.38f)
        ),
        colors = CardDefaults.cardColors(containerColor = scheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = if (emphasized) 0.13f else 0.07f), Color.Transparent)
                    )
                )
                .padding(if (compact) 13.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            if (compact) {
                HomeGlyph(glyph, accent)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    minLines = 2
                )
                Text("Открыть  ›", color = accent, style = MaterialTheme.typography.labelMedium)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeGlyph(glyph, accent)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = accent)
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
