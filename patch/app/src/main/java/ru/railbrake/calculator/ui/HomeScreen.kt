package ru.railbrake.calculator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.Vl80sObservationCatalog
import ru.railbrake.calculator.data.HistoryRecord

@Composable
internal fun HomeScreen(
    latestHistory: HistoryRecord?,
    onDiagnostics: () -> Unit,
    onKnowledge: () -> Unit,
    onLocomotives: () -> Unit,
    onCalculations: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ВЛ80С", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Рабочий профиль • основная база доступна офлайн", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RailStatusPill("ОФЛАЙН")
        }

        RailInfoBand(
            "${DiagnosticRepository.scenarios.size} диагностических сценария • ${Vl80sObservationCatalog.equipment.size} узлов оборудования"
        )

        RailHeroCard(
            title = "Диагностика ВЛ80С",
            subtitle = "Поиск неисправности по наблюдаемым признакам, ветвящиеся уточнения и безопасные проверки.",
            action = "НАЧАТЬ ДИАГНОСТИКУ  →",
            onClick = onDiagnostics
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RailNavCard(
                title = "Справочник",
                subtitle = "Статьи, нормы и связанные материалы",
                marker = "▤",
                onClick = onKnowledge,
                modifier = Modifier.weight(1f)
            )
            RailNavCard(
                title = "Локомотив / атлас",
                subtitle = "Оборудование, расположение и схемы",
                marker = "⌁",
                onClick = onLocomotives,
                modifier = Modifier.weight(1f)
            )
        }

        RailSectionHeader("Продолжить работу", "Последнее локальное действие")
        if (latestHistory != null) {
            RailNavCard(
                title = latestHistory.title,
                subtitle = "${latestHistory.mode}: ${latestHistory.summary}",
                marker = "↺",
                onClick = onCalculations,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                "История пока пуста. После первого расчёта здесь появится быстрый переход.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}
