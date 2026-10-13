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

    @Test fun leftoversExcludeCurrentRow() {
        assertEquals(212, Vu45Calculator.remainingForRow(312, listOf(100,0,0),1))
        assertEquals(212, Vu45Calculator.remainingForRow(312, listOf(100,100,0),1))
        assertNull(Vu45Calculator.remainingForRow(312, listOf(320,0),1))
        assertNull(Vu45Calculator.remainingForRow(null, listOf(0,0),0))
        assertNull(Vu45Calculator.remainingForRow(312, listOf(0),3))
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
