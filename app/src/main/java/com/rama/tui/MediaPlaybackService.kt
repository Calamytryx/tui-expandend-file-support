package com.rama.tui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.rama.tui.activities.MainActivity
import com.rama.tui.managers.MusicManager

class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "tui_playback"
        const val NOTIFICATION_ID = 1

        const val ACTION_PLAY_PAUSE = "com.rama.tui.PLAY_PAUSE"
        const val ACTION_NEXT = "com.rama.tui.NEXT"
        const val ACTION_PREV = "com.rama.tui.PREV"
        const val ACTION_STOP = "com.rama.tui.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaPlaybackService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        MusicManager.onNotificationChanged = { updateNotification() }
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> MusicManager.togglePlayPause()
            ACTION_NEXT -> MusicManager.next()
            ACTION_PREV -> MusicManager.prev()
            ACTION_STOP -> {
                MusicManager.release()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        MusicManager.onNotificationChanged = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing track"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val track = MusicManager.currentTrack
        val isPlaying = MusicManager.isPlaying

        // Tap notification open app
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        fun actionIntent(action: String, requestCode: Int) = PendingIntent.getService(
            this, requestCode,
            Intent(this, MediaPlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(track?.title ?: "Not playing")
            .setContentText(track?.displayArtists?.ifEmpty { null } ?: "")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(R.drawable.icon_prev, "Previous", actionIntent(ACTION_PREV, 1))
            .addAction(
                if (isPlaying) R.drawable.icon_pause else R.drawable.icon_play,
                if (isPlaying) "Pause" else "Play",
                actionIntent(ACTION_PLAY_PAUSE, 2)
            )
            .addAction(R.drawable.icon_next, "Next", actionIntent(ACTION_NEXT, 3))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            builder.setVisibility(Notification.VISIBILITY_PUBLIC)
        }

        return builder.build()
    }

    fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}
