package ru.railbrake.calculator.core

import org.junit.Assert.*
import org.junit.Test

class Vu45CalculatorTest {
    private fun demo(mode: Vu45Calculator.FreightMode = Vu45Calculator.FreightMode.LOADED) =
        Vu45Calculator.Input(
            mode, "4000", 240,
            listOf(
                Vu45Calculator.AxleGroup("3,5", 20),
                Vu45Calculator.AxleGroup("5", 8),
                Vu45Calculator.AxleGroup("7", 200),
                Vu45Calculator.AxleGroup("10", 12)
            )
        )

    @Test fun loadedExample() {
        val r = Vu45Calculator.calculate(demo())
        assertEquals(0, r.requiredTonForce.compareTo("1320".toBigDecimal()))
        assertEquals(0, r.actualTonForce.compareTo("1630".toBigDecimal()))
        assertEquals(0, r.actualPer100TonsTonForce.compareTo("40.75".toBigDecimal()))
        assertEquals(0, r.differenceTonForce.compareTo("310".toBigDecimal()))
        assertEquals(240, r.brakingAxles)
        assertEquals(0, r.unbrakedAxles)
        assertTrue(r.requirementMetMathematically)
    }

    @Test fun emptyNorm() {
        val r = Vu45Calculator.calculate(demo(Vu45Calculator.FreightMode.EMPTY))
        assertEquals(0, r.requiredTonForce.compareTo("2200".toBigDecimal()))
        assertFalse(r.requirementMetMathematically)
    }

    @Test fun ceilingAndUnbrakedAxles() {
        val r = Vu45Calculator.calculate(Vu45Calculator.Input(
            Vu45Calculator.FreightMode.LOADED, "301", 12,
            listOf(Vu45Calculator.AxleGroup("5", 8))
        ))
        assertEquals(0, r.requiredTonForce.compareTo("100".toBigDecimal()))
        assertEquals(8, r.brakingAxles)
        assertEquals(4, r.unbrakedAxles)
    }

    @Test fun densityBoundaryExactlyTenIsNotMore() {
        val r = Vu45Calculator.calculate(demo().copy(
            densityTimeIISeconds = "100",
            densityTimeIVSeconds = "90"
        ))
        assertFalse(r.density!!.reductionMoreThanTenPercent)
        assertEquals(0, r.density!!.timeReductionPercent.compareTo("10".toBigDecimal()))
        assertTrue(Vu45Calculator.calculate(demo().copy(
            densityTimeIISeconds = "100",
            densityTimeIVSeconds = "89.99"
        )).density!!.reductionMoreThanTenPercent)
    }

    @Test fun incompleteDensityDoesNotInvalidateBrakePressing() {
        assertNull(Vu45Calculator.calculate(demo().copy(
            densityTimeIISeconds = "100"
        )).density)
    }

    @Test fun invalidInputsRejected() {
        val cases = listOf(
            demo().copy(trainMassTons = "0"),
            demo().copy(trainMassTons = "-1"),
            demo().copy(trainAxles = 0),
            demo().copy(trainAxles = 239),
            demo().copy(groups = emptyList()),
            demo().copy(groups = listOf(Vu45Calculator.AxleGroup("0", 1))),
            demo().copy(groups = listOf(Vu45Calculator.AxleGroup("5", 0))),
            demo().copy(densityTimeIISeconds = "2", densityTimeIVSeconds = "-1")
        )
        cases.forEach { input ->
            try {
                Vu45Calculator.calculate(input)
                fail("Expected rejection for $input")
            } catch (_: IllegalArgumentException) {
                // ожидается
            }
        }
    }
}
