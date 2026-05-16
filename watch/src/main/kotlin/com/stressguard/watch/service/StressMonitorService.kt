package com.stressguard.watch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.stressguard.shared.model.StressReading
import com.stressguard.watch.R
import com.stressguard.watch.app.StressGuardApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class StressMonitorService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        lifecycleScope.launch { runPollLoop() }
        lifecycleScope.launch { runMaintenanceLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private suspend fun runPollLoop() {
        val app = StressGuardApp.from(this)
        var lastTimestampMs = 0L
        while (lifecycleScope.isActive) {
            try {
                val sample = app.health.latestStressSample()
                if (sample != null && sample.timestampMs != lastTimestampMs) {
                    lastTimestampMs = sample.timestampMs
                    handleSample(sample)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Poll iteration failed: ${t.message}")
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun handleSample(sample: com.stressguard.watch.data.HealthDataSource.Sample) {
        val app = StressGuardApp.from(this)
        val settings = app.settings.settings.first()
        val baseline = app.settings.baseline.first()
        val silencedUntil = app.settings.silencedUntilMs.first()
        val syncEnabled = app.settings.syncEnabled.first()

        val reading = StressReading(
            timestamp = Instant.ofEpochMilli(sample.timestampMs).toString(),
            score = sample.score,
            baseline = settings.baselineOverride ?: baseline,
            hr = sample.hr,
        )
        app.store.saveReading(reading)
        if (syncEnabled) PhoneSyncService.broadcastReading(applicationContext, reading)

        val alert = app.alerts.onReading(
            sample = AlertService.Sample(sample.timestampMs, sample.score),
            settings = settings,
            silencedUntilMs = silencedUntil,
        )
        if (alert != null) {
            app.store.saveAlert(alert)
            if (syncEnabled) PhoneSyncService.broadcastAlert(applicationContext, alert)
        }
    }

    private suspend fun runMaintenanceLoop() {
        val app = StressGuardApp.from(this)
        while (lifecycleScope.isActive) {
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val today = now.atZone(zone).toLocalDate()
            val nextRunZdt = today.atTime(LocalTime.of(3, 0)).atZone(zone)
                .let { if (now.atZone(zone).toLocalTime().isAfter(LocalTime.of(3, 0))) it.plusDays(1) else it }
            delay((nextRunZdt.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(60_000L))

            runCatching { runNightlyMaintenance(app) }
                .onFailure { Log.w(TAG, "Nightly maintenance failed: ${it.message}") }
        }
    }

    private suspend fun runNightlyMaintenance(app: StressGuardApp) {
        val zone = ZoneId.systemDefault()
        val yesterday = LocalDate.now(zone).minusDays(1)
        app.store.rebuildSummaryFor(yesterday, zone)

        val cutoff = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        app.store.pruneOlderThan(cutoff)

        val settings = app.settings.settings.first()
        if (settings.baselineOverride == null) {
            val sevenDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val recent = app.store.readingsBetween(sevenDaysAgo, System.currentTimeMillis())
                .map { it.score }
                .filter { it < 50 }
            if (recent.size >= 50) app.settings.setBaseline(recent.average().toInt())
        }
    }

    private fun startForegroundCompat() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "StressGuard monitoring", NotificationManager.IMPORTANCE_MIN),
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.monitoring_active))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "StressMonitorService"
        private const val CHANNEL_ID = "stressguard_monitor"
        private const val NOTIFICATION_ID = 0xCA1
        private const val POLL_INTERVAL_MS = 60_000L
    }
}
