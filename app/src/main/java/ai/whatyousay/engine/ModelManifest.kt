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
 * quantization.
 *
 * Packs with a non-blank `url`/`sha256` are real and downloadable today (see
 * MODELS.md for how the release assets are produced); the remaining entries are the
 * planned higher-tier upgrades whose `url` is filled in as each pack is published.
 * When a selected pack has no `url` the download is a no-op and that stage falls back
 * to the stub, so the app degrades gracefully instead of failing hard.
 *
 * Voice packs are `.zip` archives that the manager extracts; the MT pack is a single
 * GGUF. Hashes are computed over the downloaded artifact.
 */
object ModelCatalog {
    /** Where the released, repackaged model assets live. See MODELS.md. */
    const val RELEASE = "https://github.com/JoeProAI/whatyousay/releases/download/models-v1"

    val packs: List<ModelPack> = listOf(
        // Machine translation
        ModelPack(
            "mt-qwen25-05b", Stage.MT, "Qwen2.5 0.5B Instruct (multilingual)", 470 * MB, CORE_LANGS, "Q4_K_M", DeviceTier.LOW,
            "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            "$RELEASE/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        ),
        ModelPack("mt-hunyuan-15-1b8", Stage.MT, "Hunyuan HY-MT1.5 1.8B", 1_200 * MB, CORE_LANGS, "Q4_K_M", DeviceTier.MID, "", ""),
        ModelPack("mt-translategemma-4b", Stage.MT, "TranslateGemma 4B (55 languages)", 2_700 * MB, CORE_LANGS, "Q4_K_M", DeviceTier.FLAGSHIP, "", ""),
        // Speech to text
        ModelPack(
            "stt-whisper-tiny", Stage.STT, "Whisper tiny", 75 * MB, CORE_LANGS, "int8", DeviceTier.LOW,
            "3c2c96209775185bfec00b0d4d3eab8c80bbc41d604ce6ad9831ed760c39f252",
            "$RELEASE/stt-whisper-tiny.zip",
        ),
        ModelPack("stt-whisper-small", Stage.STT, "Whisper small", 488 * MB, CORE_LANGS, "int8", DeviceTier.MID, "", ""),
        // Text to speech
        ModelPack(
            "tts-piper", Stage.TTS, "Piper (en_US amy)", 64 * MB, CORE_LANGS, "fp16", DeviceTier.LOW,
            "1950bc7f911773a1b91c25ebd9a0d5ed87bcb56f26ec31e668104a82160866d5",
            "$RELEASE/tts-piper-amy.zip",
        ),
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
