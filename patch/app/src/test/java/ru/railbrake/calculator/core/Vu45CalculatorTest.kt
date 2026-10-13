package ru.railbrake.calculator.core

import org.junit.Assert.*
import org.junit.Test

class Vu45CalculatorTest {
    private fun sample(mode: Vu45Calculator.FreightMode = Vu45Calculator.FreightMode.LOADED) =
        Vu45Calculator.Input(mode, "4000", 240, listOf(
            Vu45Calculator.AxleGroup("3,5", 20),
            Vu45Calculator.AxleGroup("5", 8),
            Vu45Calculator.AxleGroup("7", 200),
            Vu45Calculator.AxleGroup("10", 12)
        ))

    @Test fun sampleLoaded() {
        val r = Vu45Calculator.calculate(sample())
        assertEquals(0, r.requiredTonForce.compareTo("1320".toBigDecimal()))
        assertEquals(0, r.actualTonForce.compareTo("1630".toBigDecimal()))
        assertEquals(0, r.actualPer100TonsTonForce.compareTo("40.75".toBigDecimal()))
        assertEquals(0, r.differenceTonForce.compareTo("310".toBigDecimal()))
        assertEquals(240, r.brakingAxles)
        assertEquals(0, r.unassignedQuantity)
    }
    @Test fun screenExampleAndHandBrakeTable() {
        val r = Vu45Calculator.calculate(Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "4504", 312, listOf(
                Vu45Calculator.AxleGroup("3.5", 100),
                Vu45Calculator.AxleGroup("7", 212)
            ), slope="0,006"
        ))
        assertEquals(0, r.requiredTonForce.compareTo("1487".toBigDecimal()))
        assertEquals(0, r.actualTonForce.compareTo("1834".toBigDecimal()))
        assertEquals(0, r.actualPer100TonsTonForce.setScale(1, java.math.RoundingMode.HALF_UP)
            .compareTo("40.7".toBigDecimal()))
        assertEquals(19, r.handBrakeAxles)
    }
    @Test fun emptyAndNorm44() {
        assertEquals(0, Vu45Calculator.calculate(sample(Vu45Calculator.FreightMode.EMPTY))
            .requiredTonForce.compareTo("2200".toBigDecimal()))
        assertEquals(0, Vu45Calculator.calculate(sample().copy(selectedNorm=44))
            .requiredTonForce.compareTo("1760".toBigDecimal()))
    }
    @Test fun wagonModeUsesActualAxlesPerRow() {
        val input = Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "2000", 64, listOf(
                Vu45Calculator.AxleGroup("5", 10, 4),
                Vu45Calculator.AxleGroup("7", 4, 6)
            ), quantityMode=Vu45Calculator.QuantityMode.WAGONS
        )
        val r = Vu45Calculator.calculate(input)
        assertEquals(0, r.actualTonForce.compareTo("368".toBigDecimal()))
        assertEquals(64, r.brakingAxles)
        assertEquals(64, r.enteredQuantity) // always measured in axes
        assertEquals(0, r.unassignedQuantity)
        assertEquals(0, r.unbrakedAxles)
        assertFalse(r.requirementMetMathematically) // 368 < 660 at selected 33 tс/100 т
    }
    @Test fun wagonModeRequiresDeclaredAxesNotWagonCount() {
        val input = Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "2000", 14, listOf(
                Vu45Calculator.AxleGroup("5", 10, 4),
                Vu45Calculator.AxleGroup("7", 4, 6)
            ), quantityMode=Vu45Calculator.QuantityMode.WAGONS
        )
        try {
            Vu45Calculator.calculate(input)
            fail("14 wagons cannot be confused with 64 declared axles")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun wagonModeUnassignedQuantityIsAlwaysAxles() {
        val r = Vu45Calculator.calculate(Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "2000", 72, listOf(
                Vu45Calculator.AxleGroup("5", 10, 4),
                Vu45Calculator.AxleGroup("7", 4, 6)
            ), quantityMode=Vu45Calculator.QuantityMode.WAGONS
        ))
        assertEquals(64, r.enteredQuantity)
        assertEquals(8, r.unassignedQuantity)
        assertEquals(8, r.unbrakedAxles)
    }

    @Test fun noAffirmativeStatusWithUnassignedAxlesEvenIfMathThresholdIsMet() {
        val r = Vu45Calculator.calculate(Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "100", 8,
            listOf(Vu45Calculator.AxleGroup("12", 4))
        ))
        assertTrue(r.actualTonForce > r.requiredTonForce)
        assertEquals(4, r.unassignedQuantity)
        assertFalse(r.requirementMetMathematically)
    }

    @Test fun validCompletedDistributionCanPassSelectedArithmeticThreshold() {
        val r = Vu45Calculator.calculate(Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "100", 4,
            listOf(Vu45Calculator.AxleGroup("12", 4))
        ))
        assertEquals(0, r.unassignedQuantity)
        assertTrue(r.requirementMetMathematically)
        // Not a regulatory/operational approval: source applicability remains to be confirmed.
    }

    @Test fun screenshotReferencePer100AndSelectedNorm44() {
        // The source APK SCREEN shows 1400 / 4500 * 100 = 31.1,
        // NOT 0.3 as claimed in the older APK extraction markdown report.
        // User-selected 44 -> 1980 tf required; 31.1 is below this selected figure.
        val r = Vu45Calculator.calculate(Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "4500", 200,
            listOf(Vu45Calculator.AxleGroup("7", 200)),
            selectedNorm = 44, slope = "0.007"
        ))
        assertEquals(0, r.actualTonForce.compareTo("1400".toBigDecimal()))
        assertEquals(0, r.requiredTonForce.compareTo("1980".toBigDecimal()))
        assertEquals(0, r.actualPer100TonsTonForce.setScale(1, java.math.RoundingMode.HALF_UP)
            .compareTo("31.1".toBigDecimal()))
        assertFalse(r.requirementMetMathematically)
        assertEquals(0, r.unassignedQuantity)
        // Manual brake axle count is intentionally NOT asserted here:
        // the APK's ceil(mass*slope) and the current Test table differ.
    }

    @Test fun leftoversExcludeCurrentRow() {
        assertEquals(212, Vu45Calculator.remainingForRow(312, listOf(100,0,0),1))
        assertEquals(212, Vu45Calculator.remainingForRow(312, listOf(100,100,0),1))
        assertNull(Vu45Calculator.remainingForRow(312, listOf(320,0),1))
        assertNull(Vu45Calculator.remainingForRow(null, listOf(0,0),0))
        assertNull(Vu45Calculator.remainingForRow(312, listOf(0),3))
    }
    @Test fun manualBrakeTableM1VerifiedCoefficients() {
        // 808-2022 ПКБ ЦВ, приложение М, таблица М.1: осей ручного торможения на 100 т.
        val verified = mapOf(
            "0.000" to "0.4", "0.002" to "0.4", "0.004" to "0.4",
            "0.006" to "0.4", "0.008" to "0.6", "0.010" to "0.8",
            "0.012" to "1.0"
        )
        verified.forEach { (slope, coefficient) ->
            val actual = Vu45Calculator.manualBrakeAxlesPer100(slope)
            assertNotNull("Missing table M.1 coefficient for $slope", actual)
            assertEquals(0, actual!!.compareTo(coefficient.toBigDecimal()))
        }
        assertEquals(0, Vu45Calculator.manualBrakeAxlesPer100("0,008")!!
            .compareTo("0.6".toBigDecimal()))
    }

    @Test fun manualBrakeDemandUsesTableInsteadOfSourceApkMassTimesSlope() {
        val result = Vu45Calculator.calculate(Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "1000", 4,
            listOf(Vu45Calculator.AxleGroup("7", 4)),
            slope = "0.008"
        ))
        // ceil(1000 * 0.6 / 100) = 6, not ceil(1000 * 0.008) = 8.
        assertEquals(6, result.handBrakeAxles)
        assertEquals(0, result.manualAxlesPer100Tons!!.compareTo("0.6".toBigDecimal()))
    }

    @Test fun instructionMHandBrakeExampleAndRoundingUp() {
        val exact = Vu45Calculator.calculate(sample().copy(slope = "0.010"))
        // Instruction 808-2022 appendix M: 4000 t * 0.8 / 100 = 32 axes.
        assertEquals(32, exact.handBrakeAxles)
        val fractional = Vu45Calculator.calculate(sample().copy(
            trainMassTons = "1001", slope = "0.010"
        ))
        assertEquals(9, fractional.handBrakeAxles) // ceil(8.008)
    }

    @Test fun handBrakeNeedsLocalRuleForSteepOrIntermediateSlopes() {
        // The source TABLE mentions 0.014–0.020, but >0.012 requires local rules;
        // do not silently use the row as a universal legal norm.
        listOf("0.007", "0.014", "0.018", "0.020", "0.022").forEach { slope ->
            assertNull(Vu45Calculator.manualBrakeAxlesPer100(slope))
            val result = Vu45Calculator.calculate(sample().copy(slope = slope))
            assertNull(result.handBrakeAxles) // Unknown, never 0 or a made-up value
            assertNull(result.manualAxlesPer100Tons)
        }
    }

    @Test fun requiredBrakePressRoundsFractionUpPerAppendixM() {
        val r = Vu45Calculator.calculate(Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "101", 4,
            listOf(Vu45Calculator.AxleGroup("5", 4)), selectedNorm = 33
        ))
        assertEquals(0, r.requiredTonForce.compareTo("34".toBigDecimal()))
        assertEquals(0, r.actualTonForce.compareTo("20".toBigDecimal()))
        assertEquals(0, r.actualPer100TonsTonForce.compareTo(
            "20".toBigDecimal().multiply("100".toBigDecimal())
                .divide("101".toBigDecimal(), 16, java.math.RoundingMode.HALF_UP)
        ))
    }

    @Test fun slopeOutOfTableRequiresLocalValue() {
        assertNull(Vu45Calculator.manualBrakeAxlesPer100("0.014"))
        assertNull(Vu45Calculator.manualBrakeAxlesPer100("0.007"))
        assertEquals(0, Vu45Calculator.manualBrakeAxlesPer100("0.008")!!.compareTo("0.6".toBigDecimal()))
    }
    @Test fun ceilingAndDensityBoundary() {
        val r = Vu45Calculator.calculate(Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "301", 12,
            listOf(Vu45Calculator.AxleGroup("5", 8)),
            densityTimeIISeconds="100", densityTimeIVSeconds="90"
        ))
        assertEquals(0, r.requiredTonForce.compareTo("100".toBigDecimal()))
        assertEquals(4, r.unassignedQuantity)
        assertFalse(r.density!!.reductionMoreThanTenPercent)
        assertEquals(0, r.density!!.timeReductionPercent.compareTo("10".toBigDecimal()))
        assertTrue(Vu45Calculator.calculate(sample().copy(
            densityTimeIISeconds="100",densityTimeIVSeconds="89.99"
        )).density!!.reductionMoreThanTenPercent)
        assertNull(Vu45Calculator.calculate(sample().copy(densityTimeIISeconds="100")).density)
    }
    @Test fun invalidInputsRejected() {
        val cases = listOf(
            sample().copy(trainMassTons="0"), sample().copy(trainAxles=0),
            sample().copy(trainAxles=239), sample().copy(groups=emptyList()),
            sample().copy(groups=listOf(Vu45Calculator.AxleGroup("0",1))),
            sample().copy(groups=listOf(Vu45Calculator.AxleGroup("5",-1))),
            sample().copy(groups=listOf(Vu45Calculator.AxleGroup("5",0))),
            sample().copy(densityTimeIISeconds="100",densityTimeIVSeconds="-1"),
            sample().copy(selectedNorm=34)
        )
        cases.forEach { input ->
            try { Vu45Calculator.calculate(input); fail("Not rejected: $input") }
            catch (_: IllegalArgumentException) {}
        }
    }
}
