package ai.whatyousay.feature.onboarding

import ai.whatyousay.WhatYouSayApp
import ai.whatyousay.core.Language
import ai.whatyousay.core.Languages
import ai.whatyousay.engine.DeviceTier
import ai.whatyousay.engine.ModelCatalog
import ai.whatyousay.engine.ModelInstallPlan
import ai.whatyousay.engine.PackStatus
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The first-run steps, in order. */
enum class OnboardingStep { WELCOME, TIER, LANGUAGES, PACKS, PERMISSION }

data class OnboardingUiState(
    val step: OnboardingStep,
    val detectedTier: DeviceTier,
    val selectedTier: DeviceTier,
    val allLanguages: List<Language>,
    val selectedLanguages: Set<String>,
    val packs: List<PackStatus>,
    val overallProgress: Float,
    val simulated: Boolean,
    val micGranted: Boolean,
) {
    val busy: Boolean get() = packs.any { it.busy }
    val canContinueLanguages: Boolean get() = selectedLanguages.size >= 2
}

/**
 * Drives the first-run flow: detect the device tier, let the user pick a quality tier
 * and the languages they care about, install the model packs for that tier, and ask
 * for the microphone.
 *
 * Downloads run through the app container's [ai.whatyousay.engine.ModelManager]. On the
 * default build that is the [ai.whatyousay.engine.SimulatedModelManager], so the whole
 * flow, including per-pack progress and sha256 verification, is demonstrable with no
 * real pack URLs and no network. The pure [ModelInstallPlan] holds the per-pack state
 * so the transitions are unit tested off-device.
 */
class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as WhatYouSayApp).container

    private var plan = ModelInstallPlan(
        ModelCatalog.defaultsFor(container.detectedTier),
        container.modelManager,
    )

    private val jobs = mutableMapOf<String, Job>()

    private val _state = MutableStateFlow(
        OnboardingUiState(
            step = OnboardingStep.WELCOME,
            detectedTier = container.detectedTier,
            selectedTier = container.detectedTier,
            allLanguages = Languages.all,
            selectedLanguages = container.settings.languageCodes,
            packs = plan.statuses(),
            overallProgress = plan.overallProgress,
            simulated = !container.packsConfigured,
            micGranted = hasMicPermission(),
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun goTo(step: OnboardingStep) = _state.value.let { _state.value = it.copy(step = step) }

    fun next() {
        val order = OnboardingStep.entries
        val idx = order.indexOf(_state.value.step)
        if (idx < order.lastIndex) goTo(order[idx + 1])
    }

    fun back() {
        val order = OnboardingStep.entries
        val idx = order.indexOf(_state.value.step)
        if (idx > 0) goTo(order[idx - 1])
    }

    fun selectTier(tier: DeviceTier) {
        if (tier == _state.value.selectedTier) return
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        plan = ModelInstallPlan(ModelCatalog.defaultsFor(tier), container.modelManager)
        _state.update { it.copy(selectedTier = tier) }
        emitPlan(plan)
    }

    fun toggleLanguage(code: String) {
        val current = _state.value.selectedLanguages
        val updated = if (code in current) current - code else current + code
        _state.value = _state.value.copy(selectedLanguages = updated)
    }

    fun downloadPack(packId: String) {
        val pack = ModelCatalog.byId(packId) ?: return
        if (jobs.containsKey(packId)) return
        // Pin the plan this job reports into, so a tier switch that swaps the plan
        // mid-download cannot make the background progress callback touch the new one.
        val activePlan = plan
        activePlan.queued(packId)
        emitPlan(activePlan)
        jobs[packId] = viewModelScope.launch {
            val result = container.modelManager.download(pack) { progress ->
                if (progress < 1f) activePlan.downloading(packId, progress) else activePlan.verifying(packId)
                emitPlan(activePlan)
            }
            if (result.isSuccess) {
                activePlan.installed(packId, container.modelManager.installedSha(pack))
            } else {
                activePlan.failed(packId, result.exceptionOrNull()?.message ?: "Download failed")
            }
            emitPlan(activePlan)
            jobs.remove(packId)
        }
    }

    fun downloadAll() {
        _state.value.packs.filterNot { it.installed || it.busy }.forEach { downloadPack(it.pack.id) }
    }

    fun removePack(packId: String) {
        val pack = ModelCatalog.byId(packId) ?: return
        jobs.remove(packId)?.cancel()
        container.modelManager.remove(pack)
        plan.absent(packId)
        emitPlan(plan)
    }

    fun onMicPermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(micGranted = granted)
    }

    /** Persist the choices and mark onboarding done. The screen handles navigation. */
    fun finish() {
        val selected = _state.value.selectedLanguages
        container.settings.tier = _state.value.selectedTier
        container.settings.languageCodes = selected
        val ordered = Languages.all.map { it.code }.filter { it in selected }
        container.settings.sourceCode = ordered.getOrElse(0) { Languages.EN.code }
        container.settings.targetCode = ordered.getOrNull(1) ?: ordered.firstOrNull() ?: Languages.ES.code
        container.settings.onboardingComplete = true
    }

    /**
     * Publish [source]'s status into the UI, ignoring callbacks from a plan that a tier
     * switch has since replaced. [MutableStateFlow.update] keeps the read-modify-write
     * atomic across the per-pack download coroutines.
     */
    private fun emitPlan(source: ModelInstallPlan) {
        if (source !== plan) return
        _state.update { it.copy(packs = source.statuses(), overallProgress = source.overallProgress) }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
