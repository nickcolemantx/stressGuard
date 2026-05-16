package com.stressguard.companion.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stress_readings")
data class StressReadingEntity(
    @PrimaryKey val timestampMs: Long,
    val score: Int,
    val baseline: Int?,
    val hr: Int?,
)
