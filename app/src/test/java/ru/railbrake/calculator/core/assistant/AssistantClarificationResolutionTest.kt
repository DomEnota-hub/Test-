package ru.railbrake.calculator.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

class AssistantClarificationResolutionTest {

    @Test
    fun explicitDiagnosticsChoiceBecomesTroubleshootIntent() {
        val parsed = AssistantQueryParser.parse("диагностика ГВ ВЛ80С")

        assertEquals(AssistantIntent.TROUBLESHOOT, parsed.intent)
        assertEquals(TechnicalFamily.VL80S, parsed.family)
        assertEquals(TechnicalSection.DIAGNOSTICS, parsed.preferredSection)
        assertNull(parsed.ambiguity)
    }

    @Test
    fun explicitDescriptionChoiceBecomesReferenceIntent() {
        val parsed = AssistantQueryParser.parse("описание ГВ ВЛ80С")

        assertEquals(AssistantIntent.DEFINE_TERM, parsed.intent)
        assertEquals(TechnicalSection.EQUIPMENT, parsed.preferredSection)
        assertNull(parsed.ambiguity)
    }

    @Test
    fun explicitBrakeTestTypeDoesNotAskTypeAgain() {
        val parsed = AssistantQueryParser.parse("полная проба тормозов")

        assertEquals(AssistantIntent.PROCEDURE, parsed.intent)
        assertNull(parsed.ambiguity)
    }
}
