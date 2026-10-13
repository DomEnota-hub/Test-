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

    fun clear(context: Context) {
        context.getSharedPreferences(VU45_HISTORY_STORE, Context.MODE_PRIVATE).edit().clear().apply()
    }

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
                Vu45Calculator.AxleGroup(g.getString("forcePerAxleTonForce"), g.getInt("brakingAxles"), g.optInt("wagonAxles", 4))
            },
            densityTimeIISeconds = input.optJSONObject("measuredDensity")
                ?.optString("timeIISeconds")?.takeIf { it.isNotBlank() },
            densityTimeIVSeconds = input.optJSONObject("measuredDensity")
                ?.optString("timeIVSeconds")?.takeIf { it.isNotBlank() },
            quantityMode = Vu45Calculator.QuantityMode.valueOf(input.optString("quantityMode", "AXLES")),
            selectedNorm = input.optInt("selectedNorm", if (input.optString("freightMode") == "EMPTY") 55 else 33),
            slope = input.optString("slope", "0.006")
        )
    }

    fun encode(input: Vu45Calculator.Input, result: Vu45Calculator.Result, timestamp: Long): String {
        val groups = JSONArray()
        input.groups.forEach {
            groups.put(JSONObject().put("forcePerAxleTonForce", it.forcePerAxleTonForce)
                .put("brakingAxles", it.brakingAxles).put("wagonAxles", it.wagonAxles))
        }
        val inputJson = JSONObject()
            .put("freightMode", input.mode.name)
            .put("trainMassTons", input.trainMassTons)
            .put("trainAxles", input.trainAxles)
            .put("quantityMode", input.quantityMode.name)
            .put("selectedNorm", input.selectedNorm)
            .put("slope", input.slope)
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
            .put("handBrakeAxles", result.handBrakeAxles ?: JSONObject.NULL)
            .put("assignedQuantity", result.enteredQuantity)
            .put("unassignedQuantity", result.unassignedQuantity)
        result.density?.let {
            results.put("relativeDensityReductionPercent", it.timeReductionPercent.toPlainString())
            results.put("relativeReductionMoreThan10Percent", it.reductionMoreThanTenPercent)
        }
        return JSONObject()
            .put("kind", "vu45-brake-pressing")
            .put("schemaVersion", 1)
            .put("formulaVersion", 2)
            .put("sourceRuleId", "808-2022-PKB-CV:appendix-M:edition-2025-11-26")
            .put("calculationMethod", "APPENDIX_M")
            .put("createdAt", Instant.ofEpochMilli(timestamp).toString())
            .put("inputs", inputJson)
            .put("results", results)
            .toString()
    }
}

