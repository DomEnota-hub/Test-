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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.ExtendedEmergencyRuntimeRepository
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.expandedEmergencyProfileId
import ru.railbrake.calculator.data.ExtendedEmergencyModeRepository
import ru.railbrake.calculator.ui.theme.RailTheme

@Composable
internal fun ExtendedEmergencyEvidenceSection(
    standardScenarioId: String,
    family: TechnicalFamily
) {
    val context = LocalContext.current
    val modeRepository = remember(context) { ExtendedEmergencyModeRepository(context) }
    val runtimeRepository = remember(context) { ExtendedEmergencyRuntimeRepository(context) }
    val profileId = expandedEmergencyProfileId(family)
    val modeEnabled = modeRepository.isEnabled()
    val evidence = remember(standardScenarioId, family, profileId, modeEnabled) {
        runtimeRepository.evidenceFor(
            standardScenarioId = standardScenarioId,
            profileId = profileId,
            modeEnabled = modeEnabled
        )
    }

    if (evidence.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        evidence.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RailTheme.colors.extendedEmergencyContainer),
                border = BorderStroke(2.dp, RailTheme.colors.extendedEmergencyBorder),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        ExtendedEmergencyModeRepository.BADGE,
                        color = RailTheme.colors.extendedEmergency,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(item.dispositionLabel, fontWeight = FontWeight.Bold)
                    Text(item.riskLabel, style = MaterialTheme.typography.bodySmall)
                    Text(item.summary)
                    Text(item.terminal, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (item.prohibited) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                "Процедура выполнения намеренно скрыта. Эта запись не является разрешением обходить защиту, принудительно включать аппарат или выполнять нестандартное вмешательство.",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text("Источники", fontWeight = FontWeight.Black)
                    item.sources.forEach { source ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(source.title, fontWeight = FontWeight.Bold)
                            Text(source.provenanceLabel, style = MaterialTheme.typography.bodySmall)
                            Text(
                                source.statusLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
