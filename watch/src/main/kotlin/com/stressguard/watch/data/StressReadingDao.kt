package com.stressguard.watch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stressguard.watch.data.entities.StressReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StressReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: StressReadingEntity)

    @Query("SELECT * FROM stress_readings WHERE timestampMs >= :sinceMs ORDER BY timestampMs ASC")
    suspend fun readingsSince(sinceMs: Long): List<StressReadingEntity>

    @Query("SELECT * FROM stress_readings WHERE timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC")
    suspend fun readingsBetween(fromMs: Long, toMs: Long): List<StressReadingEntity>

    @Query("SELECT * FROM stress_readings ORDER BY timestampMs DESC LIMIT 1")
    fun latest(): Flow<StressReadingEntity?>

    @Query("SELECT * FROM stress_readings ORDER BY timestampMs DESC LIMIT :n")
    suspend fun lastN(n: Int): List<StressReadingEntity>

    @Query("DELETE FROM stress_readings WHERE timestampMs < :cutoffMs")
    suspend fun pruneOlderThan(cutoffMs: Long): Int
}
