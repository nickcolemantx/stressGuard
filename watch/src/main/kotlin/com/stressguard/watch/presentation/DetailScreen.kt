package com.stressguard.watch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DetailScreen(
    onBack: () -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: StressViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var window by remember { mutableStateOf(StressViewModel.Window.FOUR_HOUR) }
    var readings by remember { mutableStateOf<List<StressReading>>(emptyList()) }
    var snapshot by remember { mutableStateOf<StressViewModel.DaySnapshot?>(null) }

    LaunchedEffect(window) { readings = viewModel.loadWindow(window) }
    LaunchedEffect(Unit) { snapshot = viewModel.loadDaySnapshot() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onBack))
                Text(
                    text = "  STRESS DETAIL",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("⚙", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onSettings))
        }

        val current = state.current
        if (current != null) {
            val color = StressColors.forScore(current.score)
            Text(
                text = "Current: ${current.score}  ${StressColors.labelFor(current.score)}",
                color = color,
                fontSize = 11.sp,
            )
        }
        Text("Baseline: ${state.baseline}", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
        snapshot?.let { snap ->
            snap.peak?.let {
                Text(
                    text = "Peak today: ${it.score} at ${it.localTime()}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                )
            }
            Text(
                text = "Avg today: ${snap.avg ?: '-'}   Alerts: ${snap.alertCount}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
            )
            snap.lastAlert?.let {
                Text(
                    text = "Last alert: ${Instant.parse(it.timestamp).atZone(ZoneId.systemDefault()).toLocalTime().truncated()} (+${it.delta} pts)",
                    color = Color(0xFFFFC107),
                    fontSize = 10.sp,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        ) {
            StressViewModel.Window.values().forEach { w ->
                WindowChip(label = w.xLabel, selected = w == window) { window = w }
            }
        }

        StressChart(
            readings = readings,
            baseline = state.baseline,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun WindowChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color.White else Color.White.copy(alpha = 0.15f)
    val fg = if (selected) Color.Black else Color.White
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) { Text(label, color = fg, fontSize = 9.sp) }
}

private fun StressReading.localTime(): String =
    Instant.parse(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun java.time.LocalTime.truncated(): String = format(DateTimeFormatter.ofPattern("HH:mm"))
