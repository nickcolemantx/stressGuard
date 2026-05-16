package com.stressguard.watch.service

import android.content.Context
import com.stressguard.shared.model.AlertEvent
import com.stressguard.shared.model.StressReading
import com.stressguard.watch.data.StressDatabase
import com.stressguard.watch.data.entities.AlertEventEntity
import com.stressguard.watch.data.entities.DailySummaryEntity
import com.stressguard.watch.data.entities.StressReadingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DataStoreService(context: Context) {

    private val db = StressDatabase.get(context)
    private val readings = db.stressReadings()
    private val alerts = db.alertEvents()
    private val summaries = db.dailySummaries()

    val latestReading: Flow<StressReading?> = readings.latest().map { it?.toShared() }

    val latestAlert: Flow<AlertEvent?> = alerts.latest().map { it?.toShared() }

    suspend fun saveReading(reading: StressReading) {
        readings.insert(
            StressReadingEntity(
                timestampMs = Instant.parse(reading.timestamp).toEpochMilli(),
                score = reading.score,
                baseline = reading.baseline,
                hr = reading.hr,
            ),
        )
    }

    suspend fun saveAlert(event: AlertEvent) {
        alerts.insert(
            AlertEventEntity(
                timestampMs = Instant.parse(event.timestamp).toEpochMilli(),
                score = event.score,
                delta = event.delta,
                deltaWindowMin = event.deltaWindowMin,
                thresholdUsed = event.thresholdUsed,
                silenceDurationMin = event.silenceDurationMin,
            ),
        )
    }

    suspend fun readingsBetween(fromMs: Long, toMs: Long): List<StressReading> =
        readings.readingsBetween(fromMs, toMs).map { it.toShared() }

    suspend fun lastNReadings(n: Int): List<StressReading> =
        readings.lastN(n).map { it.toShared() }

    suspend fun alertsBetween(fromMs: Long, toMs: Long): List<AlertEvent> =
        alerts.between(fromMs, toMs).map { it.toShared() }

    suspend fun alertCountSince(sinceMs: Long): Int = alerts.countSince(sinceMs)

    suspend fun rebuildSummaryFor(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val day = readings.readingsBetween(start, end)
        if (day.isEmpty()) return
        val peak = day.maxBy { it.score }
        summaries.upsert(
            DailySummaryEntity(
                date = date.toString(),
                avgScore = day.map { it.score }.average().toInt(),
                peakScore = peak.score,
                peakAtMs = peak.timestampMs,
                alertCount = alerts.between(start, end).size,
            ),
        )
    }

    suspend fun pruneOlderThan(cutoffMs: Long) {
        readings.pruneOlderThan(cutoffMs)
        alerts.pruneOlderThan(cutoffMs)
    }

    private fun StressReadingEntity.toShared() = StressReading(
        timestamp = Instant.ofEpochMilli(timestampMs).toString(),
        score = score,
        baseline = baseline,
        hr = hr,
    )

    private fun AlertEventEntity.toShared() = AlertEvent(
        timestamp = Instant.ofEpochMilli(timestampMs).toString(),
        score = score,
        delta = delta,
        deltaWindowMin = deltaWindowMin,
        thresholdUsed = thresholdUsed,
        silenceDurationMin = silenceDurationMin,
    )
}
