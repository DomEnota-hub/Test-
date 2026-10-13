package ru.railbrake.calculator.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Расчёт отдельных показателей ВУ-45. Не составляет справку и не даёт допуска к отправлению.
 * Все усилия на ось задаются вручную; действующие таблицы Л.1/М.1/М.3 здесь не воспроизведены.
 */
object Vu45Calculator {
    enum class FreightMode(val title: String, val normPer100Tons: Int) {
        LOADED("Гружёный", 33),
        EMPTY("Порожний", 55)
    }

    data class AxleGroup(val forcePerAxleTonForce: String, val brakingAxles: Int)
    data class Input(
        val mode: FreightMode,
        val trainMassTons: String,
        val trainAxles: Int,
        val groups: List<AxleGroup>,
        val densityTimeIISeconds: String? = null,
        val densityTimeIVSeconds: String? = null
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
        val unbrakedAxles: Int,
        val requirementMetMathematically: Boolean,
        val density: Density?
    )

    fun parsePositive(raw: String, label: String): BigDecimal {
        val normalized = raw.trim().replace(',', '.')
        require(normalized.isNotBlank() && normalized.length <= 40) { "Укажите $label" }
        val number = normalized.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Некорректное число: $label")
        require(number > BigDecimal.ZERO) { "$label должно быть больше нуля" }
        return number
    }

    fun calculate(input: Input): Result {
        val mass = parsePositive(input.trainMassTons, "массу состава")
        require(input.trainAxles > 0) { "Количество осей должно быть положительным" }
        require(input.groups.isNotEmpty()) { "Добавьте хотя бы одну группу тормозных осей" }
        var totalAxles = 0L
        var actual = BigDecimal.ZERO
        input.groups.forEachIndexed { index, group ->
            require(group.brakingAxles > 0) { "У группы ${index + 1} количество осей должно быть больше нуля" }
            val force = parsePositive(group.forcePerAxleTonForce, "нажатие в группе ${index + 1}")
            totalAxles += group.brakingAxles.toLong()
            require(totalAxles <= input.trainAxles.toLong()) {
                "Количество осей с действующими тормозами превышает общее число осей"
            }
            actual = actual.add(force.multiply(BigDecimal.valueOf(group.brakingAxles.toLong())))
        }

        val required = mass.multiply(BigDecimal.valueOf(input.mode.normPer100Tons.toLong()))
            .divide(BigDecimal.valueOf(100L), 0, RoundingMode.CEILING)
        val per100 = actual.multiply(BigDecimal.valueOf(100L))
            .divide(mass, 16, RoundingMode.HALF_UP)

        val timeII = input.densityTimeIISeconds?.takeIf { it.isNotBlank() }
        val timeIV = input.densityTimeIVSeconds?.takeIf { it.isNotBlank() }
        // Одиночное измерение не трактуем как проверку плотности.
        val density = if (timeII != null && timeIV != null) {
            val ii = parsePositive(timeII, "время во II положении")
            val iv = parsePositive(timeIV, "время в IV положении")
            Density(
                timeReductionPercent = ii.subtract(iv).multiply(BigDecimal.valueOf(100L))
                    .divide(ii, 16, RoundingMode.HALF_UP),
                reductionMoreThanTenPercent = iv.multiply(BigDecimal.TEN) <
                    ii.multiply(BigDecimal.valueOf(9L))
            )
        } else null

        return Result(
            requiredTonForce = required,
            actualTonForce = actual,
            actualPer100TonsTonForce = per100,
            differenceTonForce = actual.subtract(required),
            brakingAxles = totalAxles.toInt(),
            unbrakedAxles = input.trainAxles - totalAxles.toInt(),
            requirementMetMathematically = actual >= required,
            density = density
        )
    }
}
