package com.stressguard.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AlertSettings(
    val threshold: Int = 70,
    val spikeDelta: Int = 20,
    val spikeWindowMin: Int = 5,
    val cooldownMin: Int = 15,
    val reAlertIfSustained: Boolean = true,
    val vibrationPattern: String = "standard",
    val baselineOverride: Int? = null,
    val nightMode: Boolean = false,
    val nightModeStart: String = "23:00",
    val nightModeEnd: String = "07:00",
)
