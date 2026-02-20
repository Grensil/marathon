package com.example.healthcare.domain.model

data class RunningMetrics(
    val timestamp: Long,
    val heartRate: Int? = null,
    val speed: Double? = null, // m/s
    val distance: Double? = null, // meters
    val cadence: Double? = null, // steps per minute
    val pace: Double? = null, // min/km
    val altitude: Double? = null, // meters
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elapsedTimeSeconds: Long = 0
) {
    companion object {
        fun speedToPace(speedInMetersPerSecond: Double): Double? {
            return if (speedInMetersPerSecond > 0) {
                (1000.0 / 60.0) / speedInMetersPerSecond
            } else null
        }
    }
}
