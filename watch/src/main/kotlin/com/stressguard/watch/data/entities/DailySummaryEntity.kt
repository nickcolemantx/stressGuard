package com.stressguard.watch.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey val date: String,
    val avgScore: Int,
    val peakScore: Int,
    val peakAtMs: Long,
    val alertCount: Int,
)
