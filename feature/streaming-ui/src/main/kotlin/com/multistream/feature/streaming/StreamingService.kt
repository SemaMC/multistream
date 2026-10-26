package com.multistream.feature.streamingui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground service that keeps the streaming process alive.
 *
 * On Android 14+ (API 34+) MediaProjection requires a foreground service with
 * `foregroundServiceType="mediaProjection"`. This service satisfies that requirement and stops the
 * system from killing the app while a session is running.
 *
 * The service does NOT own the MediaProjection: [StreamingViewModel] still handles the consent
 * flow and the capture. The service only protects the process.
 */
class StreamingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: the service type is required at runtime, not only in the manifest.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Multistream")
            .setContentText("Streaming in progress")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the streaming session alive"
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "streaming_channel"
        const val NOTIFICATION_ID = 1001
    }
}
