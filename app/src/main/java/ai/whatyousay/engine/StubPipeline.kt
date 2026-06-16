package ai.whatyousay.engine

import ai.whatyousay.core.Language
import ai.whatyousay.core.Languages
import ai.whatyousay.core.Transcriber
import ai.whatyousay.core.Transcription
import ai.whatyousay.core.Translator
import ai.whatyousay.core.TranslationResult
import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Synthesizer
import ai.whatyousay.core.TranslationPipeline

/**
 * A fully local stub pipeline so the UI runs, previews, and tests before the
 * native models are wired. It is deterministic, has no dependencies, and never
 * touches the network or the microphone. Real engines drop in behind the same
 * interfaces in Phase 1.
 */

private val DEMO_DICTIONARY: Map<String, Map<String, String>> = mapOf(
    "hello" to mapOf("es" to "hola", "fr" to "bonjour", "de" to "hallo", "ja" to "konnichiwa"),
    "thank you" to mapOf("es" to "gracias", "fr" to "merci", "de" to "danke", "ja" to "arigato"),
    "where is the station" to mapOf("es" to "donde esta la estacion", "fr" to "ou est la gare"),
    "how much is this" to mapOf("es" to "cuanto cuesta esto", "fr" to "combien ca coute"),
)

class StubTranscriber : Transcriber {
    override suspend fun transcribe(samples: ShortArray, sampleRate: Int, hint: Language?): Transcription =
        Transcription(text = "hello", language = hint ?: Languages.EN, isFinal = true, confidence = 0.9f)

    override fun detectLanguage(text: String): Language? {
        val lower = text.lowercase()
        return when {
            DEMO_DICTIONARY.keys.any { lower.contains(it) } -> Languages.EN
            else -> null
        }
    }
}

class StubTranslator : Translator {
    override val supported: Set<String> = Languages.all.map { it.code }.toSet()

    override suspend fun translate(text: String, pair: LanguagePair): TranslationResult {
        val key = text.trim().lowercase()
        val hit = DEMO_DICTIONARY[key]?.get(pair.target.code)
        val out = hit ?: "[${pair.target.code}] $text"
        return TranslationResult(sourceText = text, translatedText = out, pair = pair)
    }
}

class StubSynthesizer : Synthesizer {
    override suspend fun synthesize(text: String, language: Language, sampleRate: Int): ShortArray {
        // Silent PCM sized to a rough speaking duration. Real TTS replaces this.
        val durationSeconds = (text.length / 12.0).coerceIn(0.3, 8.0)
        return ShortArray((sampleRate * durationSeconds).toInt())
    }
}

fun buildStubPipeline(): TranslationPipeline =
    TranslationPipeline(StubTranscriber(), StubTranslator(), StubSynthesizer())
