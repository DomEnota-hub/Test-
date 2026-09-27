package ru.railbrake.calculator.core.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicBoolean

internal enum class AssistantVoicePhase { PREPARING, LISTENING, TRANSCRIBING }

internal sealed interface AssistantVoiceOutcome {
    data class Transcript(val text: String) : AssistantVoiceOutcome
    data object NoSpeech : AssistantVoiceOutcome
}

/** One foreground push-to-talk session. The model never receives network access or app data. */
internal class AssistantVoiceInput(private val context: Context) {
    private val stopRequested = AtomicBoolean(false)
    private var recognizer: OfflineRecognizer? = null

    fun stop() { stopRequested.set(true) }

    /** Called only after the active capture job completes. */
    fun close() {
        stop()
        recognizer?.release()
        recognizer = null
    }

    suspend fun capture(onPhase: suspend (AssistantVoicePhase) -> Unit): AssistantVoiceOutcome =
        withContext(Dispatchers.IO) {
            check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) { "Нет разрешения на микрофон" }
            stopRequested.set(false)
            onPhase(AssistantVoicePhase.PREPARING)
            val vad = Vad(
                assetManager = context.assets,
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = "assistant_voice/silero_vad.onnx",
                        minSilenceDuration = 0.8f,
                        minSpeechDuration = 0.25f,
                        maxSpeechDuration = AssistantVoicePolicy.MAX_SECONDS.toFloat(),
                        windowSize = AssistantVoicePolicy.WINDOW_SAMPLES
                    ),
                    sampleRate = AssistantVoicePolicy.SAMPLE_RATE,
                    numThreads = 1
                )
            )
            try {
                val samples = record(vad, onPhase)
                coroutineContext.ensureActive()
                if (!AssistantVoicePolicy.usableSpeech(samples)) return@withContext AssistantVoiceOutcome.NoSpeech
                onPhase(AssistantVoicePhase.TRANSCRIBING)
                val asr = recognizer ?: createRecognizer().also { recognizer = it }
                coroutineContext.ensureActive()
                val stream = asr.createStream()
                try {
                    stream.acceptWaveform(samples, AssistantVoicePolicy.SAMPLE_RATE)
                    asr.decode(stream)
                    val text = AssistantVoicePolicy.transcriptOrNull(asr.getResult(stream).text)
                    if (text == null) AssistantVoiceOutcome.NoSpeech else AssistantVoiceOutcome.Transcript(text)
                } finally {
                    stream.release()
                }
            } finally {
                vad.release()
            }
        }

    private fun createRecognizer(): OfflineRecognizer {
        val model = "assistant_voice/sherpa-onnx-zipformer-ru-int8-2025-04-20"
        return OfflineRecognizer(
            assetManager = context.assets,
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = "$model/encoder.int8.onnx",
                        decoder = "$model/decoder.onnx",
                        joiner = "$model/joiner.int8.onnx"
                    ),
                    tokens = "$model/tokens.txt",
                    modelType = "transducer",
                    numThreads = 2
                )
            )
        )
    }

    private suspend fun record(
        vad: Vad,
        onPhase: suspend (AssistantVoicePhase) -> Unit
    ): FloatArray {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) { "Нет разрешения на микрофон" }
        val minBytes = AudioRecord.getMinBufferSize(
            AssistantVoicePolicy.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBytes > 0) { "Микрофон не поддерживает запись 16 кГц" }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AssistantVoicePolicy.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBytes * 2, AssistantVoicePolicy.WINDOW_SAMPLES * 4)
        )
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Не удалось открыть микрофон" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Микрофон занят" }
            onPhase(AssistantVoicePhase.LISTENING)
            val pcm = ShortArray(AssistantVoicePolicy.WINDOW_SAMPLES)
            var recorded = 0
            var buffered = 0
            val utterance = ArrayList<Float>()
            // VAD returns speech with its own leading padding. Keep only the first utterance.
            while (true) {
                coroutineContext.ensureActive()
                if (AssistantVoicePolicy.shouldFinish(recorded, if (vad.empty()) 0 else 1, stopRequested.get())) break
                val count = recorder.read(pcm, buffered, pcm.size - buffered, AudioRecord.READ_BLOCKING)
                check(count >= 0) { "Ошибка чтения микрофона: $count" }
                if (count == 0) continue
                recorded += count
                buffered += count
                if (buffered == pcm.size) {
                    vad.acceptWaveform(FloatArray(pcm.size) { pcm[it] / 32768f })
                    buffered = 0
                }
            }
            vad.flush()
            while (!vad.empty()) {
                val segment = vad.front().samples
                for (sample in segment) utterance.add(sample)
                vad.pop()
            }
            return FloatArray(utterance.size) { utterance[it] }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }
}
