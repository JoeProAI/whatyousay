package ai.whatyousay.engine

import ai.whatyousay.core.Languages

/** Device capability tiers, lowest to highest. Order matters for selection. */
enum class DeviceTier { LOW, MID, FLAGSHIP }

/** Which pipeline stage a pack serves. */
enum class Stage { MT, STT, TTS, CAMERA }

/**
 * A downloadable model pack. Packs are not baked into the APK: the install stays
 * small, models stay updatable, and the user only pulls what their device can run.
 */
data class ModelPack(
    val id: String,
    val stage: Stage,
    val displayName: String,
    val approxBytes: Long,
    val languages: List<String>,
    val quantization: String,
    val minTier: DeviceTier,
    val sha256: String,
    val url: String,
)

private val CORE_LANGS = Languages.all.map { it.code }
private const val GB = 1_000_000_000L
private const val MB = 1_000_000L

/**
 * The shipping catalog. Sizes are approximate on-device footprints after
 * quantization. sha256/url are filled at release from the pack CDN.
 */
object ModelCatalog {
    val packs: List<ModelPack> = listOf(
        // Machine translation
        ModelPack("mt-hunyuan-hymt2", Stage.MT, "Hunyuan Hy-MT2 (33 languages)", 460 * MB, CORE_LANGS, "Q4_0", DeviceTier.LOW, "", ""),
        ModelPack("mt-hunyuan-15-1b8", Stage.MT, "Hunyuan HY-MT1.5 1.8B", 1_200 * MB, CORE_LANGS, "Q4_K_M", DeviceTier.MID, "", ""),
        ModelPack("mt-translategemma-4b", Stage.MT, "TranslateGemma 4B (55 languages)", 2_700 * MB, CORE_LANGS, "Q4_K_M", DeviceTier.FLAGSHIP, "", ""),
        ModelPack("mt-nllb-600m", Stage.MT, "NLLB-200 distilled 600M (200 languages)", 650 * MB, CORE_LANGS, "Q8_0", DeviceTier.LOW, "", ""),
        // Speech to text
        ModelPack("stt-whisper-tiny", Stage.STT, "Whisper tiny", 75 * MB, CORE_LANGS, "int8", DeviceTier.LOW, "", ""),
        ModelPack("stt-whisper-small", Stage.STT, "Whisper small", 488 * MB, CORE_LANGS, "int8", DeviceTier.MID, "", ""),
        // Text to speech
        ModelPack("tts-piper", Stage.TTS, "Piper", 64 * MB, CORE_LANGS, "fp16", DeviceTier.LOW, "", ""),
        ModelPack("tts-kokoro-82m", Stage.TTS, "Kokoro-82M", 330 * MB, CORE_LANGS, "fp16", DeviceTier.FLAGSHIP, "", ""),
        // Camera / multimodal
        ModelPack("cam-minicpm-v", Stage.CAMERA, "MiniCPM-V", 5L * GB, CORE_LANGS, "Q4_0", DeviceTier.FLAGSHIP, "", ""),
    )

    /** Best pack for a stage that the device can actually run. */
    fun forStage(stage: Stage, tier: DeviceTier): ModelPack? =
        packs
            .filter { it.stage == stage && it.minTier.ordinal <= tier.ordinal }
            .maxByOrNull { it.minTier.ordinal }

    /** The default MT/STT/TTS bundle for a tier. */
    fun defaultsFor(tier: DeviceTier): List<ModelPack> =
        listOf(Stage.MT, Stage.STT, Stage.TTS).mapNotNull { forStage(it, tier) }

    fun byId(id: String): ModelPack? = packs.firstOrNull { it.id == id }
}

/** Classify a device from RAM and NPU availability. */
fun tierFor(ramBytes: Long, hasNpu: Boolean): DeviceTier {
    val gb = ramBytes.toDouble() / GB
    return when {
        gb >= 12.0 && hasNpu -> DeviceTier.FLAGSHIP
        gb < 6.0 -> DeviceTier.LOW
        else -> DeviceTier.MID
    }
}
