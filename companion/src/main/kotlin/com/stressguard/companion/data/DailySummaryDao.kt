package com.stressguard.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stressguard.companion.data.entities.DailySummaryEntity

@Dao
interface DailySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summaries WHERE date = :date")
    suspend fun forDate(date: String): DailySummaryEntity?

    @Query("SELECT * FROM daily_summaries ORDER BY date DESC LIMIT :n")
    suspend fun recent(n: Int): List<DailySummaryEntity>
}
