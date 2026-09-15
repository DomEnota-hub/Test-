package ru.railbrake.calculator.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

enum class TenTonsChoice {
    LESS_THAN_10,
    TEN_OR_MORE
}

enum class AppendixFormula {
    FORMULA_1,
    FORMULA_2
}

enum class ProfileMode {
    NORMAL,
    BROKEN_FULL_TRACK,
    SEPARATE_SEGMENT
}

data class MassCalculationInput(
    val massTons: Double,
    val axleCount: Int? = null,
    val manualAxleLoadTons: Double? = null,
    val slopePermille: Double,
    /**
     * Нормативно ровно 10,00 т/ось относится к категории "10 и более".
     * LESS_THAN_10 допускается только как явный учебный режим сравнения.
     */
    val tenTonsChoice: TenTonsChoice? = null
)

data class MassCalculationResult(
    val massTons: Double,
    val axleCount: Int?,
    val axleLoadTons: Double,
    val isExactlyTenTons: Boolean,
    val heavyCategory: Boolean,
    val slopePermille: Double,
    val shoeCoefficientPer100Tons: Double,
    val manualAxleCoefficientPer100Tons: Double,
    val shoesExact: Double,
    val requiredShoes: Int,
    val manualAxlesExact: Double,
    val fullManualBrakeAxles: Int,
    val tenTonsTrainingOverrideUsed: Boolean
)

data class MassSupplementResult(
    val requiredShoes: Int,
    val availableShoes: Int,
    val shoeShortage: Int,
    val remainingShoeEquivalent: Double,
    val manualAxlesPerShoeEquivalent: Double,
    val additionalManualAxlesExact: Double,
    val additionalManualAxles: Int,
    val axlesPerHandBrakeUnit: Int,
    val requiredHandBrakeUnits: Int,
    val actuallyBrakedAxles: Int,
    val reserveManualAxles: Int
)

data class Appendix12Input(
    val axleCount: Int,
    val profileMode: ProfileMode,
    val slopePermille: Double,
    val formula: AppendixFormula?,
    val oilyRails: Boolean,
    val windSpeedMs: Double,
    val windDirectionMatchesPossibleMovement: Boolean,
    val availableShoes: Int,
    val axlesPerHandBrakeUnit: Int,
    /** Расчёт оставления состава без локомотива по п. 20 приложения 12. */
    val leavingWithoutLocomotive: Boolean = false,
    /** Подтверждение специальных условий п. 20 при уклоне более 2,5‰. */
    val point20ConditionConfirmed: Boolean = false
)

data class Appendix12Result(
    val baseExactBeforeOil: Double,
    val baseExactAfterOil: Double,
    val baseShoes: Int,
    val oppositeSideShoes: Int,
    val windCoefficientPer200Axles: Int,
    val windShoes: Int,
    val totalRequiredShoes: Int,
    val availableShoes: Int,
    val shoeShortage: Int,
    val substituteBrakeAxles: Int,
    val axlesPerHandBrakeUnit: Int,
    val requiredHandBrakeUnits: Int,
    val actuallyBrakedAxles: Int,
    val reserveBrakeAxles: Int,
    val isLowSlopeRule: Boolean,
    val formulaUsed: AppendixFormula?,
    val specialUnderHalfPermilleRuleUsed: Boolean,
    val extraShoesAfterSpecialRule: Int
)

