package ru.railbrake.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Visual navigation stub only. Work-records storage, acceptance archives, media and diagnostic
 * drafts are NOT implemented by this screen. The existing calculation history remains usable.
 */
@Composable
internal fun WorkRecordsLanding(onHistory: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Рабочие записи", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Общий раздел для замечаний, расчётов, диагностики и личных заметок",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        WorkRecordsFutureCategory(
            title = "Замечания при приёмке",
            description = "Открытые замечания и архив приёмки"
        )
        RailNavCard(
            title = "История расчётов",
            subtitle = "Открыть существующие сохранённые результаты",
            marker = "▦",
            onClick = onHistory,
            modifier = Modifier.fillMaxWidth()
        )
        WorkRecordsFutureCategory(
            title = "История диагностики",
            description = "Завершённые отчёты и черновики сценариев"
        )
        WorkRecordsFutureCategory(
            title = "Личные заметки",
            description = "Заметки, вложения и теги"
        )

        Text(
            "В этой итерации работает переход в историю расчётов. Остальные категории пока " +
                "представлены только в дизайне и будут подключаться к хранилищу отдельно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkRecordsFutureCategory(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "В разработке",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
