package ai.whatyousay.engine

import ai.whatyousay.core.Synthesizer
import ai.whatyousay.core.Transcriber
import java.io.File

/**
 * Voice runs on sherpa-onnx: Whisper for speech-to-text, Silero for voice-activity
 * detection, and Kokoro or Piper for text-to-speech. sherpa-onnx ships as a prebuilt
 * AAR that is not part of the default build, so the concrete engines live in the
 * gated `sherpa` source set (compiled only with -PwithNative=true) and are reached
 * through this small SPI.
 *
 * When the AAR is absent (the default build and CI), the loader returns null and the
 * app stays on the stub voice path. This is what keeps the "builds and runs on the
 * stub at all times" guarantee true.
 */

/**
 * Streaming voice-activity detector. Feed 16 kHz mono PCM as normalized floats; pop
 * finished speech segments as they complete.
 */
interface VoiceActivityDetector {
    fun accept(samples: FloatArray)

    /** A completed speech segment, or null if none is ready yet. */
    fun poll(): FloatArray?

    /** Force-close any in-progress segment, returning it when non-empty. */
    fun flush(): FloatArray?

    fun reset()

    fun close()
}

/**
 * Builds the native voice engines from an extracted model-pack directory. The
 * directory layout follows the sherpa-onnx convention for each model family; see
 * app/src/sherpa/README.md.
 */
interface VoiceEngineFactory {
    /** Whisper STT. `language` biases a multilingual model; pass "" to auto-detect. */
    fun createTranscriber(modelDir: File, language: String): Transcriber

    /**
     * Kokoro or Piper TTS from one voice directory per language code. The key ""
     * marks a voice with no declared language, used as the fallback.
     */
    fun createSynthesizer(voiceDirs: Map<String, File>): Synthesizer

    /** Silero VAD from a single .onnx model file. */
    fun createVad(vadModel: File): VoiceActivityDetector
}

/**
 * Loads the sherpa-onnx voice factory if it was compiled in. Returns null when the
 * gated source set was excluded, when the AAR is missing, or when the JNI library
 * fails to load, all of which mean "use the stub".
 */
object NativeVoiceEngines {
    private const val IMPL = "ai.whatyousay.engine.sherpa.SherpaVoiceFactory"

    fun load(): VoiceEngineFactory? =
        try {
            val cls = Class.forName(IMPL)
            cls.getDeclaredConstructor().newInstance() as VoiceEngineFactory
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
}
