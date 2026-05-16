package com.stressguard.watch.app

import android.app.Application
import android.content.Context
import com.stressguard.watch.data.HealthDataSource
import com.stressguard.watch.data.SettingsRepository
import com.stressguard.watch.data.SyntheticHealthDataSource
import com.stressguard.watch.service.AlertService
import com.stressguard.watch.service.DataStoreService

class StressGuardApp : Application() {

    lateinit var settings: SettingsRepository
        private set

    lateinit var store: DataStoreService
        private set

    lateinit var alerts: AlertService
        private set

    lateinit var health: HealthDataSource
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        store = DataStoreService(this)
        alerts = AlertService(this)
        health = buildHealthDataSource(this)
    }

    companion object {
        @Volatile private var instance: StressGuardApp? = null

        fun from(context: Context): StressGuardApp =
            (context.applicationContext as? StressGuardApp)
                ?: instance
                ?: error("StressGuardApp not initialised")

        private fun buildHealthDataSource(context: Context): HealthDataSource {
            // Real Samsung Health Data SDK integration goes here once libs/*.aar are present.
            // The SDK exposes HealthDataResolver / HealthDataStore APIs that read the
            // Samsung-computed stress score; wire that to a HealthDataSource impl and
            // return it instead of the synthetic source below.
            return SyntheticHealthDataSource()
        }
    }
}
