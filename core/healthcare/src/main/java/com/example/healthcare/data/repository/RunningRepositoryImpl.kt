package com.example.healthcare.data.repository

import android.util.Log
import com.example.healthcare.BuildConfig
import com.example.healthcare.data.datasource.HealthConnectDataSource
import com.example.healthcare.data.sensor.GpsSensor
import com.example.healthcare.data.sensor.StepCounterSensor
import com.example.healthcare.domain.model.ExerciseType
import com.example.healthcare.domain.model.RunningMetrics
import com.example.healthcare.domain.model.RunningSession
import com.example.healthcare.domain.repository.RunningRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunningRepositoryImpl @Inject constructor(
    private val healthConnectDataSource: HealthConnectDataSource,
    private val stepCounterSensor: StepCounterSensor,
    private val gpsSensor: GpsSensor
) : RunningRepository {

    private val mutex = Mutex()

    @Volatile private var currentSessionId: String? = null
    @Volatile private var sessionStartTime: Instant? = null
    @Volatile private var totalSteps: Int = 0
    @Volatile private var totalDistance: Double = 0.0
    @Volatile private var lastLatitude: Double? = null
    @Volatile private var lastLongitude: Double? = null
    @Volatile private var hasEverReceivedGps: Boolean = false

    companion object {
        private const val TAG = "Logd"
    }

    override suspend fun startExerciseSession(exerciseType: ExerciseType): Result<String> {
        return mutex.withLock {
            val startTime = Instant.now()
            sessionStartTime = startTime

            val sessionId = "session_${System.currentTimeMillis()}"
            currentSessionId = sessionId
            totalSteps = 0
            totalDistance = 0.0
            lastLatitude = null
            lastLongitude = null
            hasEverReceivedGps = false

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Session started: $sessionId")
            }
            Result.success(sessionId)
        }
    }

    override suspend fun stopExerciseSession(sessionId: String): Result<Unit> {
        return mutex.withLock {
            currentSessionId = null
            sessionStartTime = null
            totalSteps = 0
            totalDistance = 0.0
            lastLatitude = null
            lastLongitude = null
            hasEverReceivedGps = false
            stepCounterSensor.resetSession()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Session stopped: $sessionId")
            }
            Result.success(Unit)
        }
    }

    override fun observeRunningMetrics(): Flow<RunningMetrics> =
        combine(
            stepCounterSensor.observeSteps().catch { e ->
                if (BuildConfig.DEBUG) Log.d(TAG, "StepCounterSensor error: ${e.message}")
                emit(0)
            },
            gpsSensor.observeLocation().catch { e ->
                if (BuildConfig.DEBUG) Log.d(TAG, "GpsSensor error: ${e.message}")
                emit(com.example.healthcare.data.sensor.GpsData(0.0, 0.0, null, null, 0f, 0L))
            }
        ) { steps, gpsData ->
            if (currentSessionId == null) {
                return@combine null
            }

            val startTime = sessionStartTime ?: Instant.now()
            val currentTime = Instant.now()
            val elapsedSeconds = java.time.Duration.between(startTime, currentTime).seconds

            val hasValidGps = gpsData.latitude != 0.0 && gpsData.longitude != 0.0

            // Update steps BEFORE distance calculation
            totalSteps = steps

            if (hasValidGps) {
                hasEverReceivedGps = true
                val prevLat = lastLatitude
                val prevLon = lastLongitude
                if (prevLat != null && prevLon != null) {
                    val distanceIncrement = gpsSensor.calculateDistance(
                        prevLat, prevLon,
                        gpsData.latitude, gpsData.longitude
                    )
                    // Filter GPS noise (drift < 3m) and spikes (> 100m)
                    if (distanceIncrement in 3f..100f) {
                        totalDistance += distanceIncrement
                    }
                }
                lastLatitude = gpsData.latitude
                lastLongitude = gpsData.longitude
            } else if (!hasEverReceivedGps) {
                // Only use step-based distance if GPS was NEVER available
                totalDistance = totalSteps * 0.75
            }

            // Minimum speed threshold: 0.5 m/s (~1.8 km/h) filters GPS noise at standstill
            val MIN_SPEED_THRESHOLD = 0.5

            val speed = if (hasValidGps && gpsData.speed != null && gpsData.speed > MIN_SPEED_THRESHOLD) {
                gpsData.speed.toDouble()
            } else if (elapsedSeconds > 0 && totalDistance > 0) {
                totalDistance / elapsedSeconds
            } else {
                0.0
            }

            val pace = if (speed > 0) {
                RunningMetrics.speedToPace(speed)
            } else null

            val cadence = if (totalSteps > 0 && elapsedSeconds > 0) {
                (totalSteps.toDouble() / elapsedSeconds) * 60.0
            } else null

            RunningMetrics(
                timestamp = currentTime.toEpochMilli(),
                heartRate = null,
                speed = speed,
                distance = totalDistance,
                cadence = cadence,
                pace = pace,
                altitude = gpsData.altitude,
                latitude = if (hasValidGps) gpsData.latitude else null,
                longitude = if (hasValidGps) gpsData.longitude else null,
                elapsedTimeSeconds = elapsedSeconds
            )
        }.mapNotNull { it }

    override suspend fun getActiveSession(): Result<RunningSession?> {
        val sessionId = currentSessionId
        val startTime = sessionStartTime

        return if (sessionId != null && startTime != null) {
            Result.success(
                RunningSession(
                    sessionId = sessionId,
                    startTime = startTime.toEpochMilli(),
                    isActive = true
                )
            )
        } else {
            Result.success(null)
        }
    }

    override suspend fun checkPermissions(): Boolean {
        return healthConnectDataSource.hasAllPermissions()
    }

    override suspend fun requestPermissions(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun isHealthConnectAvailable(): Boolean {
        return healthConnectDataSource.isAvailable()
    }
}
