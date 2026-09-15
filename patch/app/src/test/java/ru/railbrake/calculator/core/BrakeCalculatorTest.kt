package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrakeCalculatorTest {
    @Test
    fun nonFinite_mass_and_wind_are_rejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            BrakeCalculator.calculateMass(MassCalculationInput(Double.POSITIVE_INFINITY, axleCount = 10, slopePermille = 0.0))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            BrakeCalculator.calculateAppendix12(Appendix12Input(200, ProfileMode.NORMAL, 8.0, AppendixFormula.FORMULA_1, false, Double.NaN, true, 0, 4))
        }
    }

    @Test
    fun leaving_without_locomotive_over_2_5_requires_point20_confirmation() {
        val input = Appendix12Input(200, ProfileMode.NORMAL, 8.0, AppendixFormula.FORMULA_1, false, 0.0, false, 0, 4, leavingWithoutLocomotive = true)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { BrakeCalculator.calculateAppendix12(input) }
        BrakeCalculator.calculateAppendix12(input.copy(point20ConditionConfirmed = true))
    }

    @Test
    fun wagon_axle_overflow_is_rejected() {
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            BrakeCalculator.wagonAxles(Int.MAX_VALUE, 0, 0, 0)
        }
    }

    @Test
    fun mass_4000t_212axles_12permille() {
        val result = BrakeCalculator.calculateMass(
            MassCalculationInput(massTons = 4000.0, axleCount = 212, slopePermille = 12.0)
        )
        assertTrue(result.heavyCategory)
        assertEquals(16, result.requiredShoes)
        assertEquals(40, result.fullManualBrakeAxles)
    }

    @Test
    fun exactlyTen_is_normatively_tenOrMore() {
        val result = BrakeCalculator.calculateMass(
            MassCalculationInput(1200.0, axleCount = 120, slopePermille = 10.0)
        )
        assertTrue(result.isExactlyTenTons)
        assertTrue(result.heavyCategory)
        assertFalse(result.tenTonsTrainingOverrideUsed)
        assertEquals(4, result.requiredShoes)
    }

    @Test
    fun exactlyTen_can_be_overridden_only_when_explicitly_requested_for_training() {
        val result = BrakeCalculator.calculateMass(
            MassCalculationInput(
                1200.0,
                axleCount = 120,
                slopePermille = 10.0,
                tenTonsChoice = TenTonsChoice.LESS_THAN_10
            )
        )
        assertFalse(result.heavyCategory)
        assertTrue(result.tenTonsTrainingOverrideUsed)
        assertEquals(10, result.requiredShoes)
    }

    @Test
    fun mass_category_boundaries_around_ten_tons_per_axle() {
        val below = BrakeCalculator.calculateMass(
            MassCalculationInput(999.9, manualAxleLoadTons = 9.999, slopePermille = 10.0)
        )
        val exact = BrakeCalculator.calculateMass(
            MassCalculationInput(1000.0, manualAxleLoadTons = 10.0, slopePermille = 10.0)
        )
        val above = BrakeCalculator.calculateMass(
            MassCalculationInput(1000.1, manualAxleLoadTons = 10.001, slopePermille = 10.0)
        )
        assertFalse(below.heavyCategory)
        assertTrue(exact.heavyCategory)
        assertTrue(above.heavyCategory)
    }

    @Test
    fun mass_supplement_uses_exact_remaining_shoe_equivalent() {
        val result = BrakeCalculator.calculateMass(
            MassCalculationInput(3800.0, axleCount = 200, slopePermille = 12.0)
        )
        val supplement = BrakeCalculator.calculateMassSupplement(
            result,
            availableShoes = 15,
            axlesPerHandBrakeUnit = 4
        )
        assertEquals(15.2, result.shoesExact, 1e-9)
        assertEquals(16, result.requiredShoes)
        assertEquals(1, supplement.shoeShortage)
        assertEquals(0.2, supplement.remainingShoeEquivalent, 1e-9)
        assertEquals(0.5, supplement.additionalManualAxlesExact, 1e-9)
        assertEquals(1, supplement.additionalManualAxles)
        assertEquals(1, supplement.requiredHandBrakeUnits)
    }

    @Test
    fun consist_771t_34axles_10permille() {
        val result = BrakeCalculator.calculateMass(
            MassCalculationInput(771.0, axleCount = 34, slopePermille = 10.0)
        )
        assertEquals(3, result.requiredShoes)
        assertEquals(7, result.fullManualBrakeAxles)
    }

    @Test
    fun appendix_formula1_with_oil_and_wind() {
        val result = BrakeCalculator.calculateAppendix12(
            Appendix12Input(
                axleCount = 200,
                profileMode = ProfileMode.NORMAL,
                slopePermille = 8.0,
                formula = AppendixFormula.FORMULA_1,
                oilyRails = true,
                windSpeedMs = 16.0,
                windDirectionMatchesPossibleMovement = true,
                availableShoes = 15,
                axlesPerHandBrakeUnit = 4
            )
        )
        assertEquals(23, result.totalRequiredShoes)
        assertEquals(8, result.shoeShortage)
        assertEquals(40, result.substituteBrakeAxles)
        assertEquals(10, result.requiredHandBrakeUnits)
    }

    @Test
    fun appendix_under_half_permille_can_use_one_parking_brake_for_base_pair() {
        val result = BrakeCalculator.calculateAppendix12(
            Appendix12Input(
                axleCount = 200,
                profileMode = ProfileMode.NORMAL,
                slopePermille = 0.0,
                formula = null,
                oilyRails = false,
                windSpeedMs = 0.0,
                windDirectionMatchesPossibleMovement = false,
                availableShoes = 0,
                axlesPerHandBrakeUnit = 4
            )
        )
        assertEquals(2, result.totalRequiredShoes)
        assertTrue(result.specialUnderHalfPermilleRuleUsed)
        assertEquals(1, result.requiredHandBrakeUnits)
        assertEquals(0, result.substituteBrakeAxles)
        assertEquals(0, result.extraShoesAfterSpecialRule)
    }

    @Test
    fun horizontal_special_rule_does_not_absorb_wind_shortage() {
        val result = BrakeCalculator.calculateAppendix12(
            Appendix12Input(
                axleCount = 200,
                profileMode = ProfileMode.NORMAL,
                slopePermille = 0.0,
                formula = null,
                oilyRails = false,
                windSpeedMs = 16.0,
                windDirectionMatchesPossibleMovement = true,
                availableShoes = 0,
                axlesPerHandBrakeUnit = 4
            )
        )
        assertTrue(result.specialUnderHalfPermilleRuleUsed)
        assertEquals(3, result.windShoes)
        assertEquals(3, result.extraShoesAfterSpecialRule)
        assertEquals(15, result.substituteBrakeAxles)
        assertEquals(5, result.requiredHandBrakeUnits)
        assertEquals(20, result.actuallyBrakedAxles)
        assertEquals(1, result.reserveBrakeAxles)
    }

    @Test
    fun appendix_exactly_half_permille_does_not_use_special_parking_brake_rule() {
        val result = BrakeCalculator.calculateAppendix12(
            Appendix12Input(
                axleCount = 200,
                profileMode = ProfileMode.NORMAL,
                slopePermille = 0.5,
                formula = null,
                oilyRails = false,
                windSpeedMs = 0.0,
                windDirectionMatchesPossibleMovement = false,
                availableShoes = 0,
                axlesPerHandBrakeUnit = 4
            )
        )
        assertFalse(result.specialUnderHalfPermilleRuleUsed)
        assertEquals(10, result.substituteBrakeAxles)
        assertEquals(3, result.requiredHandBrakeUnits)
    }

    @Test
    fun slope_boundaries_are_handled_without_gaps() {
        fun calc(slope: Double): Appendix12Result = BrakeCalculator.calculateAppendix12(
            Appendix12Input(
                axleCount = 200,
                profileMode = ProfileMode.NORMAL,
                slopePermille = slope,
                formula = if (slope <= 0.5) null else AppendixFormula.FORMULA_1,
                oilyRails = false,
                windSpeedMs = 0.0,
                windDirectionMatchesPossibleMovement = false,
                availableShoes = 100,
                axlesPerHandBrakeUnit = 4
            )
        )
        assertTrue(calc(0.499).isLowSlopeRule)
        assertTrue(calc(0.500).isLowSlopeRule)
        assertFalse(calc(0.501).isLowSlopeRule)
        assertEquals(1, calc(1.000).oppositeSideShoes)
        assertEquals(0, calc(1.001).oppositeSideShoes)
    }

    @Test
    fun wind_boundaries_are_strict() {
        fun wind(speed: Double): Int = BrakeCalculator.calculateAppendix12(
            Appendix12Input(200, ProfileMode.NORMAL, 8.0, AppendixFormula.FORMULA_1, false, speed, true, 100, 4)
        ).windCoefficientPer200Axles
        assertEquals(0, wind(15.0))
        assertEquals(3, wind(15.0001))
        assertEquals(3, wind(21.0))
        assertEquals(7, wind(21.0001))
    }

    @Test
    fun wind_rounding_uses_actual_axle_count() {
        fun windShoes(speed: Double): Int = BrakeCalculator.calculateAppendix12(
            Appendix12Input(201, ProfileMode.NORMAL, 8.0, AppendixFormula.FORMULA_1, false, speed, true, 100, 4)
        ).windShoes
        assertEquals(4, windShoes(16.0))
        assertEquals(8, windShoes(22.0))
    }
}

