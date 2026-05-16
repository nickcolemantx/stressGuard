package com.stressguard.companion.app

import android.app.Application
import android.content.Context
import com.stressguard.companion.data.SettingsRepository
import com.stressguard.companion.data.StressRepository
import com.stressguard.companion.service.NotificationService
import com.stressguard.companion.service.WatchSyncClient

class StressGuardCompanionApp : Application() {

    lateinit var store: StressRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    lateinit var notifier: NotificationService
        private set

    lateinit var watch: WatchSyncClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = StressRepository(this)
        settings = SettingsRepository(this)
        notifier = NotificationService(this)
        watch = WatchSyncClient(this)
        notifier.ensureChannel()
    }

    companion object {
        @Volatile private var instance: StressGuardCompanionApp? = null

        fun from(context: Context): StressGuardCompanionApp =
            (context.applicationContext as? StressGuardCompanionApp)
                ?: instance
                ?: error("StressGuardCompanionApp not initialised")
    }
}
