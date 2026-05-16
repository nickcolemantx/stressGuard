package com.stressguard.companion.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stressguard.shared.model.AlertSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "companion_settings")

class SettingsRepository(private val context: Context) {

    private val store = context.settingsStore

    val settings: Flow<AlertSettings> = store.data.map { p ->
        AlertSettings(
            threshold = p[K.threshold] ?: 70,
            spikeDelta = p[K.spikeDelta] ?: 20,
            spikeWindowMin = p[K.spikeWindowMin] ?: 5,
            cooldownMin = p[K.cooldownMin] ?: 15,
            reAlertIfSustained = p[K.reAlertIfSustained] ?: true,
            vibrationPattern = p[K.vibrationPattern] ?: "standard",
            baselineOverride = p[K.baselineOverride],
            nightMode = p[K.nightMode] ?: false,
            nightModeStart = p[K.nightModeStart] ?: "23:00",
            nightModeEnd = p[K.nightModeEnd] ?: "07:00",
        )
    }

    val baseline: Flow<Int> = store.data.map { p -> p[K.baseline] ?: 48 }

    val mirrorAlertsToPhone: Flow<Boolean> = store.data.map { p -> p[K.mirrorAlerts] ?: true }

    val notificationVibration: Flow<Boolean> = store.data.map { p -> p[K.notifVibration] ?: true }

    val autoSync: Flow<Boolean> = store.data.map { p -> p[K.autoSync] ?: true }

    val syncWindowDays: Flow<Int> = store.data.map { p -> p[K.syncWindowDays] ?: 30 }

    val lastSyncedMs: Flow<Long> = store.data.map { p -> p[K.lastSyncedMs] ?: 0L }

    val watchConnected: Flow<Boolean> = store.data.map { p -> p[K.watchConnected] ?: false }

    suspend fun update(s: AlertSettings) {
        store.edit { p ->
            p[K.threshold] = s.threshold
            p[K.spikeDelta] = s.spikeDelta
            p[K.spikeWindowMin] = s.spikeWindowMin
            p[K.cooldownMin] = s.cooldownMin
            p[K.reAlertIfSustained] = s.reAlertIfSustained
            p[K.vibrationPattern] = s.vibrationPattern
            if (s.baselineOverride != null) p[K.baselineOverride] = s.baselineOverride else p.remove(K.baselineOverride)
            p[K.nightMode] = s.nightMode
            p[K.nightModeStart] = s.nightModeStart
            p[K.nightModeEnd] = s.nightModeEnd
        }
    }

    suspend fun setBaseline(value: Int) = store.edit { it[K.baseline] = value.coerceIn(0, 100) }

    suspend fun setMirrorAlerts(enabled: Boolean) = store.edit { it[K.mirrorAlerts] = enabled }

    suspend fun setNotificationVibration(enabled: Boolean) = store.edit { it[K.notifVibration] = enabled }

    suspend fun setAutoSync(enabled: Boolean) = store.edit { it[K.autoSync] = enabled }

    suspend fun setSyncWindowDays(days: Int) = store.edit { it[K.syncWindowDays] = days.coerceIn(1, 365) }

    suspend fun markSyncedNow() = store.edit { it[K.lastSyncedMs] = System.currentTimeMillis() }

    suspend fun setWatchConnected(connected: Boolean) = store.edit { it[K.watchConnected] = connected }

    private object K {
        val threshold = intPreferencesKey("threshold")
        val spikeDelta = intPreferencesKey("spike_delta")
        val spikeWindowMin = intPreferencesKey("spike_window_min")
        val cooldownMin = intPreferencesKey("cooldown_min")
        val reAlertIfSustained = booleanPreferencesKey("re_alert_if_sustained")
        val vibrationPattern = stringPreferencesKey("vibration_pattern")
        val baselineOverride = intPreferencesKey("baseline_override")
        val baseline = intPreferencesKey("baseline")
        val nightMode = booleanPreferencesKey("night_mode")
        val nightModeStart = stringPreferencesKey("night_mode_start")
        val nightModeEnd = stringPreferencesKey("night_mode_end")
        val mirrorAlerts = booleanPreferencesKey("mirror_alerts")
        val notifVibration = booleanPreferencesKey("notification_vibration")
        val autoSync = booleanPreferencesKey("auto_sync")
        val syncWindowDays = intPreferencesKey("sync_window_days")
        val lastSyncedMs = longPreferencesKey("last_synced_ms")
        val watchConnected = booleanPreferencesKey("watch_connected")
    }
}
