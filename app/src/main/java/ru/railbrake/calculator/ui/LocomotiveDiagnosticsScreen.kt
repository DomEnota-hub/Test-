package ru.railbrake.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.TechnicalFamily

@Composable
fun LocomotiveDiagnosticsScreen(
    initialScenarioId: String? = null,
    initialEquipmentId: String? = null,
    workingFamily: TechnicalFamily? = null
) {
    val linkedFamily = if (initialScenarioId != null || initialEquipmentId != null)
        diagnosticInitialFamily(initialScenarioId, initialEquipmentId) else null
    var familyName by rememberSaveable(workingFamily, initialScenarioId, initialEquipmentId) {
        mutableStateOf((linkedFamily ?: workingFamily)?.name.orEmpty())
    }
    val family = TechnicalFamily.entries.firstOrNull { it.name == familyName }
    Column(Modifier.fillMaxSize()) {
        if (family == null) {
            Text("Выберите серию для диагностики. Это разовый просмотр; рабочий локомотив не изменится.",
                modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(family == TechnicalFamily.VL80S, { familyName = TechnicalFamily.VL80S.name },
                enabled = workingFamily == null || workingFamily == TechnicalFamily.VL80S,
                label = { Text("ВЛ80С") })
            FilterChip(family == TechnicalFamily.ERMAK, { familyName = TechnicalFamily.ERMAK.name },
                enabled = workingFamily == null || workingFamily == TechnicalFamily.ERMAK,
                label = { Text("Ермак") })
        }
        if (workingFamily != null && family != null && family != workingFamily) {
            Text("Материал другой серии. Рабочий локомотив не изменён.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        if (family != null) Box(Modifier.fillMaxWidth().weight(1f)) {
            if (family == TechnicalFamily.VL80S)
                DiagnosticScreen(diagnosticScenarioForFamily(initialScenarioId, family),
                    diagnosticEquipmentForFamily(initialEquipmentId, family))
            else ErmakDiagnosticsScreen(
                initialScenarioId = diagnosticScenarioForFamily(initialScenarioId, family),
                initialEquipmentId = diagnosticEquipmentForFamily(initialEquipmentId, family)
            )
        }
    }
}

internal fun diagnosticInitialFamily(scenarioId: String?, equipmentId: String?): TechnicalFamily =
    if (scenarioId?.startsWith("ER-DIAG-") == true || equipmentId?.startsWith("ER-EQ-") == true)
        TechnicalFamily.ERMAK else TechnicalFamily.VL80S

internal fun diagnosticScenarioForFamily(id: String?, family: TechnicalFamily): String? = when (family) {
    TechnicalFamily.VL80S -> id?.takeUnless { it.startsWith("ER-") }
    TechnicalFamily.ERMAK -> id?.takeIf { it.startsWith("ER-DIAG-") }
}

internal fun diagnosticEquipmentForFamily(id: String?, family: TechnicalFamily): String? = when (family) {
    TechnicalFamily.VL80S -> id?.takeUnless { it.startsWith("ER-") }
    TechnicalFamily.ERMAK -> id?.takeIf { it.startsWith("ER-EQ-") }
}
