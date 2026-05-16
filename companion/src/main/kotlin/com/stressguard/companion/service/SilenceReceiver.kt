package com.stressguard.companion.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.stressguard.companion.app.StressGuardCompanionApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SilenceReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SILENCE) return
        val duration = intent.getIntExtra(EXTRA_DURATION_MIN, NotificationService.DEFAULT_SILENCE_MIN)
        val pending = goAsync()
        scope.launch {
            try {
                StressGuardCompanionApp.from(context).watch.sendSilenceCommand(duration)
                NotificationManagerCompat.from(context).cancel(NotificationService.NOTIF_ID)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SILENCE = "com.stressguard.companion.action.SILENCE"
        const val EXTRA_DURATION_MIN = "duration_min"
    }
}
