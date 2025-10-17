package com.example.history

/**
 * 러닝 화면 상태
 */
data class RunningState(
    val isRunning: Boolean = false,
    val sessionId: String? = null,
    val elapsedTime: Long = 0L, // milliseconds
    val distance: Double = 0.0, // meters
    val currentPace: String = "--:--", // min/km
    val averagePace: String = "--:--", // min/km
    val currentHeartRate: Int? = null,
    val averageHeartRate: Int? = null,
    val currentCadence: Int? = null,
    val averageCadence: Int? = null,
    val currentAltitude: Double? = null, // meters
    val calories: Int = 0,
    val hasPermissions: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
