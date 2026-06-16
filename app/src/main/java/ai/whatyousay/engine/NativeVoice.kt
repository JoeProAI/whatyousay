package ai.whatyousay.engine

import ai.whatyousay.core.Language
import ai.whatyousay.core.Languages
import ai.whatyousay.core.Synthesizer
import ai.whatyousay.core.Transcriber
import ai.whatyousay.core.Transcription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Speech to text via whisper.cpp over JNI. Native code returns a tab-separated
 * "langCode\ttext" so detection rides along with the transcript. The .so is built
 * from whisper.cpp alongside the llama bridge.
 */
class WhisperTranscriber private constructor(@Volatile private var handle: Long) : Transcriber {

    override suspend fun transcribe(samples: ShortArray, sampleRate: Int, hint: Language?): Transcription =
        withContext(Dispatchers.Default) {
            check(handle != 0L) { "WhisperTranscriber is closed" }
            val raw = nativeTranscribe(handle, samples, sampleRate, hint?.code ?: "")
            val tab = raw.indexOf('\t')
            val langCode = if (tab > 0) raw.substring(0, tab) else ""
            val text = if (tab >= 0) raw.substring(tab + 1) else raw
            Transcription(
                text = text.trim(),
                language = Languages.byCode(langCode),
                isFinal = true,
                confidence = 1f,
            )
        }

    /** Whisper detects language from audio, not text, so text-only detection is a no-op. */
    override fun detectLanguage(text: String): Language? = null

    fun close() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    private external fun nativeTranscribe(handle: Long, samples: ShortArray, sampleRate: Int, langHint: String): String
    private external fun nativeFree(handle: Long)

    companion object {
        init {
            System.loadLibrary("whatyousay_voice")
        }

        @JvmStatic
        private external fun nativeInit(modelPath: String): Long

        fun load(modelPath: String): WhisperTranscriber? {
            val h = nativeInit(modelPath)
            return if (h != 0L) WhisperTranscriber(h) else null
        }
    }
}

/**
 * Text to speech via Piper (or Kokoro) over JNI, returning 16-bit PCM mono. The
 * voice model path is chosen per language by the ModelManager.
 */
class PiperSynthesizer private constructor(@Volatile private var handle: Long) : Synthesizer {

    override suspend fun synthesize(text: String, language: Language, sampleRate: Int): ShortArray =
        withContext(Dispatchers.Default) {
            check(handle != 0L) { "PiperSynthesizer is closed" }
            nativeSynthesize(handle, text, language.code, sampleRate)
        }

    fun close() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    private external fun nativeSynthesize(handle: Long, text: String, langCode: String, sampleRate: Int): ShortArray
    private external fun nativeFree(handle: Long)

    companion object {
        init {
            System.loadLibrary("whatyousay_voice")
        }

        @JvmStatic
        private external fun nativeInit(voiceModelPath: String): Long

        fun load(voiceModelPath: String): PiperSynthesizer? {
            val h = nativeInit(voiceModelPath)
            return if (h != 0L) PiperSynthesizer(h) else null
        }
    }
}
