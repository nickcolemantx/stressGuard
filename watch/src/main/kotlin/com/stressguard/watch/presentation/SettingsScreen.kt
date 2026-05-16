package com.stressguard.watch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: StressViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(state.settings) }
    LaunchedEffect(state.settings) { draft = state.settings }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Header("Alert Thresholds", onBack)

        Stepper(
            label = "Threshold",
            value = draft.threshold,
            range = 50..90,
            step = 5,
            onChange = {
                val updated = draft.copy(threshold = it)
                draft = updated
                viewModel.updateSettings(updated)
            },
        )
        Stepper(
            label = "Spike Δ pts",
            value = draft.spikeDelta,
            range = 10..40,
            step = 5,
            onChange = {
                val updated = draft.copy(spikeDelta = it)
                draft = updated
                viewModel.updateSettings(updated)
            },
        )
        Stepper(
            label = "Spike window m",
            value = draft.spikeWindowMin,
            range = 2..15,
            step = 1,
            onChange = {
                val updated = draft.copy(spikeWindowMin = it)
                draft = updated
                viewModel.updateSettings(updated)
            },
        )

        Header("Alerts", null)
        Stepper(
            label = "Cooldown m",
            value = draft.cooldownMin,
            range = 5..60,
            step = 5,
            onChange = {
                val updated = draft.copy(cooldownMin = it)
                draft = updated
                viewModel.updateSettings(updated)
            },
        )
        Toggle(
            label = "Re-alert sustained",
            value = draft.reAlertIfSustained,
            onChange = {
                val updated = draft.copy(reAlertIfSustained = it)
                draft = updated
                viewModel.updateSettings(updated)
            },
        )
        SegmentedRow(
            label = "Vibration",
            options = listOf("standard", "strong", "single", "off"),
            selected = draft.vibrationPattern,
            onSelect = { picked ->
                val updated = draft.copy(vibrationPattern = picked)
                draft = updated
                viewModel.updateSettings(updated)
                viewModel.testHaptics(picked)
            },
        )

        Header("Baseline", null)
        Text("Current: ${state.baseline}", color = Color.White, fontSize = 11.sp)
        ActionRow(label = "Recalculate now") { viewModel.recalculateBaseline() }
        Stepper(
            label = "Manual override",
            value = draft.baselineOverride ?: state.baseline,
            range = 30..70,
            step = 1,
            onChange = {
                val updated = draft.copy(baselineOverride = it)
                draft = updated
                viewModel.updateSettings(updated)
                viewModel.setBaseline(it)
            },
        )

        Header("Data & Sync", null)
        Toggle(
            label = "Sync to phone",
            value = state.syncEnabled,
            onChange = { viewModel.setSyncEnabled(it) },
        )

        Header("Night Mode", null)
        Toggle(
            label = "Suppress alerts",
            value = draft.nightMode,
            onChange = {
                val updated = draft.copy(nightMode = it)
                draft = updated
                viewModel.updateSettings(updated)
            },
        )

        if (state.silencedUntilMs > System.currentTimeMillis()) {
            ActionRow(label = "Clear silence") { viewModel.clearSilence() }
        }
    }
}

@Composable
private fun Header(text: String, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        if (onBack != null) {
            Text(
                "← ",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onBack),
            )
        }
        Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Chip("−") {
            val next = (value - step).coerceAtLeast(range.first)
            if (next != value) onChange(next)
        }
        Box(modifier = Modifier.padding(horizontal = 6.dp)) {
            Text("$value", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Chip("+") {
            val next = (value + step).coerceAtMost(range.last)
            if (next != value) onChange(next)
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Chip(if (value) "ON" else "OFF") { onChange(!value) }
    }
}

@Composable
private fun SegmentedRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(label, color = Color.White, fontSize = 10.sp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { opt ->
                val sel = opt == selected
                val bg = if (sel) Color.White else Color.White.copy(alpha = 0.15f)
                val fg = if (sel) Color.Black else Color.White
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) { Text(opt, color = fg, fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Chip("Run") { onClick() }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(label, color = Color.White, fontSize = 11.sp) }
}
