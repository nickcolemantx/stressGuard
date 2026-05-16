package com.stressguard.watch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Text
import com.stressguard.shared.model.StressReading
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun WatchFaceScreen(
    onTap: () -> Unit = {},
    viewModel: StressViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalTime.now()) }
    var recent by remember { mutableStateOf<List<StressReading>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            recent = viewModel.loadWindow(StressViewModel.Window.FOUR_HOUR)
            delay(60_000L)
        }
    }

    val score = state.current?.score
    val color = score?.let { StressColors.forScore(it) } ?: Color.Gray

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onTap)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = Color.White,
                    fontSize = 14.sp,
                )
                Text(
                    text = state.current?.hr?.let { "♥ ${it}bpm" } ?: "—",
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "STRESS",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.18f))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = score?.toString() ?: "--",
                        color = color,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = score?.let { StressColors.labelFor(it) } ?: "WAITING",
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            StressChart(
                readings = recent,
                baseline = state.baseline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp),
            )

            val now2 = System.currentTimeMillis()
            val silencedRemaining = ((state.silencedUntilMs - now2) / 60_000L).toInt()
            if (silencedRemaining > 0) {
                Text(
                    text = "🔕 SILENCED — $silencedRemaining min remaining",
                    color = Color(0xFFFFC107),
                    fontSize = 10.sp,
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

