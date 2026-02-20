package com.example.history

data class RunningRecord(
    val sessionId: String,
    val timestamp: Long,
    val elapsedTime: Long, // milliseconds
    val distance: Double, // meters
    val averagePace: String, // min/km
    val averageCadence: Int?, // spm
    val averageHeartRate: Int? = null,
    val calories: Int = 0
)

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val timestamp: Long = 0L
)

data class RunningState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
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
    val error: String? = null,
    val showCompletionDialog: Boolean = false,
    val completedRecord: RunningRecord? = null,
    val records: List<RunningRecord> = emptyList(),
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val routePoints: List<LocationPoint> = emptyList()
)
