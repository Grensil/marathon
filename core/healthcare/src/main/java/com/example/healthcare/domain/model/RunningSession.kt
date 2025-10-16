package com.example.healthcare.domain.model

/**
 * 실시간 러닝 세션 데이터
 */
data class RunningSession(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val isActive: Boolean = true,
    val totalDistance: Double = 0.0, // meters
    val totalDuration: Long = 0L, // milliseconds
    val averageHeartRate: Int? = null,
    val currentHeartRate: Int? = null,
    val averagePace: Double? = null, // min/km
    val currentPace: Double? = null, // min/km
    val averageCadence: Double? = null, // steps per minute
    val currentCadence: Double? = null, // steps per minute
    val calories: Double = 0.0
)
