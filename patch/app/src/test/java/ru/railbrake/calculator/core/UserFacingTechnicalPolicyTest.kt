package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingTechnicalPolicyTest {
    @Test
    fun recognizesCurrentAndFutureStyleInternalIds() {
        listOf(
            "VL-SCH-BR-EPK",
            "VL80-ACC-005",
            "ER-EQ-014",
            "ER-DIAG-031",
            "CHME3-EQ-DIESEL",
            "CHME3T-EQ-BRAKE-RESISTORS",
            "CHME3E-DIAG-001",
            "CHME3-SRC-NOTIK-1996",
            "CHME3E-INT-TRACTION-CONTROL",
            "SYS-BRAKE",
            "SAFETY-ERMAK-001",
            "route_from_cab"
        ).forEach { value ->
            assertTrue("expected internal id: $value", UserFacingTechnicalPolicy.isInternalReference(value))
        }
    }

    @Test
    fun allowsHumanTechnicalNamesAndDesignations() {
        listOf("ЭПК", "GC40P", "ЧМЭ3Э", "НН106", "КОГ1", "2ЭС5К", "3ЭС5К", "YSH11").forEach { value ->
            assertFalse("human label incorrectly hidden: $value", UserFacingTechnicalPolicy.containsInternalReference(value))
        }
    }

    @Test
    fun detectsEmbeddedIdsAndSanitizesFallbackText() {
        assertTrue(UserFacingTechnicalPolicy.containsInternalReference("Источник CHME3-SRC-NOTIK-1996"))
        assertEquals("Источник", UserFacingTechnicalPolicy.sanitize("Источник CHME3-SRC-NOTIK-1996"))
        assertEquals("Тяговый генератор", UserFacingTechnicalPolicy.sanitize("CHME3-EQ-DIESEL • Тяговый генератор"))
        assertNull(UserFacingTechnicalPolicy.sanitize("CHME3E-DIAG-001"))
    }

    @Test
    fun sourceAndRelationPresentationRequireHumanTitles() {
        val source = TechnicalSourcePresentation(
            title = "Тепловозы ЧМЭ3, ЧМЭ3Т и ЧМЭ3Э",
            documentDetails = "Учебное издание, 1996",
            provenance = "Справочный источник"
        )
        assertEquals("Тепловозы ЧМЭ3, ЧМЭ3Т и ЧМЭ3Э", source.lines().first())

        val relation = TechnicalRelationPresentation(
            fromTitle = "Датчик тока тягового генератора",
            toTitle = "Электронный регулятор GC40P",
            meaning = "Передаёт сигнал обратной связи"
        )
        assertEquals("Датчик тока тягового генератора → Электронный регулятор GC40P", relation.title)
        assertTrue(runCatching {
            TechnicalRelationPresentation("CHME3E-EQ-CURRENT-SENSOR-DTG", "GC40P", "Связь")
        }.isFailure)
    }
}
