package com.stressguard.watch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stressguard.watch.presentation.AlertOverlay
import com.stressguard.watch.presentation.DetailScreen
import com.stressguard.watch.presentation.SettingsScreen
import com.stressguard.watch.presentation.StressViewModel
import com.stressguard.watch.presentation.WatchFaceScreen
import com.stressguard.watch.service.StressMonitorService

class MainActivity : ComponentActivity() {

    private enum class Screen { WatchFace, Detail, Settings }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { startMonitor() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsThenStart()
        setContent { App() }
    }

    @Composable
    private fun App() {
        var screen by remember { mutableStateOf(Screen.WatchFace) }
        val vm: StressViewModel = viewModel()
        val overlay by vm.alertOverlay.collectAsStateWithLifecycle()

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            when (screen) {
                Screen.WatchFace -> WatchFaceScreen(
                    onTap = { screen = Screen.Detail },
                    viewModel = vm,
                )
                Screen.Detail -> DetailScreen(
                    onBack = { screen = Screen.WatchFace },
                    onSettings = { screen = Screen.Settings },
                    viewModel = vm,
                )
                Screen.Settings -> SettingsScreen(
                    onBack = { screen = Screen.WatchFace },
                    viewModel = vm,
                )
            }

            if (overlay.visible && overlay.event != null) {
                AlertOverlay(
                    event = overlay.event!!,
                    onDismiss = { vm.dismissAlert() },
                    onSilence = { vm.silence(it) },
                )
            }
        }
    }

    private fun requestPermissionsThenStart() {
        val perms = mutableListOf(
            android.Manifest.permission.BODY_SENSORS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += android.Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun startMonitor() {
        val intent = Intent(this, StressMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
