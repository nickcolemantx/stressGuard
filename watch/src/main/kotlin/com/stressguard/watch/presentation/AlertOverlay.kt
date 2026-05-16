package com.stressguard.watch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.stressguard.shared.model.AlertEvent
import kotlinx.coroutines.delay

@Composable
fun AlertOverlay(
    event: AlertEvent,
    onDismiss: () -> Unit,
    onSilence: (Int) -> Unit,
) {
    LaunchedEffect(event.timestamp) {
        delay(30_000L)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            Text(
                text = "⚡ STRESS SPIKE",
                color = Color(0xFFF44336),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Score: ${event.score}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "↑ +${event.delta} pts in ${event.deltaWindowMin} min",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) { Text("Dismiss", fontSize = 10.sp) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                SilenceChip(label = "5m") { onSilence(5) }
                SilenceChip(label = "10m") { onSilence(10) }
                SilenceChip(label = "1h") { onSilence(60) }
            }
        }
    }
}

@Composable
private fun SilenceChip(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(),
    ) { Text(label, fontSize = 10.sp) }
}
