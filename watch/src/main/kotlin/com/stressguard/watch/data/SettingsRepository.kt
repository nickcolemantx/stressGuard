package com.stressguard.watch.data

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

private val Context.settingsStore by preferencesDataStore(name = "stress_settings")

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

    val silencedUntilMs: Flow<Long> = store.data.map { p -> p[K.silencedUntilMs] ?: 0L }

    val syncEnabled: Flow<Boolean> = store.data.map { p -> p[K.syncEnabled] ?: true }

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

    suspend fun setBaseline(value: Int) {
        store.edit { it[K.baseline] = value.coerceIn(0, 100) }
    }

    suspend fun silenceFor(durationMin: Int, nowMs: Long = System.currentTimeMillis()) {
        store.edit { it[K.silencedUntilMs] = nowMs + durationMin.toLong() * 60_000L }
    }

    suspend fun clearSilence() {
        store.edit { it[K.silencedUntilMs] = 0L }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        store.edit { it[K.syncEnabled] = enabled }
    }

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
        val silencedUntilMs = longPreferencesKey("silenced_until_ms")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
    }
}
