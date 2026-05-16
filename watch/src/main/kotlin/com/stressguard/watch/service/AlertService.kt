package com.stressguard.watch.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.stressguard.shared.model.AlertEvent
import com.stressguard.shared.model.AlertSettings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class AlertService(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class Sample(val timestampMs: Long, val score: Int)

    private val window = ArrayDeque<Sample>()
    private var lastAlertAtMs: Long = 0L
    private var belowThresholdSinceMs: Long? = null

    fun onReading(
        sample: Sample,
        settings: AlertSettings,
        silencedUntilMs: Long,
    ): AlertEvent? {
        val windowMs = settings.spikeWindowMin.toLong() * 60_000L
        val cutoff = sample.timestampMs - windowMs
        while (window.isNotEmpty() && window.first().timestampMs < cutoff) window.removeFirst()
        window.addLast(sample)

        if (sample.score < settings.threshold) {
            val firstBelow = belowThresholdSinceMs
            if (firstBelow == null) {
                belowThresholdSinceMs = sample.timestampMs
            } else if (sample.timestampMs - firstBelow >= 5L * 60_000L) {
                lastAlertAtMs = 0L
                belowThresholdSinceMs = sample.timestampMs
            }
            return null
        }
        belowThresholdSinceMs = null

        val minInWindow = window.minOf { it.score }
        val delta = sample.score - minInWindow
        val deltaWindowReached = delta >= settings.spikeDelta

        val firstFire = lastAlertAtMs == 0L
        val cooldownMs = settings.cooldownMin.toLong() * 60_000L
        val cooldownExpired = sample.timestampMs - lastAlertAtMs >= cooldownMs

        val shouldFire = when {
            !deltaWindowReached -> false
            firstFire -> true
            settings.reAlertIfSustained && cooldownExpired -> true
            else -> false
        }
        if (!shouldFire) return null

        if (settings.nightMode && isWithinNightWindow(sample.timestampMs, settings)) return null

        lastAlertAtMs = sample.timestampMs
        val silenced = sample.timestampMs < silencedUntilMs
        if (!silenced) vibrate(settings.vibrationPattern)

        val deltaWindowMin = ((sample.timestampMs - window.first().timestampMs) / 60_000L).toInt().coerceAtLeast(1)
        val silenceRemainingMin = if (silenced) ((silencedUntilMs - sample.timestampMs) / 60_000L).toInt() else 0

        return AlertEvent(
            timestamp = Instant.ofEpochMilli(sample.timestampMs).toString(),
            score = sample.score,
            delta = delta,
            deltaWindowMin = deltaWindowMin,
            thresholdUsed = settings.threshold,
            silenceDurationMin = silenceRemainingMin,
        )
    }

    fun reset() {
        window.clear()
        lastAlertAtMs = 0L
        belowThresholdSinceMs = null
    }

    fun manualVibrate(pattern: String) = vibrate(pattern)

    private fun vibrate(pattern: String) {
        val vibrator = systemVibrator() ?: return
        val (timings, amplitudes) = patternFor(pattern) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    private fun patternFor(pattern: String): Pair<LongArray, IntArray>? {
        val gap = 500L
        val gapAmp = 0
        return when (pattern.lowercase()) {
            "off" -> null
            "single" -> longArrayOf(0, 400) to intArrayOf(0, 255)
            "strong" -> repeatPattern(
                base = longArrayOf(0, 300, 100, 300, 100, 600),
                amps = intArrayOf(0, 255, 0, 255, 0, 255),
                reps = 3,
                gapMs = gap,
                gapAmp = gapAmp,
            )
            else -> repeatPattern(
                base = longArrayOf(0, 200, 100, 200, 100, 400),
                amps = intArrayOf(0, 200, 0, 200, 0, 255),
                reps = 3,
                gapMs = gap,
                gapAmp = gapAmp,
            )
        }
    }

    private fun repeatPattern(
        base: LongArray,
        amps: IntArray,
        reps: Int,
        gapMs: Long,
        gapAmp: Int,
    ): Pair<LongArray, IntArray> {
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        repeat(reps) { i ->
            timings.addAll(base.toList())
            amplitudes.addAll(amps.toList())
            if (i != reps - 1) {
                timings.add(gapMs)
                amplitudes.add(gapAmp)
            }
        }
        return timings.toLongArray() to amplitudes.toIntArray()
    }

    private fun systemVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun isWithinNightWindow(nowMs: Long, settings: AlertSettings): Boolean {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime()
        val start = parseTime(settings.nightModeStart) ?: return false
        val end = parseTime(settings.nightModeEnd) ?: return false
        return if (start <= end) now in start..end else now >= start || now <= end
    }

    private fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
}
