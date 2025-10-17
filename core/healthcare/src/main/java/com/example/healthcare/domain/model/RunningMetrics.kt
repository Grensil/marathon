package com.example.healthcare.domain.model

/**
 * 실시간 러닝 메트릭
 */
data class RunningMetrics(
    val timestamp: Long,
    val heartRate: Int? = null,
    val speed: Double? = null, // m/s
    val distance: Double? = null, // meters
    val cadence: Double? = null, // steps per minute
    val pace: Double? = null, // min/km
    val altitude: Double? = null, // meters
    val elapsedTimeSeconds: Long = 0 // 경과 시간 (초)
) {
    companion object {
        /**
         * 속도(m/s)를 페이스(min/km)로 변환
         */
        fun speedToPace(speedInMetersPerSecond: Double): Double? {
            return if (speedInMetersPerSecond > 0) {
                (1000.0 / 60.0) / speedInMetersPerSecond
            } else null
        }
    }
}
