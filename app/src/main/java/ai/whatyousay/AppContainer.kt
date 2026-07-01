package ai.whatyousay

import ai.whatyousay.core.LanguagePair
import ai.whatyousay.data.AppSettings
import ai.whatyousay.engine.ConversationService
import ai.whatyousay.engine.DeviceTier
import ai.whatyousay.engine.FileModelManager
import ai.whatyousay.engine.ModelCatalog
import ai.whatyousay.engine.ModelManager
import ai.whatyousay.engine.NativeVoiceEngines
import ai.whatyousay.engine.PipelineFactory
import ai.whatyousay.engine.SimulatedModelManager
import ai.whatyousay.engine.VoiceEngineFactory
import ai.whatyousay.engine.tierFor
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File

/**
 * Manual dependency container, owned by [WhatYouSayApp] and shared by the screens.
 *
 * It picks the model manager from configuration: the shipping [ModelCatalog] has no
 * real pack URLs (they are filled at release), so until that catalog is configured
 * the app provisions packs through the [SimulatedModelManager] and the UI shows them
 * as simulated. Once real URLs are present it switches to the on-disk
 * [FileModelManager] with no other change. Pipelines are always built through
 * [PipelineFactory], so each stage resolves to a real engine or its stub.
 */
class AppContainer(private val appContext: Context) {

    val settings = AppSettings(appContext)

    val modelRoot: File = File(appContext.filesDir, "models")

    val detectedTier: DeviceTier = tierFor(totalRamBytes(), hasNpu = false)

    /** True when the catalog carries real pack URLs; false on the default stub build. */
    val packsConfigured: Boolean = ModelCatalog.packs.any { it.url.isNotBlank() }

    private val voiceFactory: VoiceEngineFactory? = NativeVoiceEngines.load()

    val modelManager: ModelManager =
        if (packsConfigured) FileModelManager(modelRoot) else SimulatedModelManager()

    /** Resolve a pipeline plus which stages are real, for the chosen tier. */
    fun resolvePipeline(language: String = ""): PipelineFactory.Resolution =
        PipelineFactory.resolve(modelRoot, modelManager, effectiveTier(), language, voiceFactory)

    /** Rebuild only the STT engine, forced to [language], reusing the loaded translator. */
    fun buildTranscriber(language: String) =
        PipelineFactory.buildTranscriber(modelRoot, modelManager, effectiveTier(), language, voiceFactory)

    /** The tier the user picked in onboarding, falling back to the detected one. */
    fun effectiveTier(): DeviceTier =
        if (settings.onboardingComplete) settings.tier else detectedTier

    fun startHandsFree(pair: LanguagePair) {
        val intent = Intent(appContext, ConversationService::class.java).apply {
            putExtra(ConversationService.EXTRA_SOURCE, pair.source.code)
            putExtra(ConversationService.EXTRA_TARGET, pair.target.code)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    fun stopHandsFree() {
        appContext.stopService(Intent(appContext, ConversationService::class.java))
    }

    private fun totalRamBytes(): Long {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }
}
