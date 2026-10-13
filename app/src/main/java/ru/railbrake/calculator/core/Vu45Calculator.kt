package ru.railbrake.calculator.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ВУ-45: арифметическая перепроверка показателей справки.
 * Основание: 808-2022 ПКБ ЦВ, приложение М (расчёт нажатия и табл. М.1).
 * Нормативная применимость выбранных величин и местные условия проверяются отдельно;
 * вычисленный результат НЕ является разрешением на движение.
 * Коэффициенты нажатия на ось пользователь задаёт по фактическому оборудованию.
 */
object Vu45Calculator {
    enum class FreightMode(val title: String, val normPer100Tons: Int) {
        LOADED("Гружёный", 33),
        EMPTY("Порожний", 55)
    }
    enum class QuantityMode { AXLES, WAGONS }

    // brakingAxles сохраняет имя legacy-контракта; в WAGONS это число вагонов.
    data class AxleGroup(
        val forcePerAxleTonForce: String,
        val brakingAxles: Int,
        val wagonAxles: Int = 4
    )
    data class Input(
        val mode: FreightMode,
        val trainMassTons: String,
        /** Total train axles from the VU-45 certificate, including WAGONS entry mode. */
        val trainAxles: Int,
        val groups: List<AxleGroup>,
        val densityTimeIISeconds: String? = null,
        val densityTimeIVSeconds: String? = null,
        val quantityMode: QuantityMode = QuantityMode.AXLES,
        val selectedNorm: Int = mode.normPer100Tons,
        val slope: String = "0.006"
    )
    data class Density(
        val timeReductionPercent: BigDecimal,
        val reductionMoreThanTenPercent: Boolean
    )
    data class Result(
        val requiredTonForce: BigDecimal,
        val actualTonForce: BigDecimal,
        val actualPer100TonsTonForce: BigDecimal,
        val differenceTonForce: BigDecimal,
        val brakingAxles: Int,
        /** Legacy name: UNASSIGNED axles in the entered table, not proven unbraked axles. */
        val unbrakedAxles: Int,
        val requirementMetMathematically: Boolean,
        val density: Density?,
        /** Both quantities are ALWAYS in axles, independent of input mode. */
        val enteredQuantity: Int,
        val unassignedQuantity: Int,
        val handBrakeAxles: Int?,
        val manualAxlesPer100Tons: BigDecimal?
    )

    val availablePressValues = listOf(
        "1.25", "3", "3.5", "4.5", "5", "5.5", "6", "6.5",
        "7", "7.5", "8", "8.5", "9", "10", "11", "11.5",
        "12", "13", "14", "15", "16", "18", "19", "21"
    )
    // Display choices only. Values over 0.012 require a separately established local rule.
    // Their presence in this list does not authorize calculation of hand-brake axles.
    val availableSlopeValues = listOf(
        "0.000", "0.002", "0.004", "0.006", "0.008", "0.010", "0.012",
        "0.014", "0.016", "0.018", "0.020"
    )

    /** Возвращает оставшееся количество осей ИЛИ вагонов, исключая текущую строку. */
    fun remainingForRow(declared: Int?, counts: List<Int>, rowIndex: Int): Int? {
        if (declared == null || declared <= 0 || rowIndex !in counts.indices) return null
        val elsewhere = counts.withIndex().filter { it.index != rowIndex }
            .sumOf { it.value.toLong().coerceAtLeast(0L) }
        val remaining = declared.toLong() - elsewhere
        return if (remaining in 1..Int.MAX_VALUE.toLong()) remaining.toInt() else null
    }

    fun parsePositive(raw: String, label: String): BigDecimal {
        val normalized = raw.trim().replace(',', '.')
        require(normalized.isNotBlank() && normalized.length <= 40) { "Укажите $label" }
        val number = normalized.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Некорректное число: $label")
        require(number > BigDecimal.ZERO) { "$label должно быть больше нуля" }
        return number
    }

    fun parseNonNegative(raw: String, label: String): BigDecimal {
        val normalized = raw.trim().replace(',', '.')
        require(normalized.isNotBlank() && normalized.length <= 40) { "Укажите $label" }
        val number = normalized.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Некорректное число: $label")
        require(number >= BigDecimal.ZERO) { "$label не может быть отрицательным" }
        return number
    }

