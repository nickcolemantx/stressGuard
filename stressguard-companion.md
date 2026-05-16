# StressGuard — Android Companion App Design Specification
### `stressguard-companion.md`

**Version:** 2.0  
**Platform:** Android 10+ (API 29+)  
**Language:** Kotlin  
**IDE:** Android Studio — same project as `:watch`, module `:companion`  
**Communication:** Wearable Data Layer API (Google, built into Wear OS ecosystem)

---

## 1. Overview

The companion app runs on your Android phone and provides:
- Full-screen stress history chart with scroll/zoom (same windows as watch, larger display)
- Remote settings — change alert thresholds on phone, push to watch
- Phone notifications mirroring watch alerts (useful when watch is on charger)
- Data export as CSV

The watch is the source of truth for real-time data. The companion is a viewer and configuration tool.

---

## 2. Setup (Same Android Studio Project)

No additional IDE or tooling needed beyond what `stressguard-watch.md` covers. The `:companion` module shares the same project, same Gradle sync, same Git repo.

### `:companion` `build.gradle.kts`
```kotlin
dependencies {
    implementation(project(":shared"))

    // Same Wearable Data Layer as watch — handles all comms
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Samsung Health Data SDK (for reading stress history on phone side)
    implementation(files("../libs/samsung-health-data-sdk.aar"))

    // UI
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Charts
    implementation("com.patrykandpatrick.vico:compose:1.13.0")

    // Local storage
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Notifications
    implementation("androidx.core:core-ktx:1.12.0")
}
```

### Module Structure
```
companion/
└── src/main/kotlin/com/stressguard/companion/
    ├── ui/
    │   ├── DashboardScreen.kt      ← current status + mini chart
    │   ├── HistoryScreen.kt        ← full scrollable/zoomable chart
    │   ├── SettingsScreen.kt       ← alert config + push to watch
    │   └── ExportScreen.kt
    ├── service/
    │   ├── WatchListenerService.kt ← WearableListenerService impl
    │   └── NotificationService.kt
    └── data/
        ├── StressDatabase.kt       ← Room
        ├── StressReadingDao.kt
        └── SettingsRepository.kt
```

---

## 3. Screen Architecture

```
Bottom navigation:
[ Dashboard ]  [ History ]  [ Settings ]  [ Export ]
```

---

## 4. Dashboard Screen

```
┌─────────────────────────────────────────────────────┐
│  StressGuard                          🔗 Connected  │
│                                                     │
│   CURRENT STRESS                                    │
│   ┌─────────────────────────────────────────────┐   │
│   │          74  🔴 HIGH                         │   │
│   │       Last updated: 11:42 AM                │   │
│   └─────────────────────────────────────────────┘   │
│                                                     │
│   TODAY'S SUMMARY                                   │
│   Peak: 81 at 2:14 PM   Average: 61   Alerts: 3    │
│   Baseline: 48                                      │
│                                                     │
│   LAST 4 HOURS                                      │
│   ┌─────────────────────────────────────────────┐   │
│   │  [Mini chart — colour-coded, same as watch] │   │
│   └─────────────────────────────────────────────┘   │
│                               View full history →   │
│                                                     │
│   RECENT ALERTS                                     │
│   ⚡ 11:38 AM  Score 74, +22 pts in 4 min           │
│   ⚡ 10:12 AM  Score 71, +20 pts in 5 min           │
│   ⚡  9:04 AM  Score 76, +25 pts in 3 min           │
└─────────────────────────────────────────────────────┘
```

---

## 5. History Screen

Full scrollable, zoomable chart. Mirrors watch Detail View but on a larger screen.

### Time Window Selector
```
[ 4H ]  [ 1D ]  [ 1W ]  [ 1M ]  [ 1Y ]
```
Same windows and resolutions as the watch (see watch spec §9).

### Chart Layout
```
┌─────────────────────────────────────────────────────┐
│  STRESS HISTORY             [ 4H ][ 1D ][ 1W ]…    │
│                                                     │
│  100 ┤                    ╭──╮                      │
│   75 ┤  ──────────────────╯  ╲╱──── ● 74           │
│   50 ┤  · · · · · · · · · · · · · ·  Baseline 48  │
│   25 ┤                                              │
│    0 └─────────────────────────────────────────────│
│       4h ago                                  now   │
│                                                     │
│  ← swipe to scroll                   pinch to zoom  │
│                                                     │
│  ⚡ 11:38  Score 74, +22 pts                         │
│  ⚡ 10:12  Score 71, +20 pts                         │
│                                                     │
│  [↩ Return to live]          [🔄 Sync from watch]   │
└─────────────────────────────────────────────────────┘
```

### Gestures

