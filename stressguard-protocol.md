# StressGuard — Shared Communication Protocol
### `stressguard-protocol.md`

**Version:** 2.0  
**Transport:** Wearable Data Layer API (`play-services-wearable`) — built into Wear OS, no extra SDK  
**Format:** JSON strings passed as `MessageClient` byte payloads  
**Location:** Shared constants live in `:shared/protocol/WearMessages.kt`

---

## Transport Overview

Both watch and companion register a `WearableListenerService` and use `MessageClient` for fire-and-forget messages. For queued delivery when devices are out of range, `DataClient` is used for settings and history chunks.

```kotlin
// WearMessages.kt (in :shared module)
object Paths {
    const val STRESS_READING   = "/stress/reading"
    const val ALERT_FIRED      = "/stress/alert"
    const val SILENCE_COMMAND  = "/stress/silence"
    const val SETTINGS_PUSH    = "/settings/push"
    const val SETTINGS_REQUEST = "/settings/request"
    const val SETTINGS_RESPONSE = "/settings/response"
    const val HISTORY_REQUEST  = "/history/request"
    const val HISTORY_CHUNK    = "/history/chunk"
    const val ERROR            = "/error"
}
```

---

## Message Schema

All payloads are JSON. Serialise with `kotlinx.serialization` (already on classpath via Compose).

---

### STRESS_READING
**Watch → Phone** · Sent every 1 minute when connected.

```json
{
  "type": "STRESS_READING",
  "timestamp": "2026-04-29T11:42:00Z",
  "score": 74,
  "baseline": 48,
  "hr": 72
}
```

---

### ALERT_FIRED
**Watch → Phone** · Sent immediately when spike detected.

```json
{
  "type": "ALERT_FIRED",
  "timestamp": "2026-04-29T11:38:00Z",
  "score": 74,
  "delta": 22,
  "delta_window_min": 4,
  "threshold_used": 70,
  "silence_duration_min": 0
}
```

---

### SILENCE_COMMAND
**Phone → Watch** · Sent from companion notification action button.

```json
{
  "type": "SILENCE_COMMAND",
  "duration_min": 10
}
```

---

### SETTINGS_PUSH
**Phone → Watch** · Sent when user taps "Save & Push to Watch".  
Delivered via `DataClient` so it arrives even if watch is out of range.

```json
{
  "type": "SETTINGS_PUSH",
  "threshold": 70,
  "spike_delta": 20,
  "spike_window_min": 5,
  "cooldown_min": 15,
  "re_alert_if_sustained": true,
  "vibration_pattern": "standard",
  "baseline_override": null,
  "night_mode": false,
  "night_mode_start": "23:00",
  "night_mode_end": "07:00"
}
```

---

### SETTINGS_REQUEST / SETTINGS_RESPONSE
**Phone → Watch / Watch → Phone** · Phone requests current settings on app open.

```json
// Request
{ "type": "SETTINGS_REQUEST" }

// Response
{
  "type": "SETTINGS_RESPONSE",
  "threshold": 70,
  "spike_delta": 20,
  "spike_window_min": 5,
  "cooldown_min": 15,
  "re_alert_if_sustained": true,
  "vibration_pattern": "standard",
  "baseline": 48,
  "baseline_override": null,
  "night_mode": false
}
```

---

### HISTORY_REQUEST / HISTORY_CHUNK
**Phone → Watch / Watch → Phone** · Paginated history sync.

```json
// Request
{
  "type": "HISTORY_REQUEST",
  "from": "2026-04-22T00:00:00Z",
  "to":   "2026-04-29T23:59:59Z",
  "resolution": "1min"
}
// resolution options: "1min" | "5min" | "30min" | "1day"

// Response (paginated — phone ACKs each chunk before next is sent)
{
  "type": "HISTORY_CHUNK",
  "chunk_index": 0,
  "total_chunks": 4,
  "readings": [
    { "ts": "2026-04-29T11:42:00Z", "score": 74 },
    { "ts": "2026-04-29T11:41:00Z", "score": 70 }
  ]
}
```

---

### ERROR

```json
{
  "type": "ERROR",
  "in_response_to": "SETTINGS_PUSH",
  "code": "HEALTH_PERMISSION_DENIED",
  "message": "Samsung Health stress permission not granted"
}
```

Common error codes: `HEALTH_PERMISSION_DENIED`, `WEARABLE_DISCONNECTED`, `INVALID_SETTINGS_VALUE`, `HISTORY_UNAVAILABLE`

---

## Implementation Notes

- Use `MessageClient` for real-time messages (readings, alerts, silence commands)
- Use `DataClient` for settings — it queues and delivers even when devices aren't currently connected
- Both sides should handle `onMessageReceived` in their `WearableListenerService` subclass
- Chunk size for HISTORY_CHUNK: 50 readings max per chunk to stay within Wearable Data Layer message size limits
