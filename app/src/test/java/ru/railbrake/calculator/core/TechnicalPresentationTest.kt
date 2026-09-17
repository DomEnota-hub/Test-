package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalPresentationTest {
    @Test
    fun internalReferencesAreRecognized() {
        assertTrue(isInternalTechnicalReference("VL-EQ-SF-001"))
        assertTrue(isInternalTechnicalReference("VL80-ACC-005"))
        assertTrue(isInternalTechnicalReference("ER-EQ-014"))
        assertFalse(isInternalTechnicalReference("ЭПК и контроль бдительности"))
    }

    @Test
    fun embeddedTechnicalTermsAreHumanized() {
        assertEquals(
            "Проверить выбранный профиль исполнения и открыть связанные карточки оборудования и справочные материалы.",
            userFacingTechnicalText("Проверить выбранный variant-profile и открыть связанные ER-EQ/KB карточки.")
        )
        assertEquals("Проверить связанную схему", userFacingTechnicalText("Проверить связанную VL-SCH-BR-EPK схему"))
    }
}
