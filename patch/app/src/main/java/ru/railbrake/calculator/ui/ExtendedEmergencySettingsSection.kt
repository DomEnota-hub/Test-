package ru.railbrake.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.data.ExtendedEmergencyModeRepository
import ru.railbrake.calculator.ui.theme.RailTheme

@Composable
internal fun ExtendedEmergencySettingsSection() {
    val context = LocalContext.current
    val repository = remember(context) { ExtendedEmergencyModeRepository(context) }
    var enabled by remember { mutableStateOf(repository.isEnabled()) }
    var showEnableWarning by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Расширенные аварийные приёмы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Архивные, заводские и полевые методы — включаются отдельно",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (enabled) "Расширенный режим включён" else "Расширенный режим выключен",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (enabled)
                            "Помимо стандартных маршрутов могут показываться явно маркированные расширенные ветви."
                        else
                            "Стандартная диагностика не показывает расширенные ветви.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { requested ->
                        if (!requested) {
                            repository.disable()
                            enabled = false
                        } else if (repository.hasAcknowledgedWarning()) {
                            repository.enablePreviouslyAcknowledged()
                            enabled = true
                        } else {
                            showEnableWarning = true
                        }
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = RailTheme.colors.extendedEmergencyContainer,
                border = BorderStroke(2.dp, RailTheme.colors.extendedEmergencyBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        ExtendedEmergencyModeRepository.BADGE,
                        color = RailTheme.colors.extendedEmergency,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Такие материалы всегда выделяются бирюзовой рамкой и этой текстовой пометкой. Цвет акцента приложения на обозначение не влияет.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showEnableWarning) {
        val warningScrollState = rememberScrollState()
        val warningHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
        val reachedWarningEnd by remember(warningScrollState) {
            derivedStateOf {
                warningScrollState.maxValue != Int.MAX_VALUE &&
                    warningScrollState.value >= warningScrollState.maxValue
            }
        }
        AlertDialog(
            onDismissRequest = { showEnableWarning = false },
            title = { Text("Расширенные аварийные приёмы") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier.heightIn(max = warningHeight).verticalScroll(warningScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "При включении приложение сможет предлагать дополнительные способы диагностики и действий, которые могут отсутствовать в действующих нормативных документах. Они могут быть взяты из архивных инструкций, документации изготовителя, учебных материалов или описанной практики работников."
                        )
                        Text(
                            "Расширенные сценарии будут выделяться бирюзовой рамкой и пометкой «Расширенный сценарий». Внутри сценария также будет указан источник и его статус.",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Наличие такого сценария в приложении не означает, что описанное действие разрешено действующими инструкциями или применимо к конкретному исполнению локомотива. Необходимо учитывать фактическое оборудование, действующие нормативы и требования безопасности."
                        )
                        Text(
                            "После подтверждения это общее предупреждение не будет повторяться при открытии каждого расширенного сценария. Опасные или запрещённые действия внутри сценария сохраняют отдельную красную маркировку."
                        )
                    }
                    if (!reachedWarningEnd) {
                        Text(
                            "Пролистайте предупреждение до конца, чтобы включить режим.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reachedWarningEnd,
                    onClick = {
                        if (reachedWarningEnd) {
                            repository.enableAfterAcknowledgement()
                            enabled = true
                            showEnableWarning = false
                        }
                    }
                ) {
                    Text("Включить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableWarning = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
