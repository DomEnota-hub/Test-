package ru.railbrake.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import ru.railbrake.calculator.core.Vu45Calculator
import ru.railbrake.calculator.data.HistoryRecord
import java.math.BigDecimal
import java.math.RoundingMode

private data class Vu45RowDraft(val quantity: String = "", val wagonAxles: Int = 4)
private const val SETTINGS_PREFS = "vu45_workbench_settings"
private val DEFAULT_FORCES = listOf("3", "3.5", "5", "7", "7.5")
private val DEFAULT_SLOPES = listOf("0.006", "0.008")

private fun BigDecimal.vu45Display(digits: Int? = null): String =
    (if (digits != null) setScale(digits, RoundingMode.HALF_UP) else stripTrailingZeros())
        .toPlainString().replace('.', ',')

private fun readableNumber(value: String): String =
    value.replace('.', ',')

private fun parseKeys(saved: String?, fallback: List<String>): List<String> =
    if (saved == null) fallback else runCatching {
        val source = JSONArray(saved)
        (0 until source.length()).map { source.getString(it) }.distinct().ifEmpty { fallback }
    }.getOrDefault(fallback)

private fun encodeKeys(values: List<String>): String =
    JSONArray(values).toString()

private fun normalizedCustom(text: String, decimals: Int, allowZero: Boolean = false): String? =
    runCatching {
        val value = text.trim().replace(',', '.').toBigDecimal()
        require(if (allowZero) value >= BigDecimal.ZERO else value > BigDecimal.ZERO)
        require(value.scale().coerceAtLeast(0) <= decimals)
        require(value.toPlainString().length <= 16)
        value.stripTrailingZeros().toPlainString()
    }.getOrNull()

