package ai.whatyousay.engine

import ai.whatyousay.core.ConvStatus
import ai.whatyousay.core.ConversationEngine
import ai.whatyousay.core.Language
import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Transcriber
import ai.whatyousay.core.TranslationPipeline
import ai.whatyousay.core.Turn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Whether each stage resolved to a real native engine or to its stub. */
data class EngineReadiness(val mt: Boolean, val stt: Boolean, val tts: Boolean) {
    val allReal: Boolean get() = mt && stt && tts
}

/** Everything the conversation screen renders. Held in one immutable snapshot. */
data class ConversationState(
    val status: ConvStatus,
    val pair: LanguagePair,
    val partial: String = "",
    val turns: List<Turn> = emptyList(),
    val handsFree: Boolean = false,
    val readiness: EngineReadiness = EngineReadiness(false, false, false),
    val error: String? = null,
)

/**
 * Runs the conversation turn loop over a [TranslationPipeline], driving the pure
 * [ConversationEngine] and publishing a single [ConversationState] for the UI.
 *
 * It runs the identical sequence as [ConversationService.onSegment] (transcribe,
 * pick direction, translate, commit, synthesize, speak), so the same screen works
 * unchanged once the native engines drop in behind the pipeline. With the stub
 * pipeline the loop runs end to end with no models and no microphone: typed text
 * skips transcription, and a captured segment runs through the stub transcriber.
 *
 * It touches no Android types of its own (playback goes through [Speaker]), so the
 * loop is unit tested on the JVM.
 */
class ConversationController(
    private val pipeline: TranslationPipeline,
    initialPair: LanguagePair,
    private val speaker: Speaker = NoopSpeaker(),
    readiness: EngineReadiness = EngineReadiness(false, false, false),
    private val sampleRate: Int = 16_000,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AutoCloseable {

    private val engine = ConversationEngine(initialPair, clock)
    private val mutex = Mutex()

    /** A turn that runs longer than this is treated as failed so the UI recovers. */
    private val turnTimeoutMs = 45_000L

    private val _state = MutableStateFlow(
        ConversationState(status = engine.status, pair = engine.pair, readiness = readiness),
    )
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    fun setPair(pair: LanguagePair) {
        engine.setPair(pair)
        sync()
    }

    fun swap() {
        engine.setPair(engine.pair.swapped())
        sync()
    }

    /**
     * Rebuild the STT engine forced to the current source language. The (heavy) build
     * runs outside the turn lock; only the swap itself is serialized against a running
     * turn so a transcribe can never see a half-swapped pipeline.
     */
    suspend fun rebuildTranscriber(build: suspend () -> Transcriber?) {
        val next = build() ?: return
        mutex.withLock { pipeline.swapTranscriber(next) }
    }

    fun startListening() {
        engine.start()
        sync()
    }

    fun stopListening() {
        engine.stop()
        _state.update { it.copy(status = engine.status, partial = "") }
    }

    fun setHandsFree(on: Boolean) {
        _state.update { it.copy(handsFree = on) }
    }

    /** Treat typed text as a finalized utterance: skip transcription, run the rest. */
    suspend fun submitText(text: String) {
        if (text.isBlank()) return
        mutex.withLock {
            process(heard = text, detected = pipeline.transcriber.detectLanguage(text))
        }
    }

    /** Transcribe a captured PCM segment, then run the rest of the turn. */
    suspend fun submitAudio(samples: ShortArray, hint: Language? = null) {
        mutex.withLock {
            val heard = runCatching { pipeline.transcriber.transcribe(samples, sampleRate, hint) }
                .getOrElse {
                    _state.update { s -> s.copy(error = it.message, status = engine.status, partial = "") }
                    return
                }
            if (heard.text.isBlank()) {
                _state.update { it.copy(partial = "", status = engine.status) }
                return
            }
            process(heard = heard.text, detected = heard.language)
        }
    }

    private suspend fun process(heard: String, detected: Language?) {
        val direction = engine.directionFor(heard, detected) ?: return
        // Show the recognized words forming before the translation lands.
        _state.update { it.copy(status = engine.status, partial = heard, error = null) }
        // Bound the translate call: a wedged or pathologically slow turn can never leave
        // the UI stuck on "translating" forever. A timeout (null) and a real failure
        // (exception) both recover to ready, but keep their messages distinct so an
        // actual engine error is not hidden behind a misleading "timed out".
        val translated = runCatching {
            withTimeoutOrNull(turnTimeoutMs) { pipeline.translator.translate(heard, direction) }
        }
        val result = translated.getOrNull()
        if (result == null) {
            engine.ready()
            val message = translated.exceptionOrNull()?.message ?: "Translation timed out"
            _state.update { it.copy(error = message, status = engine.status, partial = "") }
            return
        }
        engine.commit(result)
        _state.update { it.copy(status = engine.status, partial = "", turns = engine.turns) }
        val pcm = runCatching {
            withTimeoutOrNull(turnTimeoutMs) {
                pipeline.synthesizer.synthesize(result.translatedText, direction.target, sampleRate)
            }
        }.getOrNull()
        if (pcm != null) speaker.speak(pcm, sampleRate)
        engine.ready()
        _state.update { it.copy(status = engine.status) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun sync() {
        _state.update {
            it.copy(status = engine.status, pair = engine.pair, turns = engine.turns)
        }
    }

    override fun close() {
        pipeline.close()
    }
}
