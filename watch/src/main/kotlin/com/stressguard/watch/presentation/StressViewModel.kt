package com.stressguard.watch.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stressguard.shared.model.AlertEvent
import com.stressguard.shared.model.AlertSettings
import com.stressguard.shared.model.StressReading
import com.stressguard.watch.app.StressGuardApp
import com.stressguard.watch.service.PhoneSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StressViewModel(app: Application) : AndroidViewModel(app) {

    enum class Window(val labelMinutes: Int, val xLabel: String) {
        FOUR_HOUR(4 * 60, "4H"),
        ONE_DAY(24 * 60, "1D"),
        ONE_WEEK(7 * 24 * 60, "1W"),
        ONE_MONTH(30 * 24 * 60, "1M"),
        ONE_YEAR(365 * 24 * 60, "1Y"),
    }

    data class AlertOverlayState(
        val visible: Boolean,
        val event: AlertEvent? = null,
    )

    data class HomeState(
        val current: StressReading?,
        val settings: AlertSettings,
        val baseline: Int,
        val silencedUntilMs: Long,
        val syncEnabled: Boolean,
    )

    private val a = StressGuardApp.from(app)

    val state: StateFlow<HomeState> = combine(
        a.store.latestReading,
        a.settings.settings,
        a.settings.baseline,
        a.settings.silencedUntilMs,
        a.settings.syncEnabled,
    ) { reading, settings, baseline, silencedUntil, syncEnabled ->
        HomeState(reading, settings, baseline, silencedUntil, syncEnabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeState(null, AlertSettings(), 48, 0L, true),
    )

    private val _alertOverlay = MutableStateFlow(AlertOverlayState(false))
    val alertOverlay: StateFlow<AlertOverlayState> = _alertOverlay

    suspend fun loadDaySnapshot(): DaySnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val readings = a.store.readingsBetween(start, end)
        val alertCount = a.store.alertCountSince(start)
        val lastAlert = a.store.alertsBetween(start, end).maxByOrNull { Instant.parse(it.timestamp) }
        val peak = readings.maxByOrNull { it.score }
        val avg = readings.takeIf { it.isNotEmpty() }?.map { it.score }?.average()?.toInt()
        return DaySnapshot(readings, alertCount, lastAlert, peak, avg)
    }

    suspend fun loadWindow(window: Window): List<StressReading> {
        val now = System.currentTimeMillis()
        return a.store.readingsBetween(now - window.labelMinutes.toLong() * 60_000L, now)
    }

    fun showAlert(event: AlertEvent) {
        _alertOverlay.value = AlertOverlayState(true, event)
    }

    fun dismissAlert() {
        _alertOverlay.value = AlertOverlayState(false)
    }

    fun silence(durationMin: Int) {
        viewModelScope.launch {
            a.settings.silenceFor(durationMin)
            _alertOverlay.value = AlertOverlayState(false)
        }
    }

    fun clearSilence() {
        viewModelScope.launch { a.settings.clearSilence() }
    }

    fun updateSettings(settings: AlertSettings) {
        viewModelScope.launch {
            a.settings.update(settings)
            val baseline = a.settings.baseline.first()
            val syncEnabled = a.settings.syncEnabled.first()
            if (syncEnabled) PhoneSyncService.pushSettings(getApplication(), settings, baseline)
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { a.settings.setSyncEnabled(enabled) }
    }

    fun setBaseline(value: Int) {
        viewModelScope.launch { a.settings.setBaseline(value) }
    }

    fun recalculateBaseline() {
        viewModelScope.launch {
            val sevenDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val recent = a.store.readingsBetween(sevenDaysAgo, System.currentTimeMillis())
                .map { it.score }.filter { it < 50 }
            if (recent.isNotEmpty()) a.settings.setBaseline(recent.average().toInt())
        }
    }

    fun testHaptics(pattern: String) = a.alerts.manualVibrate(pattern)

    data class DaySnapshot(
        val readings: List<StressReading>,
        val alertCount: Int,
        val lastAlert: AlertEvent?,
        val peak: StressReading?,
        val avg: Int?,
    )
}
