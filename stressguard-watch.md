# StressGuard — Galaxy Watch 5 App Design Specification
### `stressguard-watch.md`

**Version:** 3.0  
**Target Device:** Samsung Galaxy Watch 5 (Wear OS 3.5 powered by Samsung)  
**Language:** Kotlin  
**IDE:** Android Studio — single project, `:watch` module  
**Data Source:** Samsung Health Data SDK (stress scores) + Samsung Health Sensor SDK (raw HR)  
**Companion:** `:companion` module, same Android Studio project

> ✅ No Samsung Developer account needed for debug deploys — ADB over Wi-Fi only

---

## 1. Overview

StressGuard monitors real-time stress and immediately alerts via vibration on a spike. Unlike Samsung Health's built-in tracking, it fires on sudden jumps — giving earlier warning to work on causes or calm down.

---

## 2. Local Development Setup

Everything lives in a **single Android Studio project** with three modules: `:shared`, `:watch`, and `:companion`. One IDE, one project, open both apps at the same time.

### 2.1 System Requirements

| Requirement | Detail |
|---|---|
| **OS** | Windows, macOS, or Linux — all supported |
| **IDE** | Android Studio Hedgehog 2023.1 or later |
| **JDK** | 17 (bundled with Android Studio, no separate install) |
| **Android SDK** | API 30 minimum (Wear OS 3), API 34 target |
| **RAM** | 8 GB minimum, 16 GB recommended |
| **Watch** | Galaxy Watch 5 on same Wi-Fi as your PC |

### 2.2 Step-by-Step Setup

#### Step 1 — Install Android Studio
Download from developer.android.com/studio. Run with defaults.
Then open SDK Manager (`Tools → SDK Manager`) and confirm installed:
```
Android SDK Platform  API 34
Android SDK Platform  API 30
Android Emulator
```

#### Step 2 — Create the Multi-Module Project
```
New Project → No Activity → Name: StressGuard
```
Add modules via `File → New → New Module`:
```
:shared    → Android Library (no activity)
:watch     → Wear OS → No Activity
:companion → Phone & Tablet → No Activity
```

In root `settings.gradle.kts`:
```kotlin
include(":shared", ":watch", ":companion")
```

#### Step 3 — Add Key Dependencies to `:watch`

In `watch/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":shared"))

    // Wear OS Compose UI
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")

    // Samsung Health SDKs (downloaded as .aar — see Step 4)
    implementation(files("../libs/samsung-health-sensor-sdk.aar"))
    implementation(files("../libs/samsung-health-data-sdk.aar"))

    // Watch ↔ Phone comms (built into Wear OS, no Samsung account needed)
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Background service + coroutines
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Local database
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
}
```

#### Step 4 — Download Samsung Health SDKs (No Account Required)
These are `.aar` files, not on Maven Central. Download free from:
```
Samsung Health Sensor SDK → developer.samsung.com/health/sensor
Samsung Health Data SDK   → developer.samsung.com/health/data
```
Place both `.aar` files in a `/libs` folder at the project root.

#### Step 5 — Enable Developer Mode on the Watch
```
Settings → About Watch → Software → tap "Software Version" 5×
→ "Developer mode turned on" toast

Settings → Developer Options → ADB Debugging: ON
Settings → Developer Options → Wireless Debugging: ON
→ note the IP address and port shown
```

#### Step 6 — Enable Health Platform Developer Mode
Required separately to let Samsung Health SDKs run on-device:
```
App drawer → open "Health Platform"
Tap Health Platform rapidly ~10 times
→ "[Dev mode]" label appears below the app name
```

#### Step 7 — Connect via ADB and Deploy
```bash
# In Android Studio terminal or any terminal:
adb connect 192.168.x.x:PORT      # IP from Step 5

adb devices
# Shows: 192.168.x.x:PORT    device   ← you're in

# Then in Android Studio:
# Device dropdown → select your watch → Run ':watch'
```
No certificates, no Samsung account, no sign-up. This is all you need for personal development and testing.

### 2.3 Project Structure
```
StressGuard/
├── libs/
│   ├── samsung-health-sensor-sdk.aar
│   └── samsung-health-data-sdk.aar
│
├── shared/                           ← :shared module
│   └── src/main/kotlin/com/stressguard/shared/
│       ├── model/
│       │   ├── StressReading.kt
│       │   ├── AlertEvent.kt
│       │   └── AlertSettings.kt
│       └── protocol/
│           └── WearMessages.kt       ← message type constants & serialisation
│
├── watch/                            ← :watch module (Wear OS)
│   └── src/main/kotlin/com/stressguard/watch/
│       ├── presentation/
│       │   ├── WatchFaceScreen.kt
│       │   ├── DetailScreen.kt
│       │   ├── AlertOverlay.kt
│       │   └── SettingsScreen.kt
│       ├── service/
│       │   ├── StressMonitorService.kt   ← background polling service
│       │   ├── AlertService.kt           ← spike detection + haptics
│       │   ├── DataStoreService.kt       ← Room DB + circular buffer
│       │   └── PhoneSyncService.kt       ← Wearable Data Layer sender
│       └── tile/
│           └── StressTile.kt             ← optional Wear OS tile
│
└── companion/                        ← :companion module (Android phone)
    └── ...                           ← see stressguard-companion.md
```

