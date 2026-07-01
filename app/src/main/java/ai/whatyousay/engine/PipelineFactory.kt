package ai.whatyousay.engine

import ai.whatyousay.core.Languages
import ai.whatyousay.core.Synthesizer
import ai.whatyousay.core.Transcriber
import ai.whatyousay.core.TranslationPipeline
import ai.whatyousay.core.Translator
import android.util.Log
import java.io.File

/**
 * Assembles a TranslationPipeline from whatever is actually present on the device,
 * degrading each stage independently to its stub.
 *
 * With no native libraries and no installed model packs (the default build and CI),
 * every stage resolves to a stub, so the app builds and runs exactly as it does today.
 * As the MT GGUF and the sherpa-onnx voice packs get installed, each real engine
 * replaces its stub with no other code change. A failure to load any one engine (for
 * example a missing .so) falls back to that stage's stub rather than crashing.
 */
object PipelineFactory {

    data class Resolution(
        val pipeline: TranslationPipeline,
        val mtReal: Boolean,
        val sttReal: Boolean,
        val ttsReal: Boolean,
    )

    fun build(
        modelRoot: File,
        manager: ModelManager,
        tier: DeviceTier,
        language: String = "",
        voiceFactory: VoiceEngineFactory? = NativeVoiceEngines.load(),
    ): TranslationPipeline = resolve(modelRoot, manager, tier, language, voiceFactory).pipeline

    fun resolve(
        modelRoot: File,
        manager: ModelManager,
        tier: DeviceTier,
        language: String = "",
        voiceFactory: VoiceEngineFactory? = NativeVoiceEngines.load(),
    ): Resolution {
        val translator = loadTranslator(manager, tier)
        val transcriber = voiceFactory?.let { loadTranscriber(it, modelRoot, manager, tier, language) }
        val synthesizer = voiceFactory?.let { loadSynthesizer(it, modelRoot, manager, tier) }
        return Resolution(
            pipeline = TranslationPipeline(
                transcriber = transcriber ?: StubTranscriber(),
                translator = translator ?: StubTranslator(),
                synthesizer = synthesizer ?: StubSynthesizer(),
            ),
            mtReal = translator != null,
            sttReal = transcriber != null,
            ttsReal = synthesizer != null,
        )
    }

    /** Pack directory follows FileModelManager's layout: one directory per pack id. */
    fun packDir(modelRoot: File, pack: ModelPack): File = File(modelRoot, pack.id)

    private fun loadTranslator(manager: ModelManager, tier: DeviceTier): Translator? {
        val pack = ModelCatalog.forStage(Stage.MT, tier) ?: return null
        val path = manager.pathFor(pack) ?: return null
        return try {
            LlamaTranslator.load(path, threads = translatorThreads())
        } catch (e: Throwable) {
            Log.w(TAG, "MT engine unavailable, using stub", e)
            null
        }
    }

    /** Use the performance cores for decode without oversubscribing the little cores. */
    private fun translatorThreads(): Int =
        Runtime.getRuntime().availableProcessors().let { (it - 2).coerceIn(2, 6) }

    private fun loadTranscriber(
        factory: VoiceEngineFactory,
        modelRoot: File,
        manager: ModelManager,
        tier: DeviceTier,
        language: String,
    ): Transcriber? {
        val pack = ModelCatalog.forStage(Stage.STT, tier) ?: return null
        if (!manager.isInstalled(pack)) return null
        return try {
            factory.createTranscriber(packDir(modelRoot, pack), language)
        } catch (e: Throwable) {
            Log.w(TAG, "STT engine unavailable, using stub", e)
            null
        }
    }

    /**
     * Every installed voice contributes to one synthesizer: per-language packs map
     * their single language to the pack directory, and a combined pack maps each of
     * its per-language subdirectories. A per-language pack wins over a combined one
     * for the same language.
     */
    private fun loadSynthesizer(
        factory: VoiceEngineFactory,
        modelRoot: File,
        manager: ModelManager,
        tier: DeviceTier,
    ): Synthesizer? {
        val installed = ModelCatalog.packs
            .filter { it.stage == Stage.TTS && it.url.isNotBlank() && it.minTier.ordinal <= tier.ordinal }
            .filter { manager.isInstalled(it) }
            .sortedByDescending { it.languages.size }
        val voiceDirs = mutableMapOf<String, File>()
        for (pack in installed) {
            val dir = packDir(modelRoot, pack)
            val subVoices = dir.listFiles()
                ?.filter { it.isDirectory && Languages.byCode(it.name) != null }
                .orEmpty()
            when {
                subVoices.isNotEmpty() -> subVoices.forEach { voiceDirs[it.name.lowercase()] = it }
                pack.languages.size == 1 -> voiceDirs[pack.languages.single()] = dir
                else -> voiceDirs.putIfAbsent("", dir)
            }
        }
        if (voiceDirs.isEmpty()) return null
        return try {
            factory.createSynthesizer(voiceDirs)
        } catch (e: Throwable) {
            Log.w(TAG, "TTS engine unavailable, using stub", e)
            null
        }
    }

    private const val TAG = "PipelineFactory"
}
