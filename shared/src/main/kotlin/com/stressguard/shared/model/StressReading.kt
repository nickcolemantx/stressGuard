package com.stressguard.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class StressReading(
    val timestamp: String,
    val score: Int,
    val baseline: Int? = null,
    val hr: Int? = null,
)
