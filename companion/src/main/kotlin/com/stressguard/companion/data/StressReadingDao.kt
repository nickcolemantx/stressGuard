package com.stressguard.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stressguard.companion.data.entities.StressReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StressReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<StressReadingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: StressReadingEntity)

    @Query("SELECT * FROM stress_readings WHERE timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC")
    suspend fun between(fromMs: Long, toMs: Long): List<StressReadingEntity>

    @Query("SELECT * FROM stress_readings ORDER BY timestampMs DESC LIMIT 1")
    fun latest(): Flow<StressReadingEntity?>

    @Query("DELETE FROM stress_readings WHERE timestampMs < :cutoffMs")
    suspend fun pruneOlderThan(cutoffMs: Long): Int
}
