package ru.railbrake.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.DiagnosticApplicability
import ru.railbrake.calculator.core.DiagnosticLocomotiveFamily
import ru.railbrake.calculator.core.DiagnosticProfileContext
import ru.railbrake.calculator.core.ErmakAtlasProfile
import ru.railbrake.calculator.core.ErmakBrakeProfile
import ru.railbrake.calculator.core.ErmakControlSystemProfile
import ru.railbrake.calculator.core.ErmakMotorAxleBearingProfile
import ru.railbrake.calculator.core.ErmakSafetySystemProfile
import ru.railbrake.calculator.core.ErmakSectionProfile
import ru.railbrake.calculator.core.ErmakTractionRegulationProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ErmakProfileContextCard(
    profile: DiagnosticProfileContext,
    applicability: DiagnosticApplicability,
    onChange: (DiagnosticProfileContext) -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val policyContext = profile.toPolicyContext(applicability)
    val validationIssue = profile.validationIssue()
    val effectiveConfirmed = profile.confirmed && profile.confirmedPolicyIds().isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Профиль исполнения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                if (effectiveConfirmed) "Подтверждён пользователем" else "Не подтверждён",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(profileSummary(profile), style = MaterialTheme.typography.bodySmall)
            if (effectiveConfirmed) {
                Text(
                    if (policyContext.profileConfirmed) {
                        "Для текущего сценария найден совместимый подтверждённый профиль."
                    } else {
                        "Текущий сценарий требует другого или более точного профиля; профильное действие остаётся скрытым."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            validationIssue?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Свернуть профиль" else "Проверить / изменить профиль")
            }

            if (expanded) {
                ProfileChoiceRow(
                    title = "Модель",
                    selected = profile.family,
                    options = listOf(
                        DiagnosticLocomotiveFamily.ERMAK_2ES5K to "2ЭС5К",
                        DiagnosticLocomotiveFamily.ERMAK_3ES5K to "3ЭС5К"
                    ),
                    onSelect = { onChange(profile.copy(family = it, confirmed = false)) }
                )
                ProfileChoiceRow(
                    title = "Профиль атласа",
                    selected = profile.ermakAtlasProfile,
                    options = listOf(ErmakAtlasProfile.BASE_EARLY to "Базовое раннее"),
                    onSelect = { onChange(profile.copy(ermakAtlasProfile = it, confirmed = false)) }
                )
                ProfileChoiceRow(
                    title = "Секция",
                    selected = profile.ermakSection,
                    options = listOf(
                        ErmakSectionProfile.HEAD to "Головная",
                        ErmakSectionProfile.BOOSTER to "Бустерная"
                    ),
                    onSelect = { onChange(profile.copy(ermakSection = it, confirmed = false)) }
                )
                ProfileChoiceRow(
                    title = "МСУД",
                    selected = profile.ermakControlSystem,
                    options = listOf(
                        ErmakControlSystemProfile.MSUD_N to "МСУД-Н",
                        ErmakControlSystemProfile.MSUD_015 to "МСУД-015"
                    ),
                    onSelect = { onChange(profile.copy(ermakControlSystem = it, confirmed = false)) }
                )
                ProfileChoiceRow(
                    title = "Регулирование тяги",
                    selected = profile.ermakTractionRegulation,
                    options = listOf(
                        ErmakTractionRegulationProfile.GROUP to "Групповое",
                        ErmakTractionRegulationProfile.AXLE to "Поосное"
                    ),
                    onSelect = { onChange(profile.copy(ermakTractionRegulation = it, confirmed = false)) }
                )
                ProfileChoiceRow(
                    title = "Тормозное оборудование",
                    selected = profile.ermakBrakeProfile,
                    options = listOf(
                        ErmakBrakeProfile.CRANE_395 to "№395",
                        ErmakBrakeProfile.CRANE_130_UKTOL to "№130 + УКТОЛ",
                        ErmakBrakeProfile.CRANE_130_2 to "№130-2"
                    ),
                    onSelect = { onChange(profile.copy(ermakBrakeProfile = it, confirmed = false)) }
                )
                ProfileChoiceRow(
                    title = "Комплекс безопасности",
                    selected = profile.ermakSafetySystemProfile,
                    options = listOf(
                        ErmakSafetySystemProfile.KLUB_U_SAUT_TSKBM to "КЛУБ-У / САУТ / ТСКБМ",
                        ErmakSafetySystemProfile.BLOK_2ES5K to "БЛОК / БЛОК-М (2ЭС5К)"
                    ),
                    onSelect = { onChange(profile.copy(ermakSafetySystemProfile = it, confirmed = false)) }
                )
                ProfileChoiceRow(
                    title = "Моторно-осевые подшипники",
                    selected = profile.ermakMotorAxleBearing,
                    options = listOf(
                        ErmakMotorAxleBearingProfile.SLIDING to "Скольжения",
                        ErmakMotorAxleBearingProfile.ROLLING to "Качения"
                    ),
                    onSelect = { onChange(profile.copy(ermakMotorAxleBearing = it, confirmed = false)) }
                )
                OutlinedTextField(
                    value = profile.fireSuppressionProfileId.orEmpty(),
                    onValueChange = { onChange(profile.copy(fireSuppressionProfileId = it, confirmed = false)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Профиль пожаротушения — ID из источника") },
                    singleLine = true
                )
                Text(
                    "ID пожаротушения оставьте пустым, если точный профиль не подтверждён. Само это поле не открывает профильные действия.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Неизвестные значения не подставляются автоматически. Любое изменение снимает подтверждение.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirm, enabled = profile.canConfirm()) {
                        Text("Подтвердить")
                    }
                    TextButton(onClick = onReset) { Text("Сбросить") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ProfileChoiceRow(
    title: String,
    selected: T?,
    options: List<Pair<T, String>>,
    onSelect: (T?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Не подтверждено") }
            )
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

private fun profileSummary(profile: DiagnosticProfileContext): String {
    val parts = buildList {
        when (profile.family) {
            DiagnosticLocomotiveFamily.ERMAK_2ES5K -> add("2ЭС5К")
            DiagnosticLocomotiveFamily.ERMAK_3ES5K -> add("3ЭС5К")
            DiagnosticLocomotiveFamily.VL80S -> add("ВЛ80С")
            null -> Unit
        }
        profile.ermakAtlasProfile?.let { add("базовое раннее") }
        profile.ermakSection?.let { add(if (it == ErmakSectionProfile.HEAD) "головная секция" else "бустерная секция") }
        profile.ermakControlSystem?.let { add(if (it == ErmakControlSystemProfile.MSUD_N) "МСУД-Н" else "МСУД-015") }
        profile.ermakTractionRegulation?.let { add(if (it == ErmakTractionRegulationProfile.GROUP) "групповое регулирование" else "поосное регулирование") }
        profile.ermakBrakeProfile?.let {
            add(
                when (it) {
                    ErmakBrakeProfile.CRANE_395 -> "кран №395"
                    ErmakBrakeProfile.CRANE_130_UKTOL -> "№130 + УКТОЛ"
                    ErmakBrakeProfile.CRANE_130_2 -> "№130-2"
                }
            )
        }
        profile.ermakSafetySystemProfile?.let {
            add(if (it == ErmakSafetySystemProfile.BLOK_2ES5K) "БЛОК/БЛОК-М" else "КЛУБ-У/САУТ/ТСКБМ")
        }
        profile.ermakMotorAxleBearing?.let { add(if (it == ErmakMotorAxleBearingProfile.SLIDING) "МОП скольжения" else "МОП качения") }
        if (profile.hasSourceFireProfile()) add("пожаротушение: ${profile.fireSuppressionProfileId?.trim()}")
    }
    return parts.joinToString(" • ").ifBlank { "Исполнение не задано" }
}
