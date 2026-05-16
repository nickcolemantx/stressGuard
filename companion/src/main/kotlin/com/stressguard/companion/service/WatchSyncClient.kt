package com.stressguard.companion.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.stressguard.shared.model.AlertSettings
import com.stressguard.shared.protocol.MessageTypes
import com.stressguard.shared.protocol.Paths
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

class WatchSyncClient(private val context: Context) {

    suspend fun connectedNodeIds(): List<String> = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await().map { it.id }
    }.getOrDefault(emptyList())

    suspend fun isConnected(): Boolean = connectedNodeIds().isNotEmpty()

    suspend fun pushSettings(settings: AlertSettings, baseline: Int): Boolean {
        val out = buildJsonObject {
            put("type", JsonPrimitive(MessageTypes.SETTINGS_PUSH))
            put("threshold", JsonPrimitive(settings.threshold))
            put("spike_delta", JsonPrimitive(settings.spikeDelta))
            put("spike_window_min", JsonPrimitive(settings.spikeWindowMin))
            put("cooldown_min", JsonPrimitive(settings.cooldownMin))
            put("re_alert_if_sustained", JsonPrimitive(settings.reAlertIfSustained))
            put("vibration_pattern", JsonPrimitive(settings.vibrationPattern))
            put(
                "baseline_override",
                settings.baselineOverride?.let { JsonPrimitive(it) } ?: JsonNull,
            )
            put("baseline", JsonPrimitive(baseline))
            put("night_mode", JsonPrimitive(settings.nightMode))
            put("night_mode_start", JsonPrimitive(settings.nightModeStart))
            put("night_mode_end", JsonPrimitive(settings.nightModeEnd))
        }
        val request = PutDataMapRequest.create(Paths.SETTINGS_PUSH).apply {
            dataMap.putString("payload", out.toString())
            dataMap.putLong("ts", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        return runCatching {
            Wearable.getDataClient(context).putDataItem(request).await()
            true
        }.onFailure { Log.w(TAG, "pushSettings failed: ${it.message}") }.getOrDefault(false)
    }

    suspend fun requestHistory(fromMs: Long, toMs: Long, resolution: String) {
        val payload = buildJsonObject {
            put("type", JsonPrimitive(MessageTypes.HISTORY_REQUEST))
            put("from", JsonPrimitive(Instant.ofEpochMilli(fromMs).toString()))
            put("to", JsonPrimitive(Instant.ofEpochMilli(toMs).toString()))
            put("resolution", JsonPrimitive(resolution))
        }.toString().toByteArray()
        sendToAll(Paths.HISTORY_REQUEST, payload)
    }

    suspend fun requestSettingsFromWatch() {
        val payload = buildJsonObject {
            put("type", JsonPrimitive(MessageTypes.SETTINGS_REQUEST))
        }.toString().toByteArray()
        sendToAll(Paths.SETTINGS_REQUEST, payload)
    }

    suspend fun sendSilenceCommand(durationMin: Int) {
        val payload = buildJsonObject {
            put("type", JsonPrimitive(MessageTypes.SILENCE_COMMAND))
            put("duration_min", JsonPrimitive(durationMin))
        }.toString().toByteArray()
        sendToAll(Paths.SILENCE_COMMAND, payload)
    }

    private suspend fun sendToAll(path: String, data: ByteArray) {
        val nodes = connectedNodeIds()
        val client = Wearable.getMessageClient(context)
        nodes.forEach { id ->
            runCatching { client.sendMessage(id, path, data).await() }
                .onFailure { Log.w(TAG, "send $path failed: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "WatchSyncClient"
    }
}
