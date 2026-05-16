package com.stressguard.shared.protocol

object Paths {
    const val STRESS_READING = "/stress/reading"
    const val ALERT_FIRED = "/stress/alert"
    const val SILENCE_COMMAND = "/stress/silence"
    const val SETTINGS_PUSH = "/settings/push"
    const val SETTINGS_REQUEST = "/settings/request"
    const val SETTINGS_RESPONSE = "/settings/response"
    const val HISTORY_REQUEST = "/history/request"
    const val HISTORY_CHUNK = "/history/chunk"
    const val ERROR = "/error"
}

object MessageTypes {
    const val STRESS_READING = "STRESS_READING"
    const val ALERT_FIRED = "ALERT_FIRED"
    const val SILENCE_COMMAND = "SILENCE_COMMAND"
    const val SETTINGS_PUSH = "SETTINGS_PUSH"
    const val SETTINGS_REQUEST = "SETTINGS_REQUEST"
    const val SETTINGS_RESPONSE = "SETTINGS_RESPONSE"
    const val HISTORY_REQUEST = "HISTORY_REQUEST"
    const val HISTORY_CHUNK = "HISTORY_CHUNK"
    const val ERROR = "ERROR"
}

object ErrorCodes {
    const val HEALTH_PERMISSION_DENIED = "HEALTH_PERMISSION_DENIED"
    const val WEARABLE_DISCONNECTED = "WEARABLE_DISCONNECTED"
    const val INVALID_SETTINGS_VALUE = "INVALID_SETTINGS_VALUE"
    const val HISTORY_UNAVAILABLE = "HISTORY_UNAVAILABLE"
}
