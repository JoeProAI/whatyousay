package ai.whatyousay.core

/** A speech-to-text result. `isFinal` distinguishes streaming partials from the committed transcript. */
data class Transcription(
    val text: String,
    val language: Language?,
    val isFinal: Boolean,
    val confidence: Float,
)

/** A completed translation, carrying the pair it was produced under for display and logging. */
data class TranslationResult(
    val sourceText: String,
    val translatedText: String,
    val pair: LanguagePair,
)

/**
 * The three stages of the local pipeline, as interfaces so the engine layer can
 * swap a stub for a real native model without the UI noticing. All on-device.
 *
 * Each stage is [AutoCloseable] so a real engine can release its native handle (the
 * llama.cpp context, the sherpa-onnx recognizer or TTS) when the pipeline is torn
 * down. Stubs hold no native resources, so the default [close] is a no-op and they
 * need no change.
 */

interface Transcriber : AutoCloseable {
    /** Transcribe a PCM chunk. `hint` biases language detection when the speaker is known. */
    suspend fun transcribe(samples: ShortArray, sampleRate: Int, hint: Language?): Transcription

    /** Best-effort language identification from text alone, used when audio detection is ambiguous. */
    fun detectLanguage(text: String): Language?

    override fun close() {}
}

interface Translator : AutoCloseable {
    suspend fun translate(text: String, pair: LanguagePair): TranslationResult

    /** ISO codes this translator can handle. Used to gate unsupported pairs in the UI. */
    val supported: Set<String>

    override fun close() {}
}

interface Synthesizer : AutoCloseable {
    /** Render text to 16-bit PCM mono at the given sample rate. */
    suspend fun synthesize(text: String, language: Language, sampleRate: Int): ShortArray

    override fun close() {}
}

/**
 * Convenience bundle of a configured pipeline. Closing it releases every stage,
 * each independently so one stage failing to close cannot leave another's native
 * handle open. Closing a stub pipeline is a no-op.
 *
 * The transcriber can be swapped in place: Whisper is loaded forced to a single
 * source language, so changing the conversation's source rebuilds only the STT
 * engine and leaves the (heavier) translator and synthesizer loaded.
 */
class TranslationPipeline(
    transcriber: Transcriber,
    val translator: Translator,
    val synthesizer: Synthesizer,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false

    var transcriber: Transcriber = transcriber
        private set

    /**
     * Replace the transcriber, releasing the previous one. If the pipeline is already
     * closed, the incoming engine is released instead of installed, so a rebuild that
     * lands after teardown cannot leak its native handle.
     */
    fun swapTranscriber(next: Transcriber) {
        synchronized(lock) {
            if (closed) {
                runCatching { next.close() }
                return
            }
            val previous = transcriber
            transcriber = next
            if (previous !== next) runCatching { previous.close() }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            runCatching { transcriber.close() }
            runCatching { translator.close() }
            runCatching { synthesizer.close() }
        }
    }
}
