package com.stressguard.watch.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stressguard.shared.model.StressReading
import java.time.Instant

@Composable
fun StressChart(
    readings: List<StressReading>,
    baseline: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0 || size.height <= 0) return@Canvas

        val baselineY = size.height * (1f - baseline / 100f)
        drawLine(
            color = Color.Gray.copy(alpha = 0.6f),
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        if (readings.size < 2) return@Canvas

        val tStart = Instant.parse(readings.first().timestamp).toEpochMilli().toFloat()
        val tEnd = Instant.parse(readings.last().timestamp).toEpochMilli().toFloat()
        val tSpan = (tEnd - tStart).coerceAtLeast(1f)

        val path = Path()
        readings.forEachIndexed { index, r ->
            val t = Instant.parse(r.timestamp).toEpochMilli().toFloat()
            val x = (t - tStart) / tSpan * size.width
            val y = size.height * (1f - r.score / 100f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = StressColors.forScore(readings.last().score), style = Stroke(width = 2.5f))

        val last = readings.last()
        val lastT = Instant.parse(last.timestamp).toEpochMilli().toFloat()
        val lastX = (lastT - tStart) / tSpan * size.width
        val lastY = size.height * (1f - last.score / 100f)
        drawCircle(
            color = StressColors.forScore(last.score),
            radius = 4f,
            center = Offset(lastX, lastY),
        )
    }
}