    /**
     * Базовые коэффициенты таблицы М.1 (808-2022 ПКБ ЦВ), ручных тормозных осей/100 т.
     * Таблица М.1 также содержит строки 0.014–0.020, однако п. 9 приложения М
     * требует местного установления потребности при уклонах круче 0.012
     * (и для отдельных местных случаев). Поэтому здесь сознательно возвращаем null.
     * Null означает «без подтверждённой применимой местной нормы не рассчитано», НЕ 0.
     * Промежуточные уклоны не интерполируются, ближайшее значение не подставляется.
     * Источник: https://base.garant.ru/407616662/f52b32b623103013c77c8c319c288f45/
     */
    fun manualBrakeAxlesPer100(slope: String): BigDecimal? {
        val s = parseNonNegative(slope, "уклон")
        val table = mapOf(
            "0.000" to "0.4", "0.002" to "0.4", "0.004" to "0.4",
            "0.006" to "0.4", "0.008" to "0.6", "0.010" to "0.8",
            "0.012" to "1.0"
        )
        return table.entries.firstOrNull { s.compareTo(it.key.toBigDecimal()) == 0 }
            ?.value?.toBigDecimal()
    }

    fun calculate(input: Input): Result {
        val mass = parsePositive(input.trainMassTons, "массу состава")
        require(input.trainAxles > 0) { "Укажите общее число осей состава" }
        require(input.selectedNorm in listOf(33, 44, 55)) { "Выберите норматив 33, 44 или 55" }
        require(input.groups.isNotEmpty()) { "Заполните таблицу нажатий" }

        // trainAxles is ALWAYS the number of axes, never the number of wagons.
        // The edit mode only determines how each row's entered quantity is expressed.
        var countedAxles = 0L
        var actual = BigDecimal.ZERO
        input.groups.forEachIndexed { index, group ->
            require(group.brakingAxles >= 0) { "В строке ${index + 1} количество отрицательное" }
            val force = parsePositive(group.forcePerAxleTonForce, "нажатие в строке ${index + 1}")
            if (input.quantityMode == QuantityMode.WAGONS) {
                require(group.wagonAxles in listOf(4, 6, 8)) { "Укажите осность вагона: 4, 6 или 8" }
            }
            val axesInGroup = group.brakingAxles.toLong() *
                (if (input.quantityMode == QuantityMode.WAGONS) group.wagonAxles else 1)
            countedAxles += axesInGroup
            require(countedAxles <= input.trainAxles.toLong()) {
                "Распределено больше осей, чем указано в составе"
            }
            actual = actual.add(force.multiply(BigDecimal.valueOf(axesInGroup)))
        }
        require(countedAxles > 0) { "Укажите количество хотя бы в одной строке" }

        // P_required = CEIL(mass_t * selected_ton_force_per_100_t / 100).
        // selectedNorm is user-supplied; matching its number is not source applicability.
        val required = mass.multiply(BigDecimal.valueOf(input.selectedNorm.toLong()))
            .divide(BigDecimal.valueOf(100L), 0, RoundingMode.CEILING)
        // P_actual_per100 = P_actual / mass_t * 100 (avoid the misleading APK report).
        val per100 = actual.multiply(BigDecimal.valueOf(100L))
            .divide(mass, 16, RoundingMode.HALF_UP)

        val timeII = input.densityTimeIISeconds?.takeIf { it.isNotBlank() }
        val timeIV = input.densityTimeIVSeconds?.takeIf { it.isNotBlank() }
        val density = if (timeII != null && timeIV != null) {
            val ii = parsePositive(timeII, "время во II положении")
            val iv = parsePositive(timeIV, "время в IV положении")
            Density(
                ii.subtract(iv).multiply(BigDecimal.valueOf(100L))
                    .divide(ii, 16, RoundingMode.HALF_UP),
                iv.multiply(BigDecimal.TEN) < ii.multiply(BigDecimal.valueOf(9L))
            )
        } else null

        // N_hand_axes = CEIL(mass_t * coefficient_from_M_1 / 100), if applicable.
        // Actual available manual-brake axles are observed, not calculated here.
        val handRate = manualBrakeAxlesPer100(input.slope)
        val handAxes = handRate?.let {
            mass.multiply(it).divide(BigDecimal.valueOf(100L), 0, RoundingMode.CEILING)
                .intValueExact()
        }

        val entered = countedAxles.toInt()
        val remaining = input.trainAxles - entered
        return Result(
            requiredTonForce = required,
            actualTonForce = actual,
            actualPer100TonsTonForce = per100,
            differenceTonForce = actual.subtract(required),
            brakingAxles = entered,
            // The declared total is always in axes; an unassigned remainder is not proof of unbraked equipment.
            unbrakedAxles = remaining,
            // An incomplete breakdown must never be reported as a successful check.
            // This is ONLY arithmetic against the user-selected number, not regulatory applicability.
            requirementMetMathematically = remaining == 0 && actual >= required,
            density = density,
            enteredQuantity = entered,
            unassignedQuantity = remaining,
            handBrakeAxles = handAxes,
            manualAxlesPer100Tons = handRate
        )
    }
}
