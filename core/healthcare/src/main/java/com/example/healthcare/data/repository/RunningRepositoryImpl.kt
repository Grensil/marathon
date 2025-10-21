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
    private var lastCadenceUpdateTime: Long = 0L
    private var stepsAtLastCadenceUpdate: Int = 0

    companion object {
        private const val TAG = "Logd"
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
                emit(0) // 에러 발생 시 0 방출
            },
            gpsSensor.observeLocation().catch { e ->
                Log.d(TAG, "GpsSensor error: ${e.message}")
                // GPS 에러 시 더미 데이터 방출
                emit(com.example.healthcare.data.sensor.GpsData(0.0, 0.0, null, null, 0f, 0L))
            }
        ) { steps, gpsData ->
            Log.d(TAG, ">>> Combining: steps=$steps, gps=(${gpsData.latitude}, ${gpsData.longitude})")

            if (currentSessionId == null) {
                Log.d(TAG, "currentSessionId is null, skipping metrics emission")
                return@combine null
            }

            // 경과 시간 계산
            val startTime = sessionStartTime ?: Instant.now()
            val currentTime = Instant.now()
            val elapsedSeconds = java.time.Duration.between(startTime, currentTime).seconds
            Log.d(TAG, "Calculating metrics: startTime=$startTime, elapsed=$elapsedSeconds seconds")

            // GPS 기반 거리 누적 계산 (GPS 데이터가 유효한 경우만)
            val hasValidGps = gpsData.latitude != 0.0 && gpsData.longitude != 0.0

            if (hasValidGps) {
                if (lastLatitude != null && lastLongitude != null) {
                    val distanceIncrement = gpsSensor.calculateDistance(
                        lastLatitude!!, lastLongitude!!,
                        gpsData.latitude, gpsData.longitude
                    )
                    // 비정상적으로 큰 이동 필터링 (100m 이상은 무시)
                    if (distanceIncrement < 100) {
                        totalDistance += distanceIncrement
                        Log.d(TAG, "GPS distance increment: $distanceIncrement m, total: $totalDistance m")
                    } else {
                        Log.d(TAG, "Ignoring abnormal distance jump: $distanceIncrement m")
                    }
                }
                lastLatitude = gpsData.latitude
                lastLongitude = gpsData.longitude
            } else {
                // GPS 없으면 걸음 수 기반 거리 계산 (평균 보폭 0.75m)
                totalDistance = totalSteps * 0.75
                Log.d(TAG, "Step-based distance: $totalDistance m (steps: $totalSteps)")
            }

            // GPS 속도 우선, 없으면 거리/시간 기반 계산
            val speed = if (hasValidGps && gpsData.speed != null && gpsData.speed > 0) {
                gpsData.speed.toDouble()
            } else if (elapsedSeconds > 0 && totalDistance > 0) {
                totalDistance / elapsedSeconds
            } else {
                0.0
            }

            Log.d(TAG, "Speed: $speed m/s (GPS: $hasValidGps, elapsed: $elapsedSeconds s)")

            // 페이스 계산 (min/km)
            val pace = if (speed > 0) {
                RunningMetrics.speedToPace(speed)
            } else null

            // 걸음 수 기반 케이던스 계산
            totalSteps = steps

            // 케이던스 계산 (단위: steps per minute)
            // 전체 세션 평균 케이던스
            val cadence = if (totalSteps > 0 && elapsedSeconds > 0) {
                val avgCadence = (totalSteps.toDouble() / elapsedSeconds) * 60.0
                Log.d(TAG, "Cadence: $totalSteps steps in $elapsedSeconds s = $avgCadence spm")
                avgCadence
            } else null

            Log.d(TAG, "Steps: $totalSteps, cadence: $cadence spm")

            // 심박수는 실제 데이터가 없으므로 null
            val heartRate: Int? = null

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
