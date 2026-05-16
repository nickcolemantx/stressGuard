package com.stressguard.watch.service

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.stressguard.shared.model.AlertEvent
import com.stressguard.shared.model.AlertSettings
import com.stressguard.shared.model.StressReading
import com.stressguard.shared.protocol.ErrorCodes
import com.stressguard.shared.protocol.MessageTypes
import com.stressguard.shared.protocol.Paths
import com.stressguard.watch.app.StressGuardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PhoneSyncService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override fun onMessageReceived(event: MessageEvent) {
        val payload = String(event.data)
        val parsed = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        when (event.path) {
            Paths.SILENCE_COMMAND -> handleSilence(parsed)
            Paths.SETTINGS_REQUEST -> respondWithSettings(event.sourceNodeId)
            Paths.HISTORY_REQUEST -> handleHistoryRequest(event.sourceNodeId, parsed)
        }
    }

    private fun handleSilence(payload: JsonObject) {
        val duration = payload["duration_min"]?.jsonPrimitive?.intOrNull ?: return
        val settings = SettingsAccess.repo(this)
        scope.launch { settings.silenceFor(duration) }
    }

    private fun respondWithSettings(nodeId: String) {
        val settings = SettingsAccess.repo(this)
        scope.launch {
            val s = settings.settings.first()
            val baseline = settings.baseline.first()
            val out = buildJsonObject {
                put("type", JsonPrimitive(MessageTypes.SETTINGS_RESPONSE))
                put("threshold", JsonPrimitive(s.threshold))
                put("spike_delta", JsonPrimitive(s.spikeDelta))
                put("spike_window_min", JsonPrimitive(s.spikeWindowMin))
                put("cooldown_min", JsonPrimitive(s.cooldownMin))
                put("re_alert_if_sustained", JsonPrimitive(s.reAlertIfSustained))
                put("vibration_pattern", JsonPrimitive(s.vibrationPattern))
                put("baseline", JsonPrimitive(baseline))
                put("baseline_override", s.baselineOverride?.let { JsonPrimitive(it) } ?: JsonNull)
                put("night_mode", JsonPrimitive(s.nightMode))
            }
            sendMessage(nodeId, Paths.SETTINGS_RESPONSE, out.toString().toByteArray())
        }
    }

    private fun handleHistoryRequest(nodeId: String, payload: JsonObject) {
        val from = payload["from"]?.jsonPrimitive?.contentLong() ?: return
        val to = payload["to"]?.jsonPrimitive?.contentLong() ?: return
        val resolution = payload["resolution"]?.jsonPrimitive?.content ?: "1min"
        val store = DataStoreService(applicationContext)
        scope.launch {
            val raw = store.readingsBetween(from, to)
            val downsampled = downsample(raw, resolution)
            val chunks = downsampled.chunked(50)
            chunks.forEachIndexed { index, chunk ->
                val out = buildJsonObject {
                    put("type", JsonPrimitive(MessageTypes.HISTORY_CHUNK))
                    put("chunk_index", JsonPrimitive(index))
                    put("total_chunks", JsonPrimitive(chunks.size))
                    put("readings", json.parseToJsonElement(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(StressReading.serializer()), chunk)))
                }
                sendMessage(nodeId, Paths.HISTORY_CHUNK, out.toString().toByteArray())
            }
            if (chunks.isEmpty()) {
                sendError(nodeId, MessageTypes.HISTORY_REQUEST, ErrorCodes.HISTORY_UNAVAILABLE, "No readings in requested range")
            }
        }
    }

    private fun downsample(readings: List<StressReading>, resolution: String): List<StressReading> {
        val bucketMin = when (resolution) {
            "5min" -> 5
            "30min" -> 30
            "1day" -> 60 * 24
            else -> 1
        }
        if (bucketMin <= 1) return readings
        val bucketMs = bucketMin.toLong() * 60_000L
        return readings.groupBy { java.time.Instant.parse(it.timestamp).toEpochMilli() / bucketMs }
            .map { (bucket, entries) ->
                val avg = entries.map { it.score }.average().toInt()
                StressReading(
                    timestamp = java.time.Instant.ofEpochMilli(bucket * bucketMs).toString(),
                    score = avg,
                    baseline = entries.firstNotNullOfOrNull { it.baseline },
                    hr = entries.mapNotNull { it.hr }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                )
            }
            .sortedBy { it.timestamp }
    }

    private fun sendError(nodeId: String, inResponseTo: String, code: String, message: String) {
        val out = buildJsonObject {
            put("type", JsonPrimitive(MessageTypes.ERROR))
            put("in_response_to", JsonPrimitive(inResponseTo))
            put("code", JsonPrimitive(code))
            put("message", JsonPrimitive(message))
        }
        sendMessage(nodeId, Paths.ERROR, out.toString().toByteArray())
    }

    private fun sendMessage(nodeId: String, path: String, data: ByteArray) {
        runCatching {
            Tasks.await(Wearable.getMessageClient(applicationContext).sendMessage(nodeId, path, data))
        }.onFailure { Log.w(TAG, "sendMessage failed for $path: ${it.message}") }
    }

    private fun JsonPrimitive.contentLong(): Long? =
        contentOrNull()?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: runCatching { content.toLong() }.getOrNull()

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else null

    object SettingsAccess {
        fun repo(context: Context) = StressGuardApp.from(context).settings
    }

    companion object {
        private const val TAG = "PhoneSyncService"

        fun broadcastReading(context: Context, reading: StressReading) {
            val out = buildJsonObject {
                put("type", JsonPrimitive(MessageTypes.STRESS_READING))
                put("timestamp", JsonPrimitive(reading.timestamp))
                put("score", JsonPrimitive(reading.score))
                reading.baseline?.let { put("baseline", JsonPrimitive(it)) }
                reading.hr?.let { put("hr", JsonPrimitive(it)) }
            }
            sendToAll(context, Paths.STRESS_READING, out.toString().toByteArray())
        }

        fun broadcastAlert(context: Context, event: AlertEvent) {
            val out = buildJsonObject {
                put("type", JsonPrimitive(MessageTypes.ALERT_FIRED))
                put("timestamp", JsonPrimitive(event.timestamp))
                put("score", JsonPrimitive(event.score))
                put("delta", JsonPrimitive(event.delta))
                put("delta_window_min", JsonPrimitive(event.deltaWindowMin))
                put("threshold_used", JsonPrimitive(event.thresholdUsed))
                put("silence_duration_min", JsonPrimitive(event.silenceDurationMin))
            }
            sendToAll(context, Paths.ALERT_FIRED, out.toString().toByteArray())
        }

        fun pushSettings(context: Context, settings: AlertSettings, baseline: Int) {
            val out = buildJsonObject {
                put("type", JsonPrimitive(MessageTypes.SETTINGS_PUSH))
                put("threshold", JsonPrimitive(settings.threshold))
                put("spike_delta", JsonPrimitive(settings.spikeDelta))
                put("spike_window_min", JsonPrimitive(settings.spikeWindowMin))
                put("cooldown_min", JsonPrimitive(settings.cooldownMin))
                put("re_alert_if_sustained", JsonPrimitive(settings.reAlertIfSustained))
                put("vibration_pattern", JsonPrimitive(settings.vibrationPattern))
                put("baseline", JsonPrimitive(baseline))
                put("night_mode", JsonPrimitive(settings.nightMode))
                put("night_mode_start", JsonPrimitive(settings.nightModeStart))
                put("night_mode_end", JsonPrimitive(settings.nightModeEnd))
            }
            val request = PutDataMapRequest.create(Paths.SETTINGS_PUSH).apply {
                dataMap.putString("payload", out.toString())
                dataMap.putLong("ts", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            runCatching { Tasks.await(Wearable.getDataClient(context).putDataItem(request)) }
        }

        private fun sendToAll(context: Context, path: String, data: ByteArray) {
            val nodeClient = Wearable.getNodeClient(context)
            val messageClient = Wearable.getMessageClient(context)
            runCatching {
                val nodes = Tasks.await(nodeClient.connectedNodes)
                nodes.forEach { node ->
                    runCatching { Tasks.await(messageClient.sendMessage(node.id, path, data)) }
                }
            }.onFailure { Log.w(TAG, "sendToAll failed for $path: ${it.message}") }
        }
    }
}
