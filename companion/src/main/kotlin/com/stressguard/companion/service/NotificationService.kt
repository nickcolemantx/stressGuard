package com.stressguard.companion.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stressguard.companion.MainActivity
import com.stressguard.shared.model.AlertEvent

class NotificationService(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "StressGuard Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts mirrored from your watch"
                enableVibration(true)
            },
        )
    }

    fun showAlert(event: AlertEvent, vibrate: Boolean) {
        ensureChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_ALERT_TS, event.timestamp)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPi = PendingIntent.getActivity(
            context, REQ_OPEN, openIntent, pendingFlags(),
        )

        val silenceIntent = Intent(context, SilenceReceiver::class.java).apply {
            action = SilenceReceiver.ACTION_SILENCE
            putExtra(SilenceReceiver.EXTRA_DURATION_MIN, DEFAULT_SILENCE_MIN)
        }
        val silencePi = PendingIntent.getBroadcast(
            context, REQ_SILENCE, silenceIntent, pendingFlags(),
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Stress spike — ${event.score}")
            .setContentText("+${event.delta} pts in ${event.deltaWindowMin} min")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, "Silence ${DEFAULT_SILENCE_MIN}m", silencePi)

        if (vibrate) builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)

        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build()) }
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    companion object {
        const val CHANNEL_ID = "stressguard_alerts"
        const val NOTIF_ID = 0xA1E1
        const val REQ_OPEN = 1001
        const val REQ_SILENCE = 1002
        const val DEFAULT_SILENCE_MIN = 10
    }
}
