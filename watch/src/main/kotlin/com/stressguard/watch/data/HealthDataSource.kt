package com.stressguard.watch.data

interface HealthDataSource {

    data class Sample(
        val timestampMs: Long,
        val score: Int,
        val hr: Int?,
    )

    suspend fun latestStressSample(): Sample?

    suspend fun stressSamplesBetween(fromMs: Long, toMs: Long): List<Sample>

    suspend fun thirtyDayRestingAverage(): Int?
}
