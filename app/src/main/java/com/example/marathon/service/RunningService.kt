package com.example.marathon.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.marathon.MainActivity
import com.example.marathon.R

class RunningService : Service() {

    private var ttsManager: RunningTtsManager? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        const val CHANNEL_ID = "marathon_running_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_TTS_KILOMETER = "ACTION_TTS_KILOMETER"
        const val EXTRA_DISTANCE = "extra_distance"
        const val EXTRA_TIME = "extra_time"
        const val EXTRA_PACE = "extra_pace"
        const val EXTRA_KM = "extra_km"

        fun startService(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
            val intent = Intent(context, RunningService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context, distanceKm: String = "", elapsedTime: String = "") {
            val intent = Intent(context, RunningService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_DISTANCE, distanceKm)
                putExtra(EXTRA_TIME, elapsedTime)
            }
            context.startService(intent)
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, RunningService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, RunningService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun updateNotification(context: Context, distance: String, time: String, pace: String) {
            val intent = Intent(context, RunningService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_DISTANCE, distance)
                putExtra(EXTRA_TIME, time)
                putExtra(EXTRA_PACE, pace)
            }
            context.startService(intent)
        }

        fun announceKilometer(context: Context, km: Int, pace: String, elapsedTime: String) {
            val intent = Intent(context, RunningService::class.java).apply {
                action = ACTION_TTS_KILOMETER
                putExtra(EXTRA_KM, km)
                putExtra(EXTRA_PACE, pace)
                putExtra(EXTRA_TIME, elapsedTime)
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ttsManager = RunningTtsManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("0.00 km", "00:00", "--:--"))
                ttsManager?.announceRunStart()
            }
            ACTION_STOP -> {
                val distanceKm = intent.getStringExtra(EXTRA_DISTANCE) ?: ""
                val elapsedTime = intent.getStringExtra(EXTRA_TIME) ?: ""
                if (distanceKm.isNotEmpty() && elapsedTime.isNotEmpty()) {
                    ttsManager?.announceRunComplete(distanceKm, elapsedTime)
                }
                // Delay stop slightly to allow TTS to finish
                handler.postDelayed({
                    ttsManager?.shutdown()
                    ttsManager = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }, 3000)
            }
            ACTION_PAUSE -> {
                ttsManager?.announcePause()
            }
            ACTION_RESUME -> {
                ttsManager?.announceResume()
            }
            ACTION_UPDATE -> {
                val distance = intent.getStringExtra(EXTRA_DISTANCE) ?: "0.00 km"
                val time = intent.getStringExtra(EXTRA_TIME) ?: "00:00"
                val pace = intent.getStringExtra(EXTRA_PACE) ?: "--:--"
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification(distance, time, pace))
            }
            ACTION_TTS_KILOMETER -> {
                val km = intent.getIntExtra(EXTRA_KM, 0)
                val pace = intent.getStringExtra(EXTRA_PACE) ?: "--:--"
                val elapsedTime = intent.getStringExtra(EXTRA_TIME) ?: ""
                if (km > 0) {
                    ttsManager?.announceKilometer(km, pace, elapsedTime)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ttsManager?.shutdown()
        ttsManager = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Marathon Running",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running tracking notification"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(distance: String, time: String, pace: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Marathon - Running")
            .setContentText("$distance  |  $time  |  $pace /km")
            .setSmallIcon(R.drawable.icon_run)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
