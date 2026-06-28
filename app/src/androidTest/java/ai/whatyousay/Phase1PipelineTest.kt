package ai.whatyousay

import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Languages
import ai.whatyousay.engine.DeviceTier
import ai.whatyousay.engine.FileModelManager
import ai.whatyousay.engine.ModelCatalog
import ai.whatyousay.engine.ModelPack
import ai.whatyousay.engine.NativeVoiceEngines
import ai.whatyousay.engine.PipelineFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end Phase 1 check on real engines: extract the downloaded model packs, build the
 * pipeline through the same factory the app uses, then run a single STT -> MT -> TTS turn.
 *
 * Only meaningful on a -PwithNative build with the model packs staged under the app's
 * files dir at `staged/` (see MODELS.md / the PR notes for the adb push commands). On a
 * stub build the native factory is absent and the test self-skips via assumptions, so it
 * never turns CI red.
 */
@RunWith(AndroidJUnit4::class)
class Phase1PipelineTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val staged = File(ctx.filesDir, "staged")

    @Test
    fun translatesSpeechEndToEnd() {
        val factory = NativeVoiceEngines.load()
        assumeNotNull("native voice engine not compiled in (stub build); skipping", factory)
        assumeTrue("model packs not staged at ${staged.absolutePath}; skipping", staged.isDirectory)

        val root = File(ctx.filesDir, "models").apply { deleteRecursively(); mkdirs() }
        val manager = FileModelManager(root)

        val stt = requireNotNull(ModelCatalog.forStage(ai.whatyousay.engine.Stage.STT, DeviceTier.LOW))
        val tts = requireNotNull(ModelCatalog.forStage(ai.whatyousay.engine.Stage.TTS, DeviceTier.LOW))
        val mt = requireNotNull(ModelCatalog.forStage(ai.whatyousay.engine.Stage.MT, DeviceTier.LOW))

        install(manager, stt)
        install(manager, tts)
        install(manager, mt)

        // The VAD model is installed out-of-band by the app under models/vad/.
        File(root, "vad").mkdirs()
        stagedFile("silero_vad.onnx").copyTo(File(root, "vad/silero_vad.onnx"), overwrite = true)

        val resolution = PipelineFactory.resolve(root, manager, DeviceTier.LOW, language = "", voiceFactory = factory)
        assertTrue("STT did not resolve to a real engine", resolution.sttReal)
        assertTrue("MT did not resolve to a real engine", resolution.mtReal)
        assertTrue("TTS did not resolve to a real engine", resolution.ttsReal)

        val pipeline = resolution.pipeline
        try {
            val samples = readWavMono16("0.wav")
            val heard = runBlocking { pipeline.transcriber.transcribe(samples, 16000, hint = null) }
            Log.i(TAG, "STT => '${heard.text}' (lang=${heard.language?.code})")
            assertTrue("STT produced empty text", heard.text.isNotBlank())

            val source = "Hello, how are you today?"
            val translated = runBlocking {
                pipeline.translator.translate(source, LanguagePair(Languages.EN, Languages.ES))
            }
            Log.i(TAG, "MT => '${translated.translatedText}'")
            assertTrue("MT produced empty text", translated.translatedText.isNotBlank())
            assertTrue("MT echoed the input verbatim", !translated.translatedText.equals(source, ignoreCase = true))

            val pcm = runBlocking {
                pipeline.synthesizer.synthesize(translated.translatedText, Languages.ES, 16000)
            }
            Log.i(TAG, "TTS => ${pcm.size} samples")
            assertTrue("TTS produced no audio", pcm.size > 16000 / 4) // at least ~0.25s
        } finally {
            pipeline.close()
        }
    }

    private fun install(manager: FileModelManager, pack: ModelPack) {
        val artifact = stagedFile(FileModelManager.artifactName(pack.url, pack.id))
        val result = artifact.inputStream().use { manager.ingest(pack, artifact.length(), it) {} }
        assertTrue("failed to install ${pack.id}: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue("${pack.id} not installed after ingest", manager.isInstalled(pack))
    }

    private fun stagedFile(name: String): File {
        val f = File(staged, name)
        assertNotNull(f)
        assertTrue("missing staged file ${f.absolutePath}", f.isFile)
        return f
    }

    /** Read a 16-bit PCM mono WAV into ShortArray, skipping the header chunks. */
    private fun readWavMono16(name: String): ShortArray {
        val bytes = stagedFile(name).readBytes()
        var pos = 12 // past "RIFF"<size>"WAVE"
        var dataOff = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = le32(bytes, pos + 4)
            val body = pos + 8
            if (id == "data") {
                dataOff = body
                dataLen = size
                break
            }
            pos = body + size + (size and 1)
        }
        require(dataOff >= 0) { "no data chunk in $name" }
        val n = dataLen / 2
        val out = ShortArray(n)
        for (i in 0 until n) {
            val lo = bytes[dataOff + i * 2].toInt() and 0xff
            val hi = bytes[dataOff + i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)

    companion object {
        private const val TAG = "Phase1PipelineTest"
    }
}
