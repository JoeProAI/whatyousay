package ai.whatyousay.engine

import ai.whatyousay.core.ConversationEngine
import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Languages
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that owns the microphone during hands-free conversation mode.
 *
 * Android requires a foreground service with a microphone type for continuous capture
 * while the screen is off or backgrounded. The service builds the pipeline from
 * whatever models are installed (PipelineFactory), then runs the capture loop:
 * mic -> VAD segment -> transcribe -> translate -> synthesize -> play, looping back to
 * listening. With no native voice engine present it stays in the foreground but does
 * not capture, since segmenting speech needs the on-device VAD; the UI uses typed
 * input on the stub path. Nothing here touches the network.
 */
class ConversationService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private val busy = AtomicBoolean(false)
    private var capture: AudioCapture? = null
    private val player = AudioPlayer()

    // Serializes start/stop so a redelivered or repeated start intent cannot run two
    // capture loops at once: each (re)start tears the previous capture down first.
    private val controlMutex = Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val pair = pairFrom(intent)
        scope.launch { restart(pair) }
        return START_STICKY
    }

    override fun onDestroy() {
        // Cancel first so any in-flight (re)start aborts and releases the lock, then block
        // briefly until the active capture has fully released the mic and VAD. cancelAndJoin
        // inside teardown still awaits the capture coroutine's release after the cancel.
        scope.cancel()
        runBlocking { controlMutex.withLock { teardownCapture() } }
        super.onDestroy()
    }

    private suspend fun restart(pair: LanguagePair) = controlMutex.withLock {
        teardownCapture()
        startConversation(pair)
    }

    private suspend fun teardownCapture() {
        capture?.stop()
        capture = null
    }

    private suspend fun startConversation(pair: LanguagePair) {
        val modelRoot = File(filesDir, "models")
        val manager = FileModelManager(modelRoot)
        val tier = tierFor(totalRamBytes(), hasNpu = false)
        val voiceFactory = NativeVoiceEngines.load()

        val resolution = PipelineFactory.resolve(modelRoot, manager, tier, language = "", voiceFactory = voiceFactory)
        val pipeline = resolution.pipeline
        val engine = ConversationEngine(pair).apply { start() }

        val vadModel = File(modelRoot, "vad/silero_vad.onnx")
        if (voiceFactory == null || !vadModel.isFile) {
            Log.i(TAG, "No on-device voice engine or VAD model; staying idle (UI uses typed input).")
            return
        }

        val vad = voiceFactory.createVad(vadModel)
        capture = AudioCapture(vad).also { loop ->
            loop.start(scope) { samples -> onSegment(samples, engine, pipeline) }
        }
    }

    private suspend fun onSegment(
        samples: ShortArray,
        engine: ConversationEngine,
        pipeline: ai.whatyousay.core.TranslationPipeline,
    ) {
        if (!busy.compareAndSet(false, true)) return // drop overlapping segments while speaking
        try {
            val heard = pipeline.transcriber.transcribe(samples, AudioCapture.SAMPLE_RATE, hint = null)
            if (heard.text.isBlank()) return
            val direction = engine.directionFor(heard.text, heard.language) ?: return
            val result = pipeline.translator.translate(heard.text, direction)
            engine.commit(result)
            val pcm = pipeline.synthesizer.synthesize(result.translatedText, direction.target, AudioCapture.SAMPLE_RATE)
            player.play(pcm, AudioCapture.SAMPLE_RATE)
            engine.ready()
        } catch (e: Exception) {
            Log.w(TAG, "Conversation turn failed", e)
        } finally {
            busy.set(false)
        }
    }

    private fun pairFrom(intent: Intent?): LanguagePair {
        val source = Languages.byCode(intent?.getStringExtra(EXTRA_SOURCE) ?: "en") ?: Languages.EN
        val target = Languages.byCode(intent?.getStringExtra(EXTRA_TARGET) ?: "es") ?: Languages.ES
        return LanguagePair(source, target)
    }

    private fun totalRamBytes(): Long {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("What You Say")
            .setContentText("Listening, on-device")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Conversation", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }

    companion object {
        private const val TAG = "ConversationService"
        private const val CHANNEL_ID = "conversation"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_SOURCE = "source_lang"
        const val EXTRA_TARGET = "target_lang"
    }
}
