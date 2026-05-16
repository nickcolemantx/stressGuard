package com.stressguard.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stressguard.companion.app.StressGuardCompanionApp
import com.stressguard.shared.model.AlertEvent
import com.stressguard.shared.model.AlertSettings
import com.stressguard.shared.model.StressReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompanionViewModel(app: Application) : AndroidViewModel(app) {

    enum class Window(val xLabel: String, val minutes: Long, val resolution: String) {
        FOUR_HOUR("4H", 4 * 60, "1min"),
        ONE_DAY("1D", 24 * 60, "1min"),
        ONE_WEEK("1W", 7 * 24 * 60, "5min"),
        ONE_MONTH("1M", 30 * 24 * 60, "30min"),
        ONE_YEAR("1Y", 365 * 24 * 60, "1day"),
    }

    data class DashboardState(
        val current: StressReading?,
        val baseline: Int,
        val recentAlerts: List<AlertEvent>,
        val watchConnected: Boolean,
        val syncEnabled: Boolean,
        val lastSyncedMs: Long,
    )

    data class SettingsUiState(
        val settings: AlertSettings,
        val baseline: Int,
        val mirrorAlerts: Boolean,
        val notifVibration: Boolean,
        val autoSync: Boolean,
        val syncWindowDays: Int,
        val lastSyncedMs: Long,
    )

    data class TodaySummary(
        val avg: Int?,
        val peak: StressReading?,
        val alertCount: Int,
    )

    data class SyncStatus(
        val syncing: Boolean = false,
        val lastResult: String? = null,
    )

    private val a = StressGuardCompanionApp.from(app)

    private data class DashboardCore(
        val current: StressReading?,
        val baseline: Int,
        val alerts: List<AlertEvent>,
        val connected: Boolean,
        val syncEnabled: Boolean,
    )

    val dashboard: StateFlow<DashboardState> = combine(
        combine(
            a.store.latestReading,
            a.settings.baseline,
            a.store.recentAlerts,
            a.settings.watchConnected,
            a.settings.autoSync,
        ) { reading, baseline, alerts, connected, sync ->
            DashboardCore(reading, baseline, alerts, connected, sync)
        },
        a.settings.lastSyncedMs,
    ) { core, lastSynced ->
        DashboardState(core.current, core.baseline, core.alerts, core.connected, core.syncEnabled, lastSynced)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardState(null, 48, emptyList(), false, true, 0L),
    )

    private data class SettingsCore(
        val settings: AlertSettings,
        val baseline: Int,
        val mirror: Boolean,
        val vibration: Boolean,
        val auto: Boolean,
    )

    val settingsUi: StateFlow<SettingsUiState> = combine(
        combine(
            a.settings.settings,
            a.settings.baseline,
            a.settings.mirrorAlertsToPhone,
            a.settings.notificationVibration,
            a.settings.autoSync,
        ) { s, b, m, v, auto -> SettingsCore(s, b, m, v, auto) },
        a.settings.syncWindowDays,
        a.settings.lastSyncedMs,
    ) { core, days, lastSynced ->
        SettingsUiState(core.settings, core.baseline, core.mirror, core.vibration, core.auto, days, lastSynced)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(AlertSettings(), 48, true, true, true, 30, 0L),
    )

    private val _sync = MutableStateFlow(SyncStatus())
    val sync: StateFlow<SyncStatus> = _sync

    suspend fun loadWindow(window: Window): List<StressReading> {
        val now = System.currentTimeMillis()
        return a.store.readingsBetween(now - window.minutes * 60_000L, now)
    }

    suspend fun loadAlertsToday(): List<AlertEvent> {
        val now = System.currentTimeMillis()
        val dayStart = now - 24L * 60 * 60 * 1000
        return a.store.alertsBetween(dayStart, now)
    }

    suspend fun loadTodaySummary(): TodaySummary {
        val now = System.currentTimeMillis()
        val dayStart = now - 24L * 60 * 60 * 1000
        val readings = a.store.readingsBetween(dayStart, now)
        val avg = readings.takeIf { it.isNotEmpty() }?.map { it.score }?.average()?.toInt()
        val peak = readings.maxByOrNull { it.score }
        val alertCount = a.store.alertCountSince(dayStart)
        return TodaySummary(avg, peak, alertCount)
    }

    fun saveAndPushSettings(settings: AlertSettings) {
        viewModelScope.launch {
            a.settings.update(settings)
            val baseline = a.settings.baseline.first()
            val ok = a.watch.pushSettings(settings, baseline)
            _sync.value = SyncStatus(syncing = false, lastResult = if (ok) "Settings pushed to watch ✓" else "Push failed — watch unreachable")
        }
    }

    fun setMirrorAlerts(enabled: Boolean) =
        viewModelScope.launch { a.settings.setMirrorAlerts(enabled) }

    fun setNotificationVibration(enabled: Boolean) =
        viewModelScope.launch { a.settings.setNotificationVibration(enabled) }

    fun setAutoSync(enabled: Boolean) = viewModelScope.launch { a.settings.setAutoSync(enabled) }

    fun setSyncWindowDays(days: Int) = viewModelScope.launch { a.settings.setSyncWindowDays(days) }

    fun setBaseline(value: Int) = viewModelScope.launch { a.settings.setBaseline(value) }

    fun syncFromWatch() {
        viewModelScope.launch {
            _sync.value = SyncStatus(syncing = true)
            val days = a.settings.syncWindowDays.first().toLong()
            val now = System.currentTimeMillis()
            val from = now - days * 24L * 60 * 60 * 1000
            val resolution = if (days <= 1) "1min" else if (days <= 7) "5min" else "30min"
            a.watch.requestHistory(from, now, resolution)
            _sync.value = SyncStatus(syncing = false, lastResult = "Sync requested")
        }
    }

    fun refreshConnection() {
        viewModelScope.launch {
            val connected = a.watch.isConnected()
            a.settings.setWatchConnected(connected)
            if (connected) a.watch.requestSettingsFromWatch()
        }
    }

    fun clearSyncResult() {
        _sync.value = _sync.value.copy(lastResult = null)
    }
}
