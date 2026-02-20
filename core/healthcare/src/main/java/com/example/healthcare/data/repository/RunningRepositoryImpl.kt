package com.example.healthcare.data.repository

import android.util.Log
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
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunningRepositoryImpl @Inject constructor(
    private val healthConnectDataSource: HealthConnectDataSource,
    private val stepCounterSensor: StepCounterSensor,
    private val gpsSensor: GpsSensor
) : RunningRepository {

    private var currentSessionId: String? = null
    private var sessionStartTime: Instant? = null
    private var totalSteps: Int = 0
    private var previousSteps: Int = 0
    private var totalDistance: Double = 0.0
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var lastCadenceUpdateTime: Long = 0L
    private var stepsAtLastCadenceUpdate: Int = 0

    companion object {
        private const val TAG = "Logd"
    }

    override suspend fun startExerciseSession(exerciseType: ExerciseType): Result<String> {
        val startTime = Instant.now()
        sessionStartTime = startTime
        Log.d(TAG, "startExerciseSession called, sessionStartTime=$sessionStartTime")

        val sessionId = "session_${System.currentTimeMillis()}"
        currentSessionId = sessionId
        totalSteps = 0
        previousSteps = 0
        totalDistance = 0.0
        lastLatitude = null
        lastLongitude = null
        lastCadenceUpdateTime = 0L
        stepsAtLastCadenceUpdate = 0

        Log.d(TAG, "Phone sensor session started: $sessionId")
        return Result.success(sessionId)
    }

    override suspend fun stopExerciseSession(sessionId: String): Result<Unit> {
        currentSessionId = null
        sessionStartTime = null
        totalSteps = 0
        previousSteps = 0
        totalDistance = 0.0
        lastLatitude = null
        lastLongitude = null
        lastCadenceUpdateTime = 0L
        stepsAtLastCadenceUpdate = 0
        stepCounterSensor.resetSession()

        Log.d(TAG, "Exercise session stopped: $sessionId")
        return Result.success(Unit)
    }

    override fun observeRunningMetrics(): Flow<RunningMetrics> =
        combine(
            stepCounterSensor.observeSteps().catch { e ->
                Log.d(TAG, "StepCounterSensor error: ${e.message}")
                emit(0)
            },
            gpsSensor.observeLocation().catch { e ->
                Log.d(TAG, "GpsSensor error: ${e.message}")
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

            if (hasValidGps) {
                if (lastLatitude != null && lastLongitude != null) {
                    val distanceIncrement = gpsSensor.calculateDistance(
                        lastLatitude!!, lastLongitude!!,
                        gpsData.latitude, gpsData.longitude
                    )
                    if (distanceIncrement < 100) {
                        totalDistance += distanceIncrement
                    }
                }
                lastLatitude = gpsData.latitude
                lastLongitude = gpsData.longitude
            } else {
                totalDistance = totalSteps * 0.75
            }

            val speed = if (hasValidGps && gpsData.speed != null && gpsData.speed > 0) {
                gpsData.speed.toDouble()
            } else if (elapsedSeconds > 0 && totalDistance > 0) {
                totalDistance / elapsedSeconds
            } else {
                0.0
            }

            val pace = if (speed > 0) {
                RunningMetrics.speedToPace(speed)
            } else null

            totalSteps = steps

            val cadence = if (totalSteps > 0 && elapsedSeconds > 0) {
                (totalSteps.toDouble() / elapsedSeconds) * 60.0
            } else null

            val heartRate: Int? = null

            RunningMetrics(
                timestamp = currentTime.toEpochMilli(),
                heartRate = heartRate,
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
        return try {
            val sessionId = currentSessionId
            val startTime = sessionStartTime

            if (sessionId != null && startTime != null) {
                val session = RunningSession(
                    sessionId = sessionId,
                    startTime = startTime.toEpochMilli(),
                    isActive = true
                )
                Result.success(session)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkPermissions(): Boolean {
        return healthConnectDataSource.hasAllPermissions()
    }

    override suspend fun requestPermissions(): Result<Unit> {
        return try {
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isHealthConnectAvailable(): Boolean {
        return healthConnectDataSource.isAvailable()
    }
}
