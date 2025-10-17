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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * 러닝 리포지토리 구현
 */
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
    private var totalDistance: Double = 0.0 // GPS 기반 누적 거리 (미터)
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    companion object {
        private const val TAG = "RunningRepositoryImpl"
    }

    override suspend fun startExerciseSession(exerciseType: ExerciseType): Result<String> {
        val startTime = Instant.now()
        sessionStartTime = startTime
        Log.d(TAG, "startExerciseSession called, sessionStartTime=$sessionStartTime")

        // 세션 초기화
        val sessionId = "session_${System.currentTimeMillis()}"
        currentSessionId = sessionId
        totalSteps = 0
        previousSteps = 0
        totalDistance = 0.0
        lastLatitude = null
        lastLongitude = null

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
        stepCounterSensor.resetSession()

        Log.d(TAG, "Exercise session stopped: $sessionId")
        return Result.success(Unit)
    }

    override fun observeRunningMetrics(): Flow<RunningMetrics> =
        combine(
            stepCounterSensor.observeSteps(),
            gpsSensor.observeLocation()
        ) { steps, gpsData ->
            Log.d(TAG, "Combining: steps=$steps, gps=(${gpsData.latitude}, ${gpsData.longitude})")

            if (currentSessionId == null) {
                Log.w(TAG, "currentSessionId is null, skipping metrics emission")
                return@combine null
            }

            // 경과 시간 계산
            val startTime = sessionStartTime ?: Instant.now()
            val currentTime = Instant.now()
            val elapsedSeconds = java.time.Duration.between(startTime, currentTime).seconds
            Log.d(TAG, "Calculating metrics: startTime=$startTime, elapsed=$elapsedSeconds seconds")

            // GPS 기반 거리 누적 계산
            if (lastLatitude != null && lastLongitude != null) {
                val distanceIncrement = gpsSensor.calculateDistance(
                    lastLatitude!!, lastLongitude!!,
                    gpsData.latitude, gpsData.longitude
                )
                totalDistance += distanceIncrement
                Log.d(TAG, "Distance increment: $distanceIncrement m, total: $totalDistance m")
            }
            lastLatitude = gpsData.latitude
            lastLongitude = gpsData.longitude

            // GPS 속도 사용 (m/s), 없으면 거리 기반 계산
            val speed = gpsData.speed?.toDouble() ?: if (elapsedSeconds > 0) {
                totalDistance / elapsedSeconds
            } else 0.0

            // 페이스 계산 (min/km)
            val pace = if (speed > 0) {
                RunningMetrics.speedToPace(speed)
            } else null

            // 걸음 수 기반 케이던스 계산
            totalSteps = steps
            val stepsInLastUpdate = max(0, totalSteps - previousSteps)
            previousSteps = totalSteps

            // 케이던스 계산 (단위: steps per minute)
            val cadence = if (stepsInLastUpdate > 0 && elapsedSeconds > 0) {
                // 최근 업데이트 간격 동안의 걸음 수를 분당으로 변환 (대략 1초 간격 가정)
                stepsInLastUpdate * 60.0
            } else null

            // 심박수 시뮬레이션 (속도 기반)
            val heartRate = when {
                speed < 1.0 -> (90..120).random()
                speed < 2.0 -> (110..140).random()
                speed < 3.0 -> (130..160).random()
                else -> (150..180).random()
            }

            RunningMetrics(
                timestamp = currentTime.toEpochMilli(),
                heartRate = heartRate,
                speed = speed,
                distance = totalDistance,
                cadence = cadence,
                pace = pace,
                altitude = gpsData.altitude,
                elapsedTimeSeconds = elapsedSeconds
            ).also {
                Log.d(TAG, "Metrics: distance=${it.distance}m, speed=${it.speed}m/s, " +
                        "pace=${it.pace}, cadence=${it.cadence}, altitude=${it.altitude}")
            }
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
