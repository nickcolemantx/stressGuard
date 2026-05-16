package com.stressguard.companion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.stressguard.companion.data.entities.AlertEventEntity
import com.stressguard.companion.data.entities.DailySummaryEntity
import com.stressguard.companion.data.entities.StressReadingEntity

@Database(
    entities = [
        StressReadingEntity::class,
        AlertEventEntity::class,
        DailySummaryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class StressDatabase : RoomDatabase() {

    abstract fun stressReadings(): StressReadingDao
    abstract fun alertEvents(): AlertEventDao
    abstract fun dailySummaries(): DailySummaryDao

    companion object {
        @Volatile private var instance: StressDatabase? = null

        fun get(context: Context): StressDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StressDatabase::class.java,
                "stressguard.db",
            ).build().also { instance = it }
        }
    }
}
