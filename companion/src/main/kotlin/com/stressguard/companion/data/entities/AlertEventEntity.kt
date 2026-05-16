package com.stressguard.companion.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val score: Int,
    val delta: Int,
    val deltaWindowMin: Int,
    val thresholdUsed: Int,
    val silenceDurationMin: Int,
)
