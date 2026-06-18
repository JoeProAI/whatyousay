package ai.whatyousay.engine

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Microphone capture loop. Reads 16 kHz mono PCM, runs it through the on-device
 * voice-activity detector, and emits each completed speech segment as 16-bit PCM for
 * the transcriber. Nothing is written to disk and nothing leaves the device.
 *
 * Capture only runs when the sherpa-onnx engines are present: segmenting speech needs
 * the on-device VAD. On the stub voice path there is no VAD, so the conversation UI
 * uses typed input instead and this loop is never started. Requires RECORD_AUDIO.
 */
class AudioCapture(private val vad: VoiceActivityDetector) {

    private var record: AudioRecord? = null
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
            return
        }
        record = recorder
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
                vad.flush()?.let { onSegment(toShort(it)) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        record?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        record = null
        vad.reset()
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