| Gesture | Action |
|---|---|
| Swipe left/right | Pan through history |
| Pinch to zoom | Widen/narrow window (snaps to 4H/1D/1W/1M/1Y) |
| Tap data point | Tooltip with score, time, delta, alert status |
| Long-press region | Select range → shows min/avg/max/alert count |
| "Sync from watch" | Pulls latest history over Wearable Data Layer |

### Alert Marker Tooltip (tap ⚡)
```
┌──────────────────────────────┐
│ ⚡ Stress Spike              │
│ 11:38 AM — Score: 74        │
│ Rise: +22 pts in 4 minutes  │
│ Silenced for: 10 min        │
└──────────────────────────────┘
```

---

## 6. Settings Screen

Full-size controls. Changes pushed to watch when "Save & Push" is tapped.

### Alert Thresholds
```
Score threshold
  Trigger when score exceeds:     [────●──────]  70  (50–90)

Spike delta
  Trigger when score rises by:    [──────●────]  20 pts  (10–40)

Spike window
  Rise measured over:             ( ) 2 min
                                  (●) 5 min
                                  ( ) 10 min
                                  ( ) 15 min
```

### Alert Behaviour
```
Alert cooldown                    (●) 15 min
                                  ( ) 5 / 30 / 60 min

Re-alert if stress stays high     [ON ●]

Night mode (suppress during sleep)
  Auto-detect sleep               [ON ●]
  Manual window                   [OFF]  (e.g. 11 PM – 7 AM)
```

### Vibration & Sound (controls watch behaviour)
```
Vibration pattern                 (●) Standard
                                  ( ) Strong
                                  ( ) Single pulse
                                  ( ) Off (visual only)

Alert sound on watch              [Off ▾]
```

### Baseline
```
Current baseline:   48  (auto, recalculated Apr 29)
Manual override:    [OFF]
Manual value:       [48]  ← visible if override ON
[ Recalculate baseline now ]
```

### Phone Notifications
```
Mirror watch alerts to phone      [ON ●]
  Fires even when watch is on charger or out of haptic range

Notification sound                [Default ▾]
Notification vibration            [ON ●]
```

### Sync
```
Auto-sync when Bluetooth connected  [ON ●]
Sync history window                 [1 Month ▾]
Last synced:                        Today 11:41 AM
[ Sync now ]
```

### Save
```
[ Cancel ]                     [ Save & Push to Watch ]
```
"Save & Push" sends a `SETTINGS_PUSH` message over Wearable Data Layer. Shows a "Settings pushed to watch ✓" toast on success.

---

## 7. Phone Notifications

Fired when `ALERT_FIRED` message received from watch:

```
┌──────────────────────────────────────────────┐
│ 🔴 StressGuard — Stress Spike                │
│ Score 74 (+22 pts in 4 min)                  │
│ ─────────────────────────────────────────────│
│ [Silence 10 min]               [Open app]    │
└──────────────────────────────────────────────┘
```

- "Silence 10 min" sends a `SILENCE_COMMAND` to the watch via Wearable Data Layer
- "Open app" opens History screen scrolled to the alert timestamp
- Notification channel: `StressGuard Alerts` (HIGH importance, user can adjust in Android settings)

---

## 8. Export Screen

```
┌─────────────────────────────────────────────────────┐
│  EXPORT DATA                                        │
│                                                     │
│  Date range:                                        │
│  From: [ Apr 1, 2026 ]     To: [ Apr 29, 2026 ]    │
│                                                     │
│  Include:                                           │
│  ☑ Raw readings (1-min intervals)                   │
│  ☑ Alert events                                     │
│  ☑ Daily summaries                                  │
│  ☐ Settings history                                 │
│                                                     │
│  Format:   (●) CSV   ( ) JSON                       │
│                                                     │
│  [ Export & Share ]                                 │
│  → Android share sheet: Files, email, etc.          │
└─────────────────────────────────────────────────────┘
```

### CSV Format
```csv
timestamp,stress_score,baseline,alert_fired,silence_duration_min
2026-04-29T11:38:00,74,48,true,10
2026-04-29T11:37:00,71,48,false,0
```

---

## 9. Local Data Storage

| Table | Retention | Detail |
|---|---|---|
| `stress_readings` | 1 year | timestamp, score, baseline — synced from watch |
| `alert_events` | 1 year | timestamp, score, delta, silence duration |
| `daily_summaries` | Forever | date, avg, peak, alert count |

Room (SQLite), local only, no cloud sync.

---

## 10. Open Questions

- [ ] Android home screen widget showing current stress score?
- [ ] "Calm reminder" — companion sends a breathing prompt to the watch?
- [ ] iOS support in future? (Samsung Accessory SDK is Android-only; would require rewrite)