object BrakeCalculator {
    val massSlopesPermille = listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0)

    private val shoesHeavy = doubleArrayOf(0.2, 0.2, 0.2, 0.2, 0.2, 0.3, 0.4)
    private val shoesLight = doubleArrayOf(0.4, 0.4, 0.4, 0.4, 0.6, 0.8, 1.0)
    private val manualAxles = doubleArrayOf(0.4, 0.4, 0.4, 0.4, 0.6, 0.8, 1.0)

    fun wagonAxles(wagons4: Int, wagons6: Int, wagons8: Int, extraAxles: Int): Int {
        require(wagons4 >= 0 && wagons6 >= 0 && wagons8 >= 0 && extraAxles >= 0)
        return try {
            Math.addExact(
                Math.addExact(Math.multiplyExact(wagons4, 4), Math.multiplyExact(wagons6, 6)),
                Math.addExact(Math.multiplyExact(wagons8, 8), extraAxles)
            )
        } catch (_: ArithmeticException) {
            error("Количество осей слишком велико")
        }
    }

    fun calculateMass(input: MassCalculationInput): MassCalculationResult {
        require(input.massTons.isFinite() && input.massTons > 0.0) { "Масса должна быть конечным числом больше нуля" }

        val axleLoad = when {
            input.manualAxleLoadTons != null -> {
                require(input.manualAxleLoadTons.isFinite() && input.manualAxleLoadTons > 0.0) { "Нагрузка на ось должна быть конечным числом больше нуля" }
                input.manualAxleLoadTons
            }
            input.axleCount != null -> {
                require(input.axleCount > 0) { "Количество осей должно быть больше нуля" }
                input.massTons / input.axleCount
            }
            else -> error("Нужно указать количество осей или нагрузку на ось")
        }

        val slopeIndex = massSlopesPermille.indexOfFirst { abs(it - input.slopePermille) < 0.0001 }
        require(slopeIndex >= 0) { "Выберите уклон из таблицы: 0, 2, 4, 6, 8, 10 или 12‰" }

        val exactlyTen = axleLoad == 10.0
        val trainingOverride = exactlyTen && input.tenTonsChoice == TenTonsChoice.LESS_THAN_10
        val heavy = when {
            trainingOverride -> false
            exactlyTen -> true
            else -> axleLoad >= 10.0
        }

        val shoeCoefficient = if (heavy) shoesHeavy[slopeIndex] else shoesLight[slopeIndex]
        val manualCoefficient = manualAxles[slopeIndex]
        val shoesExact = input.massTons * shoeCoefficient / 100.0
        val manualExact = input.massTons * manualCoefficient / 100.0

        return MassCalculationResult(
            massTons = input.massTons,
            axleCount = input.axleCount,
            axleLoadTons = axleLoad,
            isExactlyTenTons = exactlyTen,
            heavyCategory = heavy,
            slopePermille = input.slopePermille,
            shoeCoefficientPer100Tons = shoeCoefficient,
            manualAxleCoefficientPer100Tons = manualCoefficient,
            shoesExact = shoesExact,
            requiredShoes = ceilToInt(shoesExact),
            manualAxlesExact = manualExact,
            fullManualBrakeAxles = ceilToInt(manualExact),
            tenTonsTrainingOverrideUsed = trainingOverride
        )
    }

    fun calculateMassSupplement(
        massResult: MassCalculationResult,
        availableShoes: Int,
        axlesPerHandBrakeUnit: Int
    ): MassSupplementResult {
        require(availableShoes >= 0) { "Количество башмаков не может быть отрицательным" }
        require(axlesPerHandBrakeUnit > 0) { "Количество тормозных осей на единицу должно быть больше нуля" }

        val wholeShortage = max(0, massResult.requiredShoes - availableShoes)
        val remainingEquivalent = max(0.0, massResult.shoesExact - availableShoes.toDouble())
        val manualPerShoeEquivalent =
            massResult.manualAxleCoefficientPer100Tons / massResult.shoeCoefficientPer100Tons
        val additionalExact = remainingEquivalent * manualPerShoeEquivalent
        val additional = if (remainingEquivalent <= 0.0) 0 else ceilToInt(additionalExact)
        val units = if (additional == 0) 0 else ceilToInt(additional.toDouble() / axlesPerHandBrakeUnit)
        val actually = units * axlesPerHandBrakeUnit

        return MassSupplementResult(
            requiredShoes = massResult.requiredShoes,
            availableShoes = availableShoes,
            shoeShortage = wholeShortage,
            remainingShoeEquivalent = remainingEquivalent,
            manualAxlesPerShoeEquivalent = manualPerShoeEquivalent,
            additionalManualAxlesExact = additionalExact,
            additionalManualAxles = additional,
            axlesPerHandBrakeUnit = axlesPerHandBrakeUnit,
            requiredHandBrakeUnits = units,
            actuallyBrakedAxles = actually,
            reserveManualAxles = max(0, actually - additional)
        )
    }

    fun calculateAppendix12(input: Appendix12Input): Appendix12Result {
        require(input.axleCount > 0) { "Количество осей должно быть больше нуля" }
        require(input.slopePermille.isFinite() && input.slopePermille >= 0.0) { "Уклон должен быть конечным неотрицательным числом" }
        require(input.windSpeedMs.isFinite() && input.windSpeedMs >= 0.0) { "Скорость ветра должна быть конечным неотрицательным числом" }
        require(input.availableShoes >= 0) { "Количество башмаков не может быть отрицательным" }
        require(input.axlesPerHandBrakeUnit > 0) { "Количество тормозных осей на единицу должно быть больше нуля" }
        require(!input.leavingWithoutLocomotive || input.slopePermille <= 2.5 || input.point20ConditionConfirmed) {
            "При уклоне более 2,5‰ оставление состава без локомотива требует подтверждения условий п. 20 приложения 12"
        }

        val lowSlopeRule = input.slopePermille <= 0.5
        val baseBeforeOil: Double
        val formulaUsed: AppendixFormula?
        var oppositeShoes = 0

        if (lowSlopeRule) {
            baseBeforeOil = 2.0
            formulaUsed = null
        } else {
            val formula = requireNotNull(input.formula) { "Для уклона более 0,5‰ выберите формулу" }
            formulaUsed = formula
            baseBeforeOil = when (formula) {
                AppendixFormula.FORMULA_1 -> input.axleCount * (1.5 * input.slopePermille + 1.0) / 200.0
                AppendixFormula.FORMULA_2 -> input.axleCount * (4.0 * input.slopePermille + 1.0) / 200.0
            }
            if (input.slopePermille <= 1.0) oppositeShoes = 1
        }

        val baseAfterOil = if (input.oilyRails) baseBeforeOil * 1.5 else baseBeforeOil
        val baseShoes = ceilToInt(baseAfterOil)

        val windCoefficient = when {
            !input.windDirectionMatchesPossibleMovement -> 0
            input.windSpeedMs > 21.0 -> 7
            input.windSpeedMs > 15.0 -> 3
            else -> 0
        }
        val windShoes = if (windCoefficient == 0) 0 else ceilToInt(input.axleCount * windCoefficient / 200.0)
        val total = baseShoes + oppositeShoes + windShoes
        val shortage = max(0, total - input.availableShoes)

        val standardSubstituteAxles = shortage * 5
        val standardUnits = if (standardSubstituteAxles == 0) 0
        else ceilToInt(standardSubstituteAxles.toDouble() / input.axlesPerHandBrakeUnit)

        // Специальная норма действует только при i < 0,5‰: один стояночный тормоз
        // одной единицы может заменить базовые башмаки с обеих сторон. Если из-за масла
        // или ветра есть дополнительные башмаки, они остаются отдельной потребностью.
        val canUseSpecialRule = input.slopePermille < 0.5 && shortage > 0
        var specialUsed = false
        var extraShoesAfterSpecial = 0
        var substituteAxles = standardSubstituteAxles
        var units = standardUnits

        if (canUseSpecialRule) {
            val extraRequiredBeyondBasePair = max(0, total - 2)
            val extraShortage = max(0, extraRequiredBeyondBasePair - input.availableShoes)
            val extraSubstituteAxles = extraShortage * 5
            val extraUnits = if (extraSubstituteAxles == 0) 0
            else ceilToInt(extraSubstituteAxles.toDouble() / input.axlesPerHandBrakeUnit)
            val specialUnits = 1 + extraUnits

            if (specialUnits <= standardUnits || standardUnits == 0) {
                specialUsed = true
                extraShoesAfterSpecial = extraShortage
                substituteAxles = extraSubstituteAxles
                units = specialUnits
            }
        }

        val actually = if (units == 0) 0 else units * input.axlesPerHandBrakeUnit
        val reserve = if (specialUsed) {
            max(0, (units - 1) * input.axlesPerHandBrakeUnit - substituteAxles)
        } else {
            max(0, actually - substituteAxles)
        }

        return Appendix12Result(
            baseExactBeforeOil = baseBeforeOil,
            baseExactAfterOil = baseAfterOil,
            baseShoes = baseShoes,
            oppositeSideShoes = oppositeShoes,
            windCoefficientPer200Axles = windCoefficient,
            windShoes = windShoes,
            totalRequiredShoes = total,
            availableShoes = input.availableShoes,
            shoeShortage = shortage,
            substituteBrakeAxles = substituteAxles,
            axlesPerHandBrakeUnit = input.axlesPerHandBrakeUnit,
            requiredHandBrakeUnits = units,
            actuallyBrakedAxles = actually,
            reserveBrakeAxles = reserve,
            isLowSlopeRule = lowSlopeRule,
            formulaUsed = formulaUsed,
            specialUnderHalfPermilleRuleUsed = specialUsed,
            extraShoesAfterSpecialRule = extraShoesAfterSpecial
        )
    }

    private fun ceilToInt(value: Double): Int {
        require(value.isFinite() && value >= 0.0 && value <= Int.MAX_VALUE) { "Расчётное значение выходит за допустимый диапазон" }
        return ceil(value - 1e-12).toInt()
    }
}

