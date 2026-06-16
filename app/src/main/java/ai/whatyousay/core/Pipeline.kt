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
 */

interface Transcriber {
    /** Transcribe a PCM chunk. `hint` biases language detection when the speaker is known. */
    suspend fun transcribe(samples: ShortArray, sampleRate: Int, hint: Language?): Transcription

    /** Best-effort language identification from text alone, used when audio detection is ambiguous. */
    fun detectLanguage(text: String): Language?
}

interface Translator {
    suspend fun translate(text: String, pair: LanguagePair): TranslationResult

    /** ISO codes this translator can handle. Used to gate unsupported pairs in the UI. */
    val supported: Set<String>
}

interface Synthesizer {
    /** Render text to 16-bit PCM mono at the given sample rate. */
    suspend fun synthesize(text: String, language: Language, sampleRate: Int): ShortArray
}

/** Convenience bundle of a configured pipeline. */
data class TranslationPipeline(
    val transcriber: Transcriber,
    val translator: Translator,
    val synthesizer: Synthesizer,
)
