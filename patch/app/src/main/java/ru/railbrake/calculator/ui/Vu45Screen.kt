package ru.railbrake.calculator.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import ru.railbrake.calculator.core.Vu45Calculator
import ru.railbrake.calculator.data.HistoryRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

private const val VU45_HISTORY_STORE = "vu45_calculation_snapshots"

/** Immutable historical input payloads are kept separately; existing shoe-calculation history is not rewritten. */
internal object Vu45HistorySnapshots {
    fun load(context: Context, timestampMillis: Long): String? =
        context.getSharedPreferences(VU45_HISTORY_STORE, Context.MODE_PRIVATE)
            .getString(timestampMillis.toString(), null)

    fun save(context: Context, timestampMillis: Long, snapshot: String) {
        val written = context.getSharedPreferences(VU45_HISTORY_STORE, Context.MODE_PRIVATE)
            .edit().putString(timestampMillis.toString(), snapshot).commit()
        check(written) { "Не удалось сохранить исходные данные расчёта" }
    }

    fun decodeInput(snapshot: String): Vu45Calculator.Input {
        val root = JSONObject(snapshot)
        val input = root.getJSONObject("inputs")
        val groups = input.getJSONArray("groups")
        return Vu45Calculator.Input(
            mode = Vu45Calculator.FreightMode.valueOf(input.getString("freightMode")),
            trainMassTons = input.getString("trainMassTons"),
            trainAxles = input.getInt("trainAxles"),
            groups = (0 until groups.length()).map { i ->
                val g = groups.getJSONObject(i)
                Vu45Calculator.AxleGroup(g.getString("forcePerAxleTonForce"), g.getInt("brakingAxles"))
            },
            densityTimeIISeconds = input.optJSONObject("measuredDensity")
                ?.optString("timeIISeconds")?.takeIf { it.isNotBlank() },
            densityTimeIVSeconds = input.optJSONObject("measuredDensity")
                ?.optString("timeIVSeconds")?.takeIf { it.isNotBlank() }
        )
    }

    fun encode(input: Vu45Calculator.Input, result: Vu45Calculator.Result, timestamp: Long): String {
        val groups = JSONArray()
        input.groups.forEach {
            groups.put(JSONObject().put("forcePerAxleTonForce", it.forcePerAxleTonForce)
                .put("brakingAxles", it.brakingAxles))
        }
        val inputJson = JSONObject()
            .put("freightMode", input.mode.name)
            .put("trainMassTons", input.trainMassTons)
            .put("trainAxles", input.trainAxles)
            .put("groups", groups)
        if (!input.densityTimeIISeconds.isNullOrBlank() ||
            !input.densityTimeIVSeconds.isNullOrBlank()) {
            inputJson.put("measuredDensity", JSONObject()
                .put("timeIISeconds", input.densityTimeIISeconds ?: "")
                .put("timeIVSeconds", input.densityTimeIVSeconds ?: ""))
        }
        val results = JSONObject()
            .put("requiredTonForce", result.requiredTonForce.toPlainString())
            .put("actualTonForce", result.actualTonForce.toPlainString())
            .put("actualPer100TonsTonForce", result.actualPer100TonsTonForce.toPlainString())
            .put("differenceTonForce", result.differenceTonForce.toPlainString())
            .put("brakingAxles", result.brakingAxles)
            .put("unbrakedAxles", result.unbrakedAxles)
        result.density?.let {
            results.put("relativeDensityReductionPercent", it.timeReductionPercent.toPlainString())
            results.put("relativeReductionMoreThan10Percent", it.reductionMoreThanTenPercent)
        }
        return JSONObject()
            .put("kind", "vu45-brake-pressing")
            .put("schemaVersion", 1)
            .put("formulaVersion", 1)
            .put("sourceRuleId", "808-2022-PKB-CV:appendix-M:edition-2025-11-26")
            .put("sourceVerification", "UNVERIFIED_FOR_OPERATION")
            .put("createdAt", Instant.ofEpochMilli(timestamp).toString())
            .put("inputs", inputJson)
            .put("results", results)
            .toString()
    }
}

private data class Vu45GroupDraft(val force: String = "", val axles: String = "")
private fun BigDecimal.vu45String(scale: Int? = null): String =
    (if (scale == null) this.stripTrailingZeros() else this.setScale(scale, RoundingMode.HALF_UP))
        .toPlainString().replace('.', ',')

