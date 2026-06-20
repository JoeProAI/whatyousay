package ai.whatyousay.engine

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures one push-to-talk utterance: open the mic on press, accumulate 16 kHz
 * mono PCM, and return the whole segment on release. Unlike [AudioCapture] this does
 * not need the on-device VAD, so the conversation screen has a working voice control
 * on the stub build. When the native STT engine is present the same captured segment
 * is transcribed for real; with no microphone or permission it yields an empty
 * segment and the stub transcriber still produces a turn. On-device only; nothing is
 * written to disk and nothing leaves the device. Requires RECORD_AUDIO.
 */
class PushToTalkRecorder {

    private var job: Job? = null
    private val collected = ArrayList<Short>()

    @Suppress("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (job != null) return
        collected.clear()
        val minBuf = AudioRecord.getMinBufferSize(AudioCapture.SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            Log.w(TAG, "AudioRecord buffer size unavailable; capture disabled")
            return
        }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AudioCapture.SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            maxOf(minBuf, READ_CHUNK * 2),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord failed to initialize; capture disabled")
            recorder.release()
            return
        }
        recorder.startRecording()
        job = scope.launch(Dispatchers.Default) {
            val buf = ShortArray(READ_CHUNK)
            try {
                while (isActive) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    synchronized(collected) {
                        for (i in 0 until n) {
                            if (collected.size < MAX_SAMPLES) collected.add(buf[i])
                        }
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
    }

    /** Stop capture and return the captured PCM, or an empty array if nothing was recorded. */
    suspend fun stop(): ShortArray {
        job?.cancelAndJoin()
        job = null
        return synchronized(collected) { collected.toShortArray() }
    }

    companion object {
        private const val TAG = "PushToTalkRecorder"
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val READ_CHUNK = 1_600 // 100 ms at 16 kHz
        private const val MAX_SAMPLES = AudioCapture.SAMPLE_RATE * 30 // cap at 30 s
    }
}
