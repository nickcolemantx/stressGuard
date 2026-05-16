package com.stressguard.watch.presentation

import androidx.compose.ui.graphics.Color

object StressColors {
    val Calm = Color(0xFF4CAF50)
    val Moderate = Color(0xFFFFC107)
    val Elevated = Color(0xFFFF9800)
    val High = Color(0xFFF44336)

    fun forScore(score: Int): Color = when {
        score < 40 -> Calm
        score < 60 -> Moderate
        score < 75 -> Elevated
        else -> High
    }

    fun labelFor(score: Int): String = when {
        score < 40 -> "CALM"
        score < 60 -> "MODERATE"
        score < 75 -> "ELEVATED"
        else -> "HIGH"
    }
}
