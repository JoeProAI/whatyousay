package ai.whatyousay.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.whatyousay.core.ConvStatus
import ai.whatyousay.core.ConversationEngine
import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Languages
import ai.whatyousay.core.TranslationPipeline
import ai.whatyousay.core.Turn
import ai.whatyousay.engine.buildStubPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationUiState(
    val status: ConvStatus = ConvStatus.IDLE,
    val pair: LanguagePair,
    val turns: List<Turn> = emptyList(),
    val error: String? = null,
)

/**
 * Drives the ConversationEngine with a pipeline. Defaults to the stub pipeline so
 * the screen runs today; swap in LlamaTranslator + WhisperTranscriber + PiperSynthesizer
 * for Phase 1 without touching the UI.
 */
class ConversationViewModel(
    private val pipeline: TranslationPipeline = buildStubPipeline(),
) : ViewModel() {

    private val engine = ConversationEngine(
        initialPair = LanguagePair(Languages.EN, Languages.ES),
        clock = { System.currentTimeMillis() },
    )

    private val _state = MutableStateFlow(ConversationUiState(pair = engine.pair))
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    fun toggleListening() {
        if (engine.status == ConvStatus.IDLE) engine.start() else engine.stop()
        sync()
    }

    fun swap() {
        engine.setPair(engine.pair.swapped())
        sync()
    }

    fun setPair(pair: LanguagePair) {
        engine.setPair(pair)
        sync()
    }

    /**
     * Demo path: feed text as if it had been transcribed. In Phase 1 the mic loop
     * calls the same sequence with whisper output, so the flow is identical.
     */
    fun submitUtterance(text: String) {
        val direction = engine.directionFor(text, pipeline.transcriber.detectLanguage(text)) ?: return
        sync()
        viewModelScope.launch {
            runCatching { pipeline.translator.translate(text, direction) }
                .onSuccess {
                    engine.commit(it)
                    engine.ready()
                    sync()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message, status = engine.status) } }
        }
    }

    private fun sync() {
        _state.update {
            it.copy(status = engine.status, pair = engine.pair, turns = engine.turns, error = null)
        }
    }
}
