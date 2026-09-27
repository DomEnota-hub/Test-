package ru.railbrake.calculator.core.assistant

/** Bounds microphone capture independently of the UI or recognizer. */
internal object AssistantVoicePolicy {
    const val SAMPLE_RATE = 16_000
    const val WINDOW_SAMPLES = 512
    const val MAX_SECONDS = 18
    const val MAX_SAMPLES = SAMPLE_RATE * MAX_SECONDS
    const val MIN_SPEECH_SAMPLES = SAMPLE_RATE / 4

    fun shouldFinish(recordedSamples: Int, speechSegments: Int, stopRequested: Boolean): Boolean =
        stopRequested || speechSegments > 0 || recordedSamples >= MAX_SAMPLES

    fun usableSpeech(samples: FloatArray): Boolean = samples.size >= MIN_SPEECH_SAMPLES

    fun transcriptOrNull(text: String): String? = text.trim().takeIf { it.isNotEmpty() }
}
