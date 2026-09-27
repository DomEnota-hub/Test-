package ru.railbrake.calculator.core.assistant

import org.junit.Assert.*
import org.junit.Test

class AssistantVoicePolicyTest {
    @Test fun boundedRecordingAndManualStop() {
        assertFalse(AssistantVoicePolicy.shouldFinish(16_000, 0, false))
        assertTrue(AssistantVoicePolicy.shouldFinish(16_000, 1, false))
        assertTrue(AssistantVoicePolicy.shouldFinish(16_000, 0, true))
        assertTrue(AssistantVoicePolicy.shouldFinish(AssistantVoicePolicy.MAX_SAMPLES, 0, false))
    }

    @Test fun silenceAndBlankTranscriptNeverReachAssistant() {
        assertFalse(AssistantVoicePolicy.usableSpeech(FloatArray(128)))
        assertTrue(AssistantVoicePolicy.usableSpeech(FloatArray(AssistantVoicePolicy.MIN_SPEECH_SAMPLES)))
        assertNull(AssistantVoicePolicy.transcriptOrNull(" \n "))
        assertEquals("ГВ не включается", AssistantVoicePolicy.transcriptOrNull(" ГВ не включается "))
    }
}
