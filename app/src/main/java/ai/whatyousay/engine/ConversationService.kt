package ai.whatyousay.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that owns the microphone during hands-free conversation mode.
 *
 * Android requires a foreground service with a microphone type for continuous
 * capture while the screen is off or backgrounded. The audio loop (AudioRecord +
 * VAD) runs here and feeds the ConversationEngine. This skeleton wires the
 * lifecycle and the required notification; the capture loop attaches in Phase 1.
 */
class ConversationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
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
        private const val CHANNEL_ID = "conversation"
        private const val NOTIFICATION_ID = 1
    }
}
