package com.stressguard.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AlertEvent(
    val timestamp: String,
    val score: Int,
    val delta: Int,
    val deltaWindowMin: Int,
    val thresholdUsed: Int,
    val silenceDurationMin: Int = 0,
)
