package com.stressguard.watch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stressguard.watch.data.entities.AlertEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {

    @Insert
    suspend fun insert(event: AlertEventEntity): Long

    @Query("SELECT * FROM alert_events WHERE timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC")
    suspend fun between(fromMs: Long, toMs: Long): List<AlertEventEntity>

    @Query("SELECT COUNT(*) FROM alert_events WHERE timestampMs >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Int

    @Query("SELECT * FROM alert_events ORDER BY timestampMs DESC LIMIT 1")
    fun latest(): Flow<AlertEventEntity?>

    @Query("DELETE FROM alert_events WHERE timestampMs < :cutoffMs")
    suspend fun pruneOlderThan(cutoffMs: Long): Int
}