---

## 3. Health Data Access

### 3.1 Two SDKs, Two Roles

| SDK | What it provides | Used for |
|---|---|---|
| **Samsung Health Data SDK** | Processed stress scores already computed by Samsung Health (same values you see in the Samsung Health app) | Spike detection, chart, history |
| **Samsung Health Sensor SDK** | Raw PPG and HR signals from BioActive sensor | Passive HR display on watch face |

> No need to compute stress from raw signals — the Health Data SDK reads Samsung's own computed score directly.

### 3.2 Required Permissions
```xml
<!-- In watch/src/main/AndroidManifest.xml -->
<uses-permission android:name="com.samsung.android.providers.health.permission.STRESS" />
<uses-permission android:name="com.samsung.android.providers.health.permission.HEART_RATE" />
<uses-permission android:name="com.samsung.android.providers.health.permission.SLEEP" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- Haptics: no extra permission needed on Wear OS -->
```

### 3.3 Stress Polling Strategy
Samsung Health computes a stress score approximately every 5 minutes during wear. The app polls via Samsung Health Data SDK on a 1-minute timer; if a new reading is available it processes it, otherwise it uses the last known value.

```kotlin
// Pseudocode — StressMonitorService.kt
val readRequest = HealthDataResolver.ReadRequest.Builder()
    .setDataType(HealthConstants.StressManagement.HEALTH_DATA_TYPE)
    .setLocalTimeRange(startTime, endTime)
    .build()
// Delivers stress score as Int 0–100
```

---

## 4. Stress Detection Logic

### 4.1 Dual-Trigger Model
```
ALERT fires when:
  Condition A  →  Current score > THRESHOLD   (default: 70)
  AND
  Condition B  →  Score rose ≥ SPIKE_DELTA    (default: +20 pts)
                  within last SPIKE_WINDOW    (default: 5 min)
```
Both must be true simultaneously to avoid false positives.

### 4.2 Parameters (all user-adjustable in Settings)

| Parameter | Default | Range |
|---|---|---|
| `THRESHOLD` | 70 | 50–90 |
| `SPIKE_DELTA` | 20 pts | 10–40 pts |
| `SPIKE_WINDOW` | 5 min | 2–15 min |
| `COOLDOWN` | 15 min | 5–60 min |

### 4.3 Re-alert & Episode End
- Re-fires every `COOLDOWN` minutes while both conditions stay true
- Episode ends when score drops below `THRESHOLD` for 5+ consecutive minutes; cooldown resets

### 4.4 Baseline
- Seeded from Samsung Health 30-day resting average on first launch
- Recalculated nightly at 3 AM (calm readings = score < 50, non-exercise periods)
- Shown as dashed line on all charts; manual override available in Settings

---

## 5. Screen Architecture
```
Watch Face  ──tap──►  Detail View  ──swipe up──►  Settings
    │
    └── spike fires ──►  Alert Overlay  (over any screen)
                              ├── Dismiss
                              └── Silence (5 / 10 / 60 min)
```
All screens: **Jetpack Compose for Wear OS** (`androidx.wear.compose`).

---

## 6. Watch Face — Main Screen
```
┌────────────────────────────────────────────┐
│  11:42                        ❤️ 72bpm     │
│ ─────────────────────────────────────────  │
│              STRESS                        │
│           ╔═══════╗                        │
│           ║  74   ║                        │
│           ╚═══════╝                        │
│           🔴  HIGH                         │
│                                            │
│  ┌──────────────────────────────────────┐  │
│  │  100 ┤         ╭─╮                   │  │
│  │   75 ┤─────────╯  ╲╱──────●          │  │
│  │   50 ┤· · · · · · · · · · ·Baseline  │  │
│  │   25 ┤                              │  │
│  │    0 └──────────────────────────────│  │
│  │      4h ago                    now  │  │
│  └──────────────────────────────────────┘  │
│    🔕 SILENCED — 9 min remaining           │
└────────────────────────────────────────────┘
```

### Colour States

