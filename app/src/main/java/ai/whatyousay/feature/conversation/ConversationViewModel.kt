package ai.whatyousay.feature.conversation

import ai.whatyousay.WhatYouSayApp
import ai.whatyousay.core.Language
import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Languages
import ai.whatyousay.engine.AudioTrackSpeaker
import ai.whatyousay.engine.ConversationController
import ai.whatyousay.engine.ConversationState
import ai.whatyousay.engine.EngineReadiness
import ai.whatyousay.engine.PushToTalkRecorder
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the conversation screen renders: the turn loop state plus screen-local bits. */
data class ConversationUiState(
    val conversation: ConversationState,
    val availableLanguages: List<Language>,
    val micGranted: Boolean,
    val recording: Boolean,
) {
    /** Hands-free auto-VAD needs the native voice engine; push-to-talk works without it. */
    val canHandsFree: Boolean get() = conversation.readiness.stt
}

/**
 * Drives the [ConversationController] for the screen and owns push-to-talk capture.
 *
 * The pipeline comes from the app container, which builds it through PipelineFactory,
 * so the same screen runs on the stub engines today and on the native engines once
 * they are installed, with no UI change. Business logic lives in the controller and
 * the pure ConversationEngine; this class only adapts them to Compose state and the
 * Android microphone and foreground service.
 */
class ConversationViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as WhatYouSayApp).container

    private val startPair = initialPair()

    // Whisper is loaded forced to the source language so it never mis-detects the
    // spoken language (for example hearing French as Arabic) and poisons the turn.
    private val resolution = container.resolvePipeline(startPair.source.code)

    private val controller = ConversationController(
        pipeline = resolution.pipeline,
        initialPair = startPair,
        speaker = AudioTrackSpeaker(),
        readiness = EngineReadiness(resolution.mtReal, resolution.sttReal, resolution.ttsReal),
    )

    private val recorder = PushToTalkRecorder()

    // Every language is always selectable here; the onboarding pick only prioritizes
    // which packs to download, it does not restrict what the user can translate.
    private val availableLanguages: List<Language> = Languages.all.sortedBy { it.name }

    private val micGranted = MutableStateFlow(hasMicPermission())
    private val recording = MutableStateFlow(false)

    val uiState: StateFlow<ConversationUiState> =
        combine(controller.state, micGranted, recording) { conv, mic, rec ->
            ConversationUiState(conv, availableLanguages, mic, rec)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConversationUiState(
                conversation = controller.state.value,
                availableLanguages = availableLanguages,
                micGranted = micGranted.value,
                recording = false,
            ),
        )

    fun setSource(language: Language) {
        updatePair(LanguagePair(language, controller.state.value.pair.target))
        rebuildTranscriberForSource()
    }

    fun setTarget(language: Language) = updatePair(LanguagePair(controller.state.value.pair.source, language))

    fun swap() {
        controller.swap()
        persistPair()
        rebuildTranscriberForSource()
    }

    fun submitText(text: String) {
        viewModelScope.launch { controller.submitText(text) }
    }

    fun onMicPermissionResult(granted: Boolean) {
        micGranted.value = granted
    }

    fun startPushToTalk() {
        recording.value = true
        controller.startListening()
        // Capture real audio only when the mic is granted; without it the demo still
        // produces a turn from an empty segment so the button is never a dead end.
        if (micGranted.value) recorder.start(viewModelScope)
    }

    fun stopPushToTalk() {
        if (!recording.value) return
        recording.value = false
        viewModelScope.launch {
            val pcm = if (micGranted.value) recorder.stop() else ShortArray(0)
            controller.submitAudio(pcm, hint = controller.state.value.pair.source)
            controller.stopListening()
        }
    }

    fun toggleHandsFree() {
        val current = controller.state.value
        if (current.handsFree) {
            container.stopHandsFree()
            controller.setHandsFree(false)
        } else {
            if (!current.readiness.stt || !micGranted.value) return
            container.startHandsFree(current.pair)
            controller.setHandsFree(true)
        }
    }

    fun dismissError() = controller.clearError()

    private fun updatePair(pair: LanguagePair) {
        controller.setPair(pair)
        persistPair()
    }

    // Reload Whisper for the new source language so recognition stays locked to what
    // the speaker actually selected. No-op on the stub build (buildTranscriber is null).
    // The controller serializes rebuilds and settles on the latest source, so rapid
    // switches are safe.
    private fun rebuildTranscriberForSource() {
        val source = controller.state.value.pair.source.code
        viewModelScope.launch {
            controller.rebuildTranscriber {
                withContext(Dispatchers.Default) { container.buildTranscriber(source) }
            }
        }
    }

    private fun persistPair() {
        val pair = controller.state.value.pair
        container.settings.sourceCode = pair.source.code
        container.settings.targetCode = pair.target.code
    }

    private fun initialPair(): LanguagePair {
        val source = Languages.byCode(container.settings.sourceCode) ?: Languages.EN
        val target = Languages.byCode(container.settings.targetCode) ?: Languages.ES
        return if (source.code == target.code) LanguagePair(Languages.EN, Languages.ES) else LanguagePair(source, target)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        if (controller.state.value.handsFree) container.stopHandsFree()
        controller.close()
        super.onCleared()
    }
}
