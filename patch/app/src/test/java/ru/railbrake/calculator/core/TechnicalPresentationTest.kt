package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun rawMachineValuesDoNotLeakIntoCatalog() {
        assertEquals(
            "Действовать по местной инструкции и установленному технологическому процессу",
            technicalPresentationLine("LOCAL_PROCEDURE")
        )
        assertEquals(
            "Применимость определяется по фактическому исполнению секции",
            technicalPresentationLine("section-aware applicability")
        )
        assertEquals("Групповое регулирование", technicalPresentationLine("group_base"))
        assertEquals("3ЭС5К №896 и позднее", technicalPresentationLine("3es5k_896plus"))
        assertEquals("п. 5", technicalPresentationLine("p.5"))
        assertNull(technicalPresentationLine("confirmed_general"))
        assertNull(technicalPresentationLine("motorCompressor"))
        assertNull(technicalPresentationLine("gv"))
    }

    @Test
    fun ermakEnglishRulesAreLocalized() {
        assertEquals(
            "Сначала выбирается исполнение, затем строится подробная схема. Неизвестные параметры не подменяются другим исполнением автоматически.",
            technicalPresentationLine("Variant selection happens before detailed scheme rendering. Unknown dimensions do not silently default to another execution.")
        )
        assertEquals(
            "Бустерная секция применяется только для 3ЭС5К.",
            technicalPresentationLine("booster is only valid for 3ES5K")
        )
    }

    @Test
    fun publicStatusesStayReadable() {
        assertEquals("Остановиться и доложить", technicalStatusPresentation("STOP_AND_REPORT"))
        assertEquals("Требует сверки со схемой секции", technicalStatusPresentation("SECONDARY_SOURCE_REQUIRES_DRAWING"))
        assertNull(technicalStatusPresentation("SOME_NEW_INTERNAL_STATUS"))
    }
}
