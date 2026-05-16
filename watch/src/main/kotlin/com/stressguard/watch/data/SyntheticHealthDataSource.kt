package com.stressguard.watch.data

import kotlin.math.roundToInt
import kotlin.random.Random

class SyntheticHealthDataSource(
    private val seed: Long = System.currentTimeMillis(),
    private val clock: () -> Long = System::currentTimeMillis,
) : HealthDataSource {

    private val rng = Random(seed)
    private var lastScore: Double = 45.0

    override suspend fun latestStressSample(): HealthDataSource.Sample {
        val drift = rng.nextDouble(-4.0, 4.0)
        val spikeChance = rng.nextDouble()
        val spike = if (spikeChance < 0.04) rng.nextDouble(15.0, 30.0) else 0.0
        lastScore = (lastScore + drift + spike).coerceIn(5.0, 98.0)
        return HealthDataSource.Sample(
            timestampMs = clock(),
            score = lastScore.roundToInt(),
            hr = (60 + rng.nextInt(0, 35) + (lastScore / 4).roundToInt()).coerceIn(50, 140),
        )
    }

    override suspend fun stressSamplesBetween(fromMs: Long, toMs: Long): List<HealthDataSource.Sample> {
        if (toMs <= fromMs) return emptyList()
        val step = 60_000L
        val out = mutableListOf<HealthDataSource.Sample>()
        var t = fromMs
        var score = 45.0
        while (t <= toMs) {
            score = (score + rng.nextDouble(-3.0, 3.0)).coerceIn(10.0, 95.0)
            out.add(
                HealthDataSource.Sample(
                    timestampMs = t,
                    score = score.roundToInt(),
                    hr = (65 + rng.nextInt(0, 25)).coerceIn(50, 130),
                ),
            )
            t += step
        }
        return out
    }

    override suspend fun thirtyDayRestingAverage(): Int = 48
}
