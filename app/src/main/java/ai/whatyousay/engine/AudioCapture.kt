package ai.whatyousay.engine

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Microphone capture loop. Reads 16 kHz mono PCM, runs it through the on-device
 * voice-activity detector, and emits each completed speech segment as 16-bit PCM for
 * the transcriber. Nothing is written to disk and nothing leaves the device.
 *
 * Capture only runs when the sherpa-onnx engines are present: segmenting speech needs
 * the on-device VAD. On the stub voice path there is no VAD, so the conversation UI
 * uses typed input instead and this loop is never started. Requires RECORD_AUDIO.
 *
 * Lifecycle: the capture coroutine owns the AudioRecord and the VAD. Teardown of both
 * runs inside the coroutine's finally block under [NonCancellable], on the same thread
 * that drives the VAD, so the recorder release and the VAD reset/close never race with
 * the read loop. [stop] suspends until that teardown has finished, and [close]s the VAD
 * so its native sherpa-onnx resources are not leaked across start/stop cycles. The VAD
 * is single-use: a fresh AudioCapture (with a fresh VAD) is built for each conversation.
 */
class AudioCapture(private val vad: VoiceActivityDetector) {

    private var job: Job? = null

    @Suppress("MissingPermission")
    fun start(scope: CoroutineScope, onSegment: suspend (ShortArray) -> Unit) {
        if (job != null) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufferSize = maxOf(minBuf, READ_CHUNK * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord failed to initialize")
            recorder.release()
            vad.close()
            return
        }
        recorder.startRecording()
        job = scope.launch(Dispatchers.Default) {
            val buf = ShortArray(READ_CHUNK)
            val floats = FloatArray(READ_CHUNK)
            try {
                while (isActive) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    for (i in 0 until n) floats[i] = buf[i] / 32768f
                    vad.accept(if (n == floats.size) floats else floats.copyOf(n))
                    var segment = vad.poll()
                    while (segment != null) {
                        onSegment(toShort(segment))
                        segment = vad.poll()
                    }
                }
            } finally {
                // Release on the capture thread, shielded from cancellation, so the
                // recorder teardown and VAD reset/close cannot race the read loop or
                // each other. No segment is emitted here: emitting from a torn-down
                // capture would kick off a translation after stop was requested.
                withContext(NonCancellable) {
                    runCatching { recorder.stop() }
                    recorder.release()
                    vad.reset()
                    vad.close()
                }
            }
        }
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
    }

    private fun toShort(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { (samples[it] * 32767f).toInt().coerceIn(-32768, 32767).toShort() }

    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val READ_CHUNK = 1_600 // 100 ms at 16 kHz
    }
}