@Composable
internal fun Vu45Screen(
    initialSnapshot: String?,
    onSaved: (HistoryRecord) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val restored = remember(initialSnapshot) {
        initialSnapshot?.let { runCatching { Vu45HistorySnapshots.decodeInput(it) }.getOrNull() }
    }
    var mode by remember(initialSnapshot) {
        mutableStateOf(restored?.mode ?: Vu45Calculator.FreightMode.LOADED)
    }
    var mass by remember(initialSnapshot) { mutableStateOf(restored?.trainMassTons.orEmpty()) }
    var axles by remember(initialSnapshot) {
        mutableStateOf(restored?.trainAxles?.toString().orEmpty())
    }
    var groups by remember(initialSnapshot) {
        mutableStateOf(
            restored?.groups?.map { Vu45GroupDraft(it.forcePerAxleTonForce, it.brakingAxles.toString()) }
                ?: listOf(Vu45GroupDraft())
        )
    }
    var timeII by remember(initialSnapshot) {
        mutableStateOf(restored?.densityTimeIISeconds.orEmpty())
    }
    var timeIV by remember(initialSnapshot) {
        mutableStateOf(restored?.densityTimeIVSeconds.orEmpty())
    }
    var result by remember(initialSnapshot) { mutableStateOf<Vu45Calculator.Result?>(null) }
    var validatedInput by remember(initialSnapshot) { mutableStateOf<Vu45Calculator.Input?>(null) }
    var error by remember(initialSnapshot) { mutableStateOf<String?>(null) }
    var saved by remember(initialSnapshot) { mutableStateOf(false) }

    fun invalidate() {
        result = null
        validatedInput = null
        error = null
        saved = false
    }

    Text("ВУ-45 · Тормозное нажатие",
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(
            "Расчётный помощник, а не официальная справка ВУ-45. Результат не подтверждает исправность тормозов и не разрешает отправление поезда. " +
                "Применимость нормативов и источники сил нажатия требуют отдельной проверки.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
    Text("Категория грузового поезда", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Vu45Calculator.FreightMode.values().forEach { choice ->
            FilterChip(selected = mode == choice, onClick = {
                mode = choice
                invalidate()
            }, label = { Text(choice.title) })
        }
    }
    OutlinedTextField(
        value = mass, onValueChange = { mass = it; invalidate() },
        label = { Text("Масса состава без локомотива, т") },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = axles, onValueChange = { axles = it; invalidate() },
        label = { Text("Всего осей состава, шт.") },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Text("Группы осей с включёнными тормозами",
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("Введите нажатие на ось вручную по подтверждённым данным. Нет группы — не значит нулевое торможение.",
        style = MaterialTheme.typography.bodySmall)
    groups.forEachIndexed { index, group ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = group.force,
                onValueChange = {
                    groups = groups.toMutableList().also { list -> list[index] = group.copy(force = it) }
                    invalidate()
                },
                label = { Text("тс/ось") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = group.axles,
                onValueChange = {
                    groups = groups.toMutableList().also { list -> list[index] = group.copy(axles = it) }
                    invalidate()
                },
                label = { Text("Осей") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                groups = groups.toMutableList().also { list -> list.removeAt(index) }
                invalidate()
            }) { Text("×") }
        }
    }
    OutlinedButton(onClick = {
        groups = groups + Vu45GroupDraft()
        invalidate()
    }) { Text("+ Добавить группу осей") }
    Text("Относительное изменение времени плотности II → IV (необязательно)",
        style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = timeII, onValueChange = { timeII = it; invalidate() },
            label = { Text("II, с") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = timeIV, onValueChange = { timeIV = it; invalidate() },
            label = { Text("IV, с") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, modifier = Modifier.weight(1f)
        )
    }
    Button(onClick = {
        runCatching {
            val input = Vu45Calculator.Input(
                mode = mode,
                trainMassTons = mass,
                trainAxles = axles.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Укажите целое число осей"),
                groups = groups.mapIndexed { index, group ->
                    Vu45Calculator.AxleGroup(
                        group.force,
                        group.axles.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Неверное количество осей в группе ${index + 1}")
                    )
                },
                densityTimeIISeconds = timeII,
                densityTimeIVSeconds = timeIV
            )
            validatedInput = input
            result = Vu45Calculator.calculate(input)
            saved = false
            error = null
        }.onFailure {
            invalidate()
            error = it.message ?: "Не удалось рассчитать введённые данные"
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Рассчитать") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    val currentResult = result
    val currentInput = validatedInput
    if (currentResult != null && currentInput != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Результат расчёта", style = MaterialTheme.typography.titleMedium)
                Text("Требуемое нажатие: ${currentResult.requiredTonForce.vu45String()} тс")
                Text("Фактическое нажатие: ${currentResult.actualTonForce.vu45String()} тс")
                Text("На 100 т массы: ${currentResult.actualPer100TonsTonForce.vu45String(2)} тс/100 т")
                Text("Разница: ${currentResult.differenceTonForce.vu45String()} тс")
                Text("Включено осей: ${currentResult.brakingAxles} из ${currentInput.trainAxles}")
                if (currentResult.unbrakedAxles > 0) {
                    Text("Оси вне указанных групп: ${currentResult.unbrakedAxles}")
                }
                Text(if (currentResult.requirementMetMathematically)
                    "Математический критерий нажатия выполнен."
                    else "Математический критерий нажатия не выполнен.",
                    fontWeight = FontWeight.SemiBold)
                currentResult.density?.let {
                    Text("II → IV: изменение времени ${it.timeReductionPercent.vu45String(2)}%.")
                    Text(if (it.reductionMoreThanTenPercent)
                        "Снижение времени больше 10%."
                        else "Снижение времени не превышает 10%.")
                    Text("Это не проверка минимального времени по таблице М.3.")
                }
                Text("Не заключение об исправности тормозов; фактические данные и нормы требуют проверки.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            enabled = !saved,
            onClick = {
                runCatching {
                    val timestamp = System.currentTimeMillis()
                    val snapshot = Vu45HistorySnapshots.encode(currentInput, currentResult, timestamp)
                    Vu45HistorySnapshots.save(context, timestamp, snapshot)
                    onSaved(HistoryRecord(
                        timestampMillis = timestamp,
                        mode = "ВУ-45",
                        title = "${mode.title} • ${mass} т",
                        summary = "${currentResult.actualTonForce.vu45String()} тс / ${currentResult.requiredTonForce.vu45String()} тс",
                        details = "На 100 т: ${currentResult.actualPer100TonsTonForce.vu45String(2)} тс/100 т; " +
                            "разница ${currentResult.differenceTonForce.vu45String()} тс; " +
                            "осей ${currentResult.brakingAxles} из ${currentInput.trainAxles}. " +
                            "Результат сохранён как расчёт, не как справка ВУ-45."
                    ))
                    saved = true
                    error = null
                }.onFailure { error = it.message ?: "Не удалось сохранить расчёт" }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saved) "Сохранено в Историю" else "Сохранить в Историю") }
    }
}
