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

    /** Real EN/FR packs (Whisper small, Qwen2.5 0.5B, bilingual Piper voices). */
    const val RELEASE_V2 = "https://github.com/JoeProAI/whatyousay/releases/download/models-v2"

    private val EN_FR_RU = listOf(Languages.EN.code, Languages.FR.code, Languages.RU.code)

    /** One Piper voice per language, downloaded individually so the user only pulls the languages they picked. */
    private data class Voice(val code: String, val name: String, val voice: String, val bytes: Long, val sha256: String)

    private val PIPER_VOICES = listOf(
        Voice("en", "English", "amy", 68 * MB, "4b596b477c9dde202d35517f2cf140aec12bab1036c770f1eae63dab4a33c222"),
        Voice("es", "Spanish", "davefx", 68 * MB, "558a181e9c757f64636b49ac9bbb54df5d00919a29038e941ab0bc1c164b4f72"),
        Voice("fr", "French", "siwis", 68 * MB, "403bc4685b97056b59de98a45913e698b8d592988c10e87b30e20824a6d846b5"),
        Voice("de", "German", "thorsten", 68 * MB, "c2a0ed9dcd78dcdeb7bb0b963b3ab493833b38233c951c981b356a5ec79297dd"),
        Voice("it", "Italian", "paola", 68 * MB, "4555568796067f0e59397266c2e552ee540894b11ae9092f28f5400c2549d0f0"),
        Voice("pt", "Portuguese", "faber", 68 * MB, "e05ebbd76df5aa440f05032f5a2c56b066838ed71bd1a5b2965b82b1ad09ac0b"),
        Voice("zh", "Chinese", "chaowen", 59 * MB, "15c9c520cd4a878fa6ac4caa46ac063e282b1a8a318d7e68634c872041f67c91"),
        Voice("ru", "Russian", "dmitri", 68 * MB, "d92a33bad79682f0b7e1d32eb8e5c69479453dbe66da74c3131a6c9d060d09ac"),
        Voice("tr", "Turkish", "fahrettin", 68 * MB, "ee08cd2207c434ee0cadc5c6ee61c6098ca7208e9fb024a4bc9c1dec06ad4912"),
        Voice("vi", "Vietnamese", "vais1000", 68 * MB, "1892a2f7272f28d27769dbef4e5f1866770724f6c820064c05640e71a9271b78"),
        Voice("uk", "Ukrainian", "ukrainian_tts", 81 * MB, "16f0dc3b55d9c41d3a787399249e182b1e7670d717ea4427cb13469eec57f60c"),
    )

    private val voicePacks: List<ModelPack> = PIPER_VOICES.map { v ->
        ModelPack(
            "tts-piper-${v.code}", Stage.TTS, "Piper voice (${v.name})", v.bytes, listOf(v.code), "fp16", DeviceTier.LOW,
            v.sha256, "$RELEASE_V2/tts-piper-${v.code}.zip",
        )
    }

    val packs: List<ModelPack> = listOf(
        // Machine translation. The default is the light, fast Qwen2.5 0.5B run with a
        // strict translation-only prompt (see llama_jni.cpp): small download, snappy
        // turns, and no chatty rambling. Gemma 2 2B is kept as an optional heavier,
        // higher-quality upgrade (unpublished until re-enabled) rather than the default.
        ModelPack(
            "mt-qwen25-05b", Stage.MT, "Qwen2.5 0.5B Instruct (multilingual)", 420 * MB, CORE_LANGS, "Q5_K_M", DeviceTier.LOW,
            "a0a413dcbb4676f21d4c951b98a393324694edb1a20a4f9547d1de8d2919ff3b",
            "$RELEASE_V2/qwen2.5-0.5b-instruct-q5_k_m.gguf",
        ),
        ModelPack("mt-gemma2-2b", Stage.MT, "Gemma 2 2B Instruct", 1_710 * MB, CORE_LANGS, "Q4_K_M", DeviceTier.MID, "", ""),
        ModelPack("mt-hunyuan-15-1b8", Stage.MT, "Hunyuan HY-MT1.5 1.8B", 1_200 * MB, CORE_LANGS, "Q4_K_M", DeviceTier.MID, "", ""),
        ModelPack("mt-translategemma-4b", Stage.MT, "TranslateGemma 4B (55 languages)", 2_700 * MB, CORE_LANGS, "Q4_K_M", DeviceTier.FLAGSHIP, "", ""),
        // Speech to text
        ModelPack(
            "stt-whisper-tiny", Stage.STT, "Whisper tiny", 75 * MB, CORE_LANGS, "int8", DeviceTier.LOW,
            "3c2c96209775185bfec00b0d4d3eab8c80bbc41d604ce6ad9831ed760c39f252",
            "$RELEASE/stt-whisper-tiny.zip",
        ),
        ModelPack(
            "stt-whisper-small", Stage.STT, "Whisper small", 380 * MB, CORE_LANGS, "int8", DeviceTier.MID,
            "822d432f9a6938a80f6bf7d09a1a7f9c41b51054f3ea06a4154cd900cd8d943a",
            "$RELEASE_V2/stt-whisper-small.zip",
        ),
        // Text to speech
        ModelPack(
            "tts-piper", Stage.TTS, "Piper (en_US amy)", 64 * MB, CORE_LANGS, "fp16", DeviceTier.LOW,
            "1950bc7f911773a1b91c25ebd9a0d5ed87bcb56f26ec31e668104a82160866d5",
            "$RELEASE/tts-piper-amy.zip",
        ),
        // Kept so devices that installed the combined pack keep their voices; new
        // installs download the per-language packs below instead.
        ModelPack(
            "tts-piper-enfrru", Stage.TTS, "Piper voices (English, French, Russian)", 205 * MB, EN_FR_RU, "fp16", DeviceTier.MID,
            "6621bbc31b48b4d266bdaccbfe2f89fc6e2680ea772283d8bda9e303d38ac4d6",
            "$RELEASE_V2/tts-piper-enfrru.zip",
        ),
        ModelPack("tts-kokoro-82m", Stage.TTS, "Kokoro-82M", 330 * MB, CORE_LANGS, "fp16", DeviceTier.FLAGSHIP, "", ""),
        // Camera / multimodal
        ModelPack("cam-minicpm-v", Stage.CAMERA, "MiniCPM-V", 5L * GB, CORE_LANGS, "Q4_0", DeviceTier.FLAGSHIP, "", ""),
    ) + voicePacks

    /**
     * Best pack for a stage that the device can actually run. Among the packs the tier
     * supports, a real (published) pack always wins over a planned one whose `url` is
     * still blank, so a high-tier device gets a working engine today instead of silently
     * falling back to the stub for an unpublished upgrade. Only when nothing is published
     * for the stage does the planned entry win (and that stage stays on the stub).
     */
    fun forStage(stage: Stage, tier: DeviceTier): ModelPack? {
        val runnable = packs.filter { it.stage == stage && it.minTier.ordinal <= tier.ordinal }
        val published = runnable.filter { it.url.isNotBlank() }
        return (published.ifEmpty { runnable }).maxByOrNull { it.minTier.ordinal }
    }

    /**
     * The published per-language voice packs covering [languages]. Languages without a
     * permissively licensed voice yet simply have no pack: they still transcribe and
     * translate on screen, the phone just does not speak them.
     */
    fun voicePacksFor(languages: Collection<String>): List<ModelPack> =
        voicePacks.filter { it.languages.single() in languages }

    /**
     * The default MT/STT/TTS bundle for a tier: one translator, one recognizer (both
     * multilingual), and a voice pack for each selected language that has one. Falls
     * back to the tier's combined voice pack when no per-language pack matches.
     */
    fun defaultsFor(tier: DeviceTier, languages: Collection<String>): List<ModelPack> {
        val voices = voicePacksFor(languages).ifEmpty { listOfNotNull(forStage(Stage.TTS, tier)) }
        return listOfNotNull(forStage(Stage.MT, tier), forStage(Stage.STT, tier)) + voices
    }

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
