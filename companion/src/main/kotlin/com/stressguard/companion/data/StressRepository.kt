package com.stressguard.companion.data

import android.content.Context
import com.stressguard.companion.data.entities.AlertEventEntity
import com.stressguard.companion.data.entities.DailySummaryEntity
import com.stressguard.companion.data.entities.StressReadingEntity
import com.stressguard.shared.model.AlertEvent
import com.stressguard.shared.model.StressReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StressRepository(context: Context) {

    private val db = StressDatabase.get(context)
    private val readings = db.stressReadings()
    private val alerts = db.alertEvents()
    private val summaries = db.dailySummaries()

    val latestReading: Flow<StressReading?> = readings.latest().map { it?.toShared() }

    val recentAlerts: Flow<List<AlertEvent>> = alerts.recent(20).map { list -> list.map { it.toShared() } }

    suspend fun saveReading(reading: StressReading) {
        readings.insert(reading.toEntity())
    }

    suspend fun saveReadings(readingList: List<StressReading>) {
        readings.insertAll(readingList.map { it.toEntity() })
    }

    suspend fun saveAlert(event: AlertEvent) {
        alerts.insert(event.toEntity())
    }

    suspend fun readingsBetween(fromMs: Long, toMs: Long): List<StressReading> =
        readings.between(fromMs, toMs).map { it.toShared() }

    suspend fun alertsBetween(fromMs: Long, toMs: Long): List<AlertEvent> =
        alerts.between(fromMs, toMs).map { it.toShared() }

    suspend fun alertCountSince(sinceMs: Long): Int = alerts.countSince(sinceMs)

    suspend fun rebuildDailySummary(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val day = readings.between(start, end)
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

    private fun StressReading.toEntity() = StressReadingEntity(
        timestampMs = Instant.parse(timestamp).toEpochMilli(),
        score = score,
        baseline = baseline,
        hr = hr,
    )

    private fun AlertEvent.toEntity() = AlertEventEntity(
        timestampMs = Instant.parse(timestamp).toEpochMilli(),
        score = score,
        delta = delta,
        deltaWindowMin = deltaWindowMin,
        thresholdUsed = thresholdUsed,
        silenceDurationMin = silenceDurationMin,
    )

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
