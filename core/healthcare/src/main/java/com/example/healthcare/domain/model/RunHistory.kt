package com.example.healthcare.domain.model

data class RunHistory(
    val id: String,
    val startTime: Long,
    val endTime: Long = 0L,
    val durationMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val averagePace: String = "00:00",
    val averageHeartRate: Int? = null,
    val averageCadence: Int? = null,
    val calories: Int = 0,
    val maxHeartRate: Int? = null,
    val maxPace: String? = null,
    val totalSteps: Int = 0
)

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val speed: Float? = null,
    val timestamp: Long = 0L,
    val orderIndex: Int = 0
)