private fun sortedNumbers(source: Collection<String>): List<String> =
    source.distinct().sortedWith(compareBy { it.toBigDecimalOrNull() ?: BigDecimal.ZERO })

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Vu45Screen(
    initialSnapshot: String?,
    onSaved: (HistoryRecord) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember(context) {
        context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
    }
    val restored = remember(initialSnapshot) {
        initialSnapshot?.let { runCatching { Vu45HistorySnapshots.decodeInput(it) }.getOrNull() }
    }

    var mass by remember(initialSnapshot) { mutableStateOf(restored?.trainMassTons.orEmpty()) }
    var declared by remember(initialSnapshot) { mutableStateOf(restored?.trainAxles?.toString().orEmpty()) }
    var quantityMode by remember(initialSnapshot) {
        mutableStateOf(restored?.quantityMode ?: Vu45Calculator.QuantityMode.AXLES)
    }
    var norm by remember(initialSnapshot) {
        mutableStateOf(restored?.selectedNorm ?: settings.getInt("norm", 33))
    }
    var slope by remember(initialSnapshot) {
        mutableStateOf(restored?.slope ?: settings.getString("selected_slope", "0.006")!!)
    }
    var forcePool by remember {
        mutableStateOf(parseKeys(settings.getString("press_pool", null), Vu45Calculator.availablePressValues))
    }
    var slopePool by remember {
        mutableStateOf(parseKeys(settings.getString("slope_pool", null), Vu45Calculator.availableSlopeValues))
    }
    var visibleForces by remember {
        mutableStateOf(parseKeys(settings.getString("visible_press", null), DEFAULT_FORCES))
    }
    var visibleSlopes by remember {
        mutableStateOf(parseKeys(settings.getString("visible_slopes", null), DEFAULT_SLOPES))
    }
    var rows by remember(initialSnapshot) {
        mutableStateOf(
            restored?.groups?.associate {
                val key = normalizedCustom(it.forcePerAxleTonForce, 2) ?: it.forcePerAxleTonForce
                key to Vu45RowDraft(it.brakingAxles.toString(), it.wagonAxles)
            } ?: emptyMap<String, Vu45RowDraft>()
        )
    }
    var timeII by remember(initialSnapshot) {
        mutableStateOf(restored?.densityTimeIISeconds.orEmpty())
    }
    var timeIV by remember(initialSnapshot) {
        mutableStateOf(restored?.densityTimeIVSeconds.orEmpty())
    }
    var expandedDensity by remember { mutableStateOf(false) }
    var pressDialog by remember { mutableStateOf(false) }
    var slopeDialog by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf(false) }
    var result by remember(initialSnapshot) { mutableStateOf<Vu45Calculator.Result?>(null) }
    var calculationInput by remember(initialSnapshot) { mutableStateOf<Vu45Calculator.Input?>(null) }
    var error by remember(initialSnapshot) { mutableStateOf<String?>(null) }
    var saved by remember(initialSnapshot) { mutableStateOf(false) }

    fun invalidate() {
        result = null
        calculationInput = null
        error = null
        saved = false
    }

    val rowKeys = sortedNumbers(visibleForces + rows.filter {
        it.value.quantity.toIntOrNull()?.let { n -> n > 0 } == true
    }.keys)
    val declaredNumber = declared.trim().toIntOrNull()?.takeIf { it > 0 }
    val parsedCounts = rowKeys.map { (rows[it]?.quantity ?: "").toIntOrNull() ?: 0 }
    val sumCount = parsedCounts.sumOf { it.toLong().coerceAtLeast(0L) }
    val displayedActual = rowKeys.fold(BigDecimal.ZERO) { total, force ->
        val group = rows[force] ?: Vu45RowDraft()
        val count = group.quantity.toIntOrNull() ?: 0
        val pressed = force.toBigDecimalOrNull() ?: BigDecimal.ZERO
        total + pressed * BigDecimal.valueOf(count.toLong().coerceAtLeast(0L)) *
            BigDecimal.valueOf((if (quantityMode == Vu45Calculator.QuantityMode.WAGONS) group.wagonAxles else 1).toLong())
    }
    val unitLabel = if (quantityMode == Vu45Calculator.QuantityMode.AXLES) "осей" else "вагонов"

    RailSectionHeader("ВУ-45", "Расчёт тормозного нажатия")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Данные состава", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { helpDialog = true }) { Text("Методика") }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = mass, onValueChange = { mass = it; invalidate() },
            label = { Text("Масса, т") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = declared, onValueChange = { declared = it; invalidate() },
            label = { Text("Кол-во ${if (quantityMode == Vu45Calculator.QuantityMode.AXLES) "осей" else "вагонов"}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.weight(1f)
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Vu45Calculator.QuantityMode.AXLES to "В осях",
            Vu45Calculator.QuantityMode.WAGONS to "В вагонах").forEach { (value, label) ->
            FilterChip(
                selected = quantityMode == value,
                onClick = {
                    if (quantityMode != value) {
                        quantityMode = value
                        rows = rows.mapValues { (_, item) -> item.copy(quantity = "") }
                        declared = ""
                        invalidate()
                    }
                },
                label = { Text(label) }
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Нормативное нажатие, тс/100 т", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(33, 44, 55).forEach { item ->
                    FilterChip(selected = norm == item, onClick = {
                        norm = item
                        settings.edit().putInt("norm", item).apply()
                        invalidate()
                    }, label = { Text(item.toString()) })
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Уклон", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { slopeDialog = true }) { Text("Настроить") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                sortedNumbers(visibleSlopes + slope).forEach { item ->
                    FilterChip(selected = slope.toBigDecimalOrNull() == item.toBigDecimalOrNull(),
                        onClick = {
                            slope = item
                            settings.edit().putString("selected_slope", item).apply()
                            invalidate()
                        }, label = { Text(readableNumber(item)) })
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Тормозное нажатие",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { pressDialog = true }) { Text("Строки") }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("На ось, тс", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.85f))
                Text("Кол-во", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.1f))
                Text("Всего, тс", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.05f))
            }
            HorizontalDivider()

            rowKeys.forEachIndexed { index, force ->
                val draft = rows[force] ?: Vu45RowDraft()
                val count = draft.quantity.toIntOrNull() ?: 0
                val candidate = Vu45Calculator.remainingForRow(declaredNumber, parsedCounts, index)
                val rowTotal = force.toBigDecimalOrNull()
                    ?.multiply(BigDecimal.valueOf(count.toLong().coerceAtLeast(0L)))
                    ?.multiply(BigDecimal.valueOf(
                        (if (quantityMode == Vu45Calculator.QuantityMode.WAGONS) draft.wagonAxles else 1).toLong()
                    )) ?: BigDecimal.ZERO

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(readableNumber(force), color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.85f))
                    OutlinedTextField(
                        value = draft.quantity,
                        onValueChange = {
                            rows = rows + (force to draft.copy(quantity = it))
                            invalidate()
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.1f)
                    )
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.weight(1.05f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (count == 0 && candidate != null) {
                            OutlinedButton(
                                onClick = {
                                    rows = rows + (force to draft.copy(quantity = candidate.toString()))
                                    invalidate()
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 1.dp)
                            ) { Text("← $candidate", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        } else {
                            Text(if (count == 0) "—" else rowTotal.vu45Display(),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (quantityMode == Vu45Calculator.QuantityMode.WAGONS) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Осность:", style = MaterialTheme.typography.labelSmall)
                        listOf(4, 6, 8).forEach { axes ->
                            FilterChip(
                                selected = draft.wagonAxles == axes,
                                onClick = {
                                    rows = rows + (force to draft.copy(wagonAxles = axes))
                                    invalidate()
                                },
                                label = { Text(axes.toString()) }
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Итого", modifier = Modifier.weight(0.85f), fontWeight = FontWeight.Bold)
                Text("$sumCount${declaredNumber?.let { " / $it" }.orEmpty()}",
                    modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Bold)
                Text(displayedActual.vu45Display(), modifier = Modifier.weight(1.05f),
                    fontWeight = FontWeight.Bold)
            }
            if (declaredNumber != null) {
                val diff = declaredNumber.toLong() - sumCount
                Text(
                    when {
                        diff < 0 -> "Превышено на ${-diff} $unitLabel"
                        diff > 0 -> "Осталось распределить: $diff $unitLabel"
                        else -> "Количество распределено полностью"
                    },
                    color = if (diff < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    TextButton(onClick = { expandedDensity = !expandedDensity }) {
        Text(if (expandedDensity) "Скрыть дополнительные показатели ↑" else "Дополнительные показатели ↓")
    }
    if (expandedDensity) {
        Text("Измеренное время II → IV (необязательно)", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = timeII, onValueChange = { timeII = it; invalidate() },
                label = { Text("II, с") }, modifier = Modifier.weight(1f),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = timeIV, onValueChange = { timeIV = it; invalidate() },
                label = { Text("IV, с") }, modifier = Modifier.weight(1f),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    }

    Button(onClick = {
        runCatching {
            val total = declared.trim().toIntOrNull()
                ?: throw IllegalArgumentException("Введите количество $unitLabel")
            val groups = rowKeys.mapNotNull { force ->
                val draft = rows[force] ?: Vu45RowDraft()
                val n = draft.quantity.trim().takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                if (draft.quantity.isNotBlank() && draft.quantity.trim().toIntOrNull() == null)
                    throw IllegalArgumentException("Неверное количество в строке ${readableNumber(force)}")
                if (n < 0) throw IllegalArgumentException("Количество не может быть отрицательным")
                if (n == 0) null else Vu45Calculator.AxleGroup(force, n, draft.wagonAxles)
            }
            val input = Vu45Calculator.Input(
                mode = if (norm == 55) Vu45Calculator.FreightMode.EMPTY
                    else Vu45Calculator.FreightMode.LOADED,
                trainMassTons = mass, trainAxles = total, groups = groups,
                quantityMode = quantityMode, selectedNorm = norm, slope = slope,
                densityTimeIISeconds = timeII, densityTimeIVSeconds = timeIV
            )
            val calculated = Vu45Calculator.calculate(input)
            calculationInput = input
            result = calculated
            saved = false
            error = null
        }.onFailure {
            invalidate()
            error = it.message ?: "Не удалось выполнить расчёт"
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Рассчитать") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    val computed = result
    val validInput = calculationInput
    if (computed != null && validInput != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Результат расчёта", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Vu45Metric("Требуемое нажатие", "${computed.requiredTonForce.vu45Display()} тс")
                Vu45Metric("Фактическое нажатие", "${computed.actualTonForce.vu45Display()} тс")
                Vu45Metric("На 100 т массы", "${computed.actualPer100TonsTonForce.vu45Display(1)} тс")
                Vu45Metric("Разница", "${computed.differenceTonForce.vu45Display()} тс")
                Vu45Metric("Распределено", "${computed.enteredQuantity} / ${validInput.trainAxles} $unitLabel")
                if (computed.handBrakeAxles != null) {
                    Vu45Metric("Ручных тормозных осей", computed.handBrakeAxles.toString())
                } else {
                    Text("Ручные тормозные оси: для выбранного уклона нужен уточнённый норматив.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (computed.requirementMetMathematically)
                        "Фактическое нажатие соответствует выбранному числовому нормативу."
                    else "Фактическое нажатие меньше выбранного числового норматива.",
                    color = if (computed.requirementMetMathematically)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                computed.density?.let {
                    Vu45Metric("Изменение времени II → IV",
                        "${it.timeReductionPercent.vu45Display(2)}%")
                }
            }
        }
        Button(
            enabled = !saved,
            onClick = {
                runCatching {
                    val timestamp = System.currentTimeMillis()
                    Vu45HistorySnapshots.save(context, timestamp,
                        Vu45HistorySnapshots.encode(validInput, computed, timestamp))
                    onSaved(HistoryRecord(
                        timestampMillis = timestamp, mode = "ВУ-45",
                        title = "${validInput.trainMassTons} т • ${validInput.selectedNorm} тс/100 т",
                        summary = "${computed.actualTonForce.vu45Display()} / ${computed.requiredTonForce.vu45Display()} тс",
                        details = "На 100 т: ${computed.actualPer100TonsTonForce.vu45Display(1)} тс; " +
                            "распределено ${computed.enteredQuantity} из ${validInput.trainAxles} $unitLabel; " +
                            "уклон ${readableNumber(validInput.slope)}" +
                            (computed.handBrakeAxles?.let { "; ручных осей $it" } ?: "")
                    ))
                    saved = true
                    error = null
                }.onFailure { error = it.message ?: "Не удалось сохранить расчёт" }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saved) "Сохранено" else "Сохранить в историю") }
    }
    TextButton(onClick = {
        mass = ""
        declared = ""
        rows = emptyMap()
        timeII = ""
        timeIV = ""
        invalidate()
    }, modifier = Modifier.fillMaxWidth()) { Text("Сбросить ввод") }

    if (pressDialog) {
        Vu45ChoiceDialog(
            title = "Настройка строк нажатия",
            allOptions = sortedNumbers(forcePool + visibleForces),
            initiallySelected = visibleForces,
            precision = 2,
            onClose = { pressDialog = false },
            onSave = { options, selected ->
                forcePool = options
                visibleForces = selected
                settings.edit().putString("press_pool", encodeKeys(options))
                    .putString("visible_press", encodeKeys(selected)).apply()
                pressDialog = false
                invalidate()
            }
        )
    }
    if (slopeDialog) {
        Vu45ChoiceDialog(
            title = "Настройка уклонов",
            allOptions = sortedNumbers(slopePool + visibleSlopes + slope),
            initiallySelected = visibleSlopes,
            precision = 3,
            allowZero = true,
            onClose = { slopeDialog = false },
            onSave = { options, selected ->
                slopePool = options
                visibleSlopes = selected
                settings.edit().putString("slope_pool", encodeKeys(options))
                    .putString("visible_slopes", encodeKeys(selected)).apply()
                slopeDialog = false
                invalidate()
            }
        )
    }
    if (helpDialog) {
        AlertDialog(
            onDismissRequest = { helpDialog = false },
            title = { Text("Методика ВУ-45") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Фактическое нажатие = сумма (тс/ось × оси). " +
                        "Требуемое = округление вверх (масса × норматив / 100).")
                    Text("Нажатие на 100 т = фактическое × 100 / масса.")
                    Text("Ручные оси — таблица М.1 приложения М инструкции 808-2022 ПКБ ЦВ. " +
                        "При уклонах круче 0,012 и особых местных условиях норматив задаётся местными документами.")
                    Text("В режиме «В вагонах» осность задаётся для каждой строки: 4, 6 или 8. " +
                        "Быстрое заполнение переносит только оставшееся количество, но не назначает нажатие автоматически.")
                    Text("Нормативы 33 / 44 / 55 выбираются по применимому типу состава.")
                }
            },
            confirmButton = {
                TextButton(onClick = { helpDialog = false }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun Vu45Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Vu45ChoiceDialog(
    title: String,
    allOptions: List<String>,
    initiallySelected: List<String>,
    precision: Int,
    allowZero: Boolean = false,
    onClose: () -> Unit,
    onSave: (List<String>, List<String>) -> Unit
) {
    var options by remember(title) { mutableStateOf(sortedNumbers(allOptions)) }
    var selected by remember(title) { mutableStateOf(initiallySelected) }
    var custom by remember(title) { mutableStateOf("") }
    var error by remember(title) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 410.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Выберите видимые значения", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    options.forEach { value ->
                        FilterChip(
                            selected = value in selected,
                            label = { Text(readableNumber(value)) },
                            onClick = {
                                selected = if (value in selected) selected - value else selected + value
                                error = null
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it; error = null },
                    label = { Text("Добавить значение") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedButton(onClick = {
                    val normalized = normalizedCustom(custom, precision, allowZero)
                    if (normalized == null) {
                        error = "Введите число с точностью до $precision знаков"
                    } else if (options.any { it.toBigDecimal().compareTo(normalized.toBigDecimal()) == 0 }) {
                        error = "Такое значение уже есть"
                    } else {
                        options = sortedNumbers(options + normalized)
                        selected = selected + normalized
                        custom = ""
                    }
                }) { Text("Добавить") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected.isEmpty()) error = "Оставьте хотя бы одно значение"
                else onSave(options, sortedNumbers(selected))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } }
    )
}