| Range | Colour | Hex | Label |
|---|---|---|---|
| 0–39 | Green | `#4CAF50` | CALM |
| 40–59 | Amber | `#FFC107` | MODERATE |
| 60–74 | Orange | `#FF9800` | ELEVATED |
| 75–100 | Red | `#F44336` | HIGH |

---

## 7. Alert Overlay
```
┌────────────────────────────────────────────┐
│            ⚡ STRESS SPIKE                  │
│               Score: 74                   │
│          ↑ +22 pts in 4 min               │
│   ┌───────────────┐  ┌──────────────────┐  │
│   │    DISMISS    │  │     SILENCE      │  │
│   └───────────────┘  └──────────────────┘  │
│   ┌──────────┐  ┌──────────┐  ┌────────┐  │
│   │  5 min   │  │  10 min  │  │ 1 hour │  │
│   └──────────┘  └──────────┘  └────────┘  │
└────────────────────────────────────────────┘
```
Auto-dismisses after 30s if no action (treated as Dismiss). Silence suppresses vibration only — stress still visible on screen.

### Haptics
```kotlin
// AlertService.kt
val pattern     = longArrayOf(0, 200, 100, 200, 100, 400)  // short·short·long
val amplitudes  = intArrayOf( 0, 200,   0, 200,   0, 255)
vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
// Repeated 3× with 500ms between repetitions (~4 seconds total)
```

---

## 8. Detail View

Tap watch face to enter.
```
┌────────────────────────────────────────────┐
│  ←             STRESS DETAIL               │
│  Current: 74  🔴 HIGH                      │
│  Baseline: 48  (30-day avg)                │
│  Peak today: 81  at 2:14 PM               │
│  Avg today: 61   Alerts: 3                 │
│                                            │
│  ┌──────────────────────────────────────┐  │
│  │   [ 4H ][ 1D ][ 1W ][ 1M ][ 1Y ]    │  │
│  │         [Scrollable chart]           │  │
│  └──────────────────────────────────────┘  │
│  Last alert: 11:38 AM  (+22 pts spike)    │
└────────────────────────────────────────────┘
```

---

## 9. Scrollable Stress Chart

### Time Windows
```
[ 4H ]  [ 1D ]  [ 1W ]  [ 1M ]  [ 1Y ]
```

| Window | Resolution | X-axis |
|---|---|---|
| 4H | 1 reading/min | HH:MM every 30 min |
| 1D | 1 reading/min | HH:MM every 2 hrs |
| 1W | 5-min averages | Day name |
| 1M | 30-min averages | Date |
| 1Y | Daily averages | Month |

### Interactions
- **Swipe left/right** — pan through history
- Anchors at **now** when first opened; **LIVE** badge shows when at present
- **"↩ Return to live"** button appears when panned into past
- **Tap any point** — tooltip showing score, time, delta, whether alert fired

### Chart Elements
- Smooth stress line, colour-coded by range (green→red)
- Dashed baseline reference line
- ⚡ alert markers pinned to the line at each spike
- Filled circle at current reading (live windows only)

---

## 10. Settings Screen

### Alert Thresholds
```
Score threshold     ────●──────────  [70]      50–90
Spike delta         ──────●────────  [20 pts]  10–40
Spike window        ────●──────────  [5 min]   2/5/10/15 min
```

### Alerts
```
Cooldown            ──●────────────  [15 min]  5/15/30/60 min
Re-alert sustained                   [ON ●]
Vibration pattern                    [Standard ▾]  Standard/Strong/Single/Off
Alert sound                          [Off ▾]
```

### Baseline
```
Current baseline    48  (auto, Apr 29)
Manual override     [OFF]
Recalculate now     [Run now]
```

### Data & Sync
```
Sync to companion   [ON ●]
Last synced         Today 11:41 AM
Sync window         [1 Month ▾]
```

---

## 11. Local Data Storage

| Table | Retention | Detail |
|---|---|---|
| `stress_readings` | Rolling 365 days | 1-min readings |
| `alert_events` | Rolling 365 days | timestamp, score, delta, silence duration |
| `daily_summaries` | Forever | date, avg, peak, alert count |

Nightly Room job aggregates raw readings and prunes rows older than 365 days.

---

## 12. Watch ↔ Phone Communication

Uses **Wearable Data Layer API** (`play-services-wearable`) — built into Wear OS, no Samsung account or separate SDK required.

Both watch and companion register a `WearableListenerService`. See `stressguard-protocol.md` for full message schema.

---

## 13. Open Questions

- [ ] Night mode: suppress vibration during detected sleep?
- [ ] Wrist-off detection: pause monitoring when watch is removed?
- [ ] Wear OS tile: show current score in the tile carousel?
- [ ] Polling battery impact: 1-min acceptable, or add 2-min option?
- [ ] Galaxy Store distribution eventually, or sideload-only?
