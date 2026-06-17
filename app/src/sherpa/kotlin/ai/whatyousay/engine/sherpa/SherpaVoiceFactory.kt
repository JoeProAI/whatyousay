package ai.whatyousay.engine.sherpa

import ai.whatyousay.core.Language
import ai.whatyousay.core.Languages
import ai.whatyousay.core.Synthesizer
import ai.whatyousay.core.Transcriber
import ai.whatyousay.core.Transcription
import ai.whatyousay.engine.VoiceActivityDetector
import ai.whatyousay.engine.VoiceEngineFactory
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * sherpa-onnx voice engines. This source set is compiled only when the app is built
 * with -PwithNative=true and the sherpa-onnx AAR is vendored into app/libs; the
 * default build never sees it and stays on the stub.
 *
 * sherpa-onnx is pinned at the release whose AAR is vendored (see app/src/sherpa/README.md).
 * Models load from app-private files (assetManager is null), so the only network use
 * remains the user-initiated, sha256-verified model-pack download.
 */
private const val VOICE_SAMPLE_RATE = 16000

class SherpaVoiceFactory : VoiceEngineFactory {
    override fun createTranscriber(modelDir: File, language: String): Transcriber =
        SherpaTranscriber(modelDir, language)

    override fun createSynthesizer(modelDir: File): Synthesizer =
        SherpaSynthesizer(modelDir)

    override fun createVad(vadModel: File): VoiceActivityDetector =
        SherpaVad(vadModel)
}

internal class SherpaTranscriber(modelDir: File, language: String) : Transcriber {
    private val recognizer: OfflineRecognizer

    init {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = VOICE_SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = pick(modelDir, "encoder").absolutePath,
                    decoder = pick(modelDir, "decoder").absolutePath,
                    language = language,
                    task = "transcribe",
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = 2,
                modelType = "whisper",
            ),
            decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(config = config)
    }

    override suspend fun transcribe(samples: ShortArray, sampleRate: Int, hint: Language?): Transcription =
        withContext(Dispatchers.Default) {
            val stream = recognizer.createStream()
            stream.acceptWaveform(toFloat(samples), sampleRate)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            stream.release()
            val detected = Languages.byCode(normalizeLang(result.lang)) ?: hint
            Transcription(
                text = result.text.trim(),
                language = detected,
                isFinal = true,
                confidence = 1f,
            )
        }

    /** Whisper detects language from audio, not text. */
    override fun detectLanguage(text: String): Language? = null
}

internal class SherpaSynthesizer(modelDir: File) : Synthesizer {
    private val tts: OfflineTts
    private val nativeSampleRate: Int

    init {
        val tokens = File(modelDir, "tokens.txt").absolutePath
        val dataDir = File(modelDir, "espeak-ng-data").let { if (it.isDirectory) it.absolutePath else "" }
        val voices = File(modelDir, "voices.bin")
        val modelConfig = if (voices.isFile) {
            OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = pick(modelDir, ".onnx").absolutePath,
                    voices = voices.absolutePath,
                    tokens = tokens,
                    dataDir = dataDir,
                    lang = "en",
                ),
                numThreads = 2,
            )
        } else {
            OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = pick(modelDir, ".onnx").absolutePath,
                    tokens = tokens,
                    dataDir = dataDir,
                ),
                numThreads = 2,
            )
        }
        tts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))
        nativeSampleRate = tts.sampleRate()
    }

    override suspend fun synthesize(text: String, language: Language, sampleRate: Int): ShortArray =
        withContext(Dispatchers.Default) {
            val audio = tts.generate(text = text, sid = 0, speed = 1.0f)
            toShort(resample(audio.samples, nativeSampleRate, sampleRate))
        }
}

internal class SherpaVad(vadModel: File) : VoiceActivityDetector {
    private val vad: Vad = Vad(
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = vadModel.absolutePath,
                threshold = 0.5f,
                minSilenceDuration = 0.25f,
                minSpeechDuration = 0.25f,
                windowSize = 512,
            ),
            sampleRate = VOICE_SAMPLE_RATE,
            numThreads = 1,
        ),
    )

    override fun accept(samples: FloatArray) = vad.acceptWaveform(samples)

    override fun poll(): FloatArray? {
        if (vad.empty()) return null
        val segment = vad.front()
        vad.pop()
        return segment.samples
    }

    override fun flush(): FloatArray? {
        vad.flush()
        return poll()
    }

    override fun reset() = vad.reset()

    override fun close() = vad.release()
}

/** Resolve a model file in a pack directory by a name fragment (e.g. "encoder", ".onnx"). */
private fun pick(dir: File, fragment: String): File {
    val match = dir.listFiles()
        ?.filter { it.isFile && it.name.contains(fragment, ignoreCase = true) }
        ?.minByOrNull { it.name.length }
    return match ?: File(dir, fragment)
}

/** Whisper reports language as a token like "<|en|>"; reduce it to the bare code. */
private fun normalizeLang(raw: String): String =
    raw.trim().trim('<', '>', '|').lowercase()

private fun toFloat(samples: ShortArray): FloatArray {
    val out = FloatArray(samples.size)
    for (i in samples.indices) {
        out[i] = samples[i] / 32768.0f
    }
    return out
}

private fun toShort(samples: FloatArray): ShortArray {
    val out = ShortArray(samples.size)
    for (i in samples.indices) {
        val v = (samples[i] * 32767.0f)
        out[i] = when {
            v > 32767.0f -> Short.MAX_VALUE
            v < -32768.0f -> Short.MIN_VALUE
            else -> v.toInt().toShort()
        }
    }
    return out
}

/** Naive linear resampling, only used when the TTS native rate differs from the target. */
private fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
    if (fromRate == toRate || toRate <= 0 || samples.isEmpty()) return samples
    val ratio = toRate.toDouble() / fromRate.toDouble()
    val outLen = (samples.size * ratio).toInt().coerceAtLeast(1)
    val out = FloatArray(outLen)
    for (i in 0 until outLen) {
        val srcPos = i / ratio
        val left = srcPos.toInt().coerceIn(0, samples.size - 1)
        val right = (left + 1).coerceAtMost(samples.size - 1)
        val frac = (srcPos - left).toFloat()
        out[i] = samples[left] * (1f - frac) + samples[right] * frac
    }
    return out
}
