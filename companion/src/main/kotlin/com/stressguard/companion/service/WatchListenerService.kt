package com.stressguard.companion.service

import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.stressguard.companion.app.StressGuardCompanionApp
import com.stressguard.shared.model.AlertEvent
import com.stressguard.shared.model.AlertSettings
import com.stressguard.shared.model.StressReading
import com.stressguard.shared.protocol.Paths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WatchListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val readingsListSerializer = ListSerializer(StressReading.serializer())

    override fun onMessageReceived(event: MessageEvent) {
        val obj = parse(event.data) ?: return
        when (event.path) {
            Paths.STRESS_READING -> handleReading(obj)
            Paths.ALERT_FIRED -> handleAlert(obj)
            Paths.SETTINGS_RESPONSE -> handleSettingsResponse(obj)
            Paths.HISTORY_CHUNK -> handleHistoryChunk(obj)
            Paths.ERROR -> Log.w(TAG, "Watch error: ${obj["code"]?.jsonPrimitive?.contentOrNull}")
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { ev ->
            if (ev.type != DataEvent.TYPE_CHANGED) return@forEach
            if (ev.dataItem.uri.path != Paths.SETTINGS_PUSH) return@forEach
            val map = DataMapItem.fromDataItem(ev.dataItem).dataMap
            val payload = map.getString("payload") ?: return@forEach
            val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return@forEach
            handleSettingsResponse(obj)
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        val app = StressGuardCompanionApp.from(this)
        scope.launch { app.settings.setWatchConnected(info.nodes.isNotEmpty()) }
    }

    private fun handleReading(obj: JsonObject) {
        val ts = obj.string("timestamp") ?: return
        val score = obj.intField("score") ?: return
        val reading = StressReading(
            timestamp = ts,
            score = score,
            baseline = obj.intField("baseline"),
            hr = obj.intField("hr"),
        )
        val app = StressGuardCompanionApp.from(this)
        scope.launch {
            app.store.saveReading(reading)
            app.settings.setWatchConnected(true)
        }
    }

    private fun handleAlert(obj: JsonObject) {
        val ts = obj.string("timestamp") ?: return
        val score = obj.intField("score") ?: return
        val event = AlertEvent(
            timestamp = ts,
            score = score,
            delta = obj.intField("delta") ?: 0,
            deltaWindowMin = obj.intField("delta_window_min") ?: 0,
            thresholdUsed = obj.intField("threshold_used") ?: 0,
            silenceDurationMin = obj.intField("silence_duration_min") ?: 0,
        )
        val app = StressGuardCompanionApp.from(this)
        scope.launch {
            app.store.saveAlert(event)
            val mirror = app.settings.mirrorAlertsToPhone.first()
            val vibrate = app.settings.notificationVibration.first()
            if (mirror) app.notifier.showAlert(event, vibrate)
        }
    }

    private fun handleSettingsResponse(obj: JsonObject) {
        val app = StressGuardCompanionApp.from(this)
        scope.launch {
            val s = AlertSettings(
                threshold = obj.intField("threshold") ?: 70,
                spikeDelta = obj.intField("spike_delta") ?: 20,
                spikeWindowMin = obj.intField("spike_window_min") ?: 5,
                cooldownMin = obj.intField("cooldown_min") ?: 15,
                reAlertIfSustained = obj.booleanField("re_alert_if_sustained") ?: true,
                vibrationPattern = obj.string("vibration_pattern") ?: "standard",
                baselineOverride = obj.intField("baseline_override"),
                nightMode = obj.booleanField("night_mode") ?: false,
                nightModeStart = obj.string("night_mode_start") ?: "23:00",
                nightModeEnd = obj.string("night_mode_end") ?: "07:00",
            )
            app.settings.update(s)
            obj.intField("baseline")?.let { app.settings.setBaseline(it) }
        }
    }

    private fun handleHistoryChunk(obj: JsonObject) {
        val readingsElement = obj["readings"] ?: return
        val readings = runCatching {
            json.decodeFromJsonElement(readingsListSerializer, readingsElement)
        }.getOrNull() ?: return
        val app = StressGuardCompanionApp.from(this)
        scope.launch {
            app.store.saveReadings(readings)
            val total = obj.intField("total_chunks") ?: 1
            val index = obj.intField("chunk_index") ?: 0
            if (index == total - 1) app.settings.markSyncedNow()
        }
    }

    private fun parse(data: ByteArray): JsonObject? =
        runCatching { json.parseToJsonElement(String(data)).jsonObject }.getOrNull()

    private fun JsonObject.intField(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.booleanField(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    companion object {
        private const val TAG = "WatchListenerService"
    }
}
