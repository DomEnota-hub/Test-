package ru.railbrake.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        AssistantHomePanel()

        RailHeroCard(
            title = "Диагностика",
            subtitle = "ВЛ80С и Ермак: поиск неисправности по наблюдаемым признакам и безопасные проверки.",
            action = "НАЧАТЬ ДИАГНОСТИКУ  →",
            onClick = onDiagnostics
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HandbookNavCard(
                onClick = onKnowledge,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            RailNavCard(
                title = "Локомотив / атлас",
                subtitle = "ВЛ80С и Ермак: оборудование, статьи и схемы",
                marker = "⚡",
                onClick = onLocomotives,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        RailSectionHeader("Последний расчёт", "Переход открывает раздел расчётов")
        if (latestHistory != null) {
            RailNavCard(
                title = "Открыть расчёты",
                subtitle = "Последнее: ${latestHistory.mode} • ${latestHistory.title} • ${latestHistory.summary}",
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

@Composable
private fun HandbookNavCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            HandbookIcon()
            Text("База знаний", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                "Тормоза, сигналы, нормы и общие материалы",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HandbookIcon() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(width = 25.dp, height = 20.dp)) {
        val stroke = Stroke(width = 2.2f)
        val centerX = size.width / 2f
        val top = 1.5f
        val bottom = size.height - 1.5f
        val outer = 1.5f
        val gutter = 1.8f

        drawLine(color, Offset(centerX - gutter, top + 1f), Offset(centerX - gutter, bottom), strokeWidth = stroke.width)
        drawLine(color, Offset(centerX + gutter, top + 1f), Offset(centerX + gutter, bottom), strokeWidth = stroke.width)

        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX - gutter, top + 1f)
                quadraticBezierTo(centerX * 0.48f, top - 1f, outer, top + 2.5f)
                lineTo(outer, bottom - 1f)
                quadraticBezierTo(centerX * 0.48f, bottom - 3f, centerX - gutter, bottom)
            },
            color = color,
            style = stroke
        )
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX + gutter, top + 1f)
                quadraticBezierTo(size.width - centerX * 0.48f, top - 1f, size.width - outer, top + 2.5f)
                lineTo(size.width - outer, bottom - 1f)
                quadraticBezierTo(size.width - centerX * 0.48f, bottom - 3f, centerX + gutter, bottom)
            },
            color = color,
            style = stroke
        )

        val leftStart = outer + 3.5f
        val leftEnd = centerX - gutter - 3f
        val rightStart = centerX + gutter + 3f
        val rightEnd = size.width - outer - 3.5f
        listOf(7f, 11f).forEach { y ->
            drawLine(color, Offset(leftStart, y), Offset(leftEnd, y), strokeWidth = 1.4f)
            drawLine(color, Offset(rightStart, y), Offset(rightEnd, y), strokeWidth = 1.4f)
        }
    }
}
