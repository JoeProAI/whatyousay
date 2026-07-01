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
import java.util.concurrent.atomic.AtomicInteger

/**
 * A fully local stub pipeline so the UI runs, previews, and tests before the
 * native models are wired. It is deterministic, has no dependencies, and never
 * touches the network or the microphone. Real engines drop in behind the same
 * interfaces in Phase 1.
 */

private val DEMO_DICTIONARY: Map<String, Map<String, String>> = mapOf(
    "hello" to mapOf(
        "es" to "hola", "fr" to "bonjour", "de" to "hallo", "it" to "ciao", "pt" to "ola",
        "ja" to "konnichiwa", "ko" to "annyeong", "zh" to "nihao", "ru" to "privet",
    ),
    "thank you" to mapOf(
        "es" to "gracias", "fr" to "merci", "de" to "danke", "it" to "grazie", "pt" to "obrigado",
        "ja" to "arigato", "ko" to "gamsahamnida", "zh" to "xiexie", "ru" to "spasibo",
    ),
    "where is the station" to mapOf(
        "es" to "donde esta la estacion", "fr" to "ou est la gare", "de" to "wo ist der bahnhof",
        "it" to "dov'e la stazione", "pt" to "onde fica a estacao",
    ),
    "how much is this" to mapOf(
        "es" to "cuanto cuesta esto", "fr" to "combien ca coute", "de" to "wie viel kostet das",
        "it" to "quanto costa questo", "pt" to "quanto custa isto",
    ),
)

// Rotated through on each captured segment so the demo does not repeat one phrase.
private val DEMO_PHRASES: List<String> = DEMO_DICTIONARY.keys.toList()

class StubTranscriber : Transcriber {
    private val next = AtomicInteger(0)

    override suspend fun transcribe(samples: ShortArray, sampleRate: Int, hint: Language?): Transcription {
        val key = DEMO_PHRASES[(next.getAndIncrement() % DEMO_PHRASES.size + DEMO_PHRASES.size) % DEMO_PHRASES.size]
        // Speak the demo phrase in the hinted language so the recognized text reads
        // as that language, matching real STT and keeping direction detection honest;
        // falls back to the English phrase for the demo.
        val language = hint ?: Languages.EN
        val phrase = if (language.code == "en") key else DEMO_DICTIONARY[key]?.get(language.code) ?: key
        return Transcription(text = phrase, language = language, isFinal = true, confidence = 0.9f)
    }

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
