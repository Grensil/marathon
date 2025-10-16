package com.example.healthcare.data.repository

import android.util.Log
import com.example.healthcare.data.datasource.HealthConnectDataSource
import com.example.healthcare.data.sensor.StepCounterSensor
import com.example.healthcare.domain.model.ExerciseType
import com.example.healthcare.domain.model.RunningMetrics
import com.example.healthcare.domain.model.RunningSession
import com.example.healthcare.domain.repository.RunningRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * 러닝 리포지토리 구현
 */
@Singleton
class RunningRepositoryImpl @Inject constructor(
    private val healthConnectDataSource: HealthConnectDataSource,
    private val stepCounterSensor: StepCounterSensor
) : RunningRepository {

    private var currentSessionId: String? = null
    private var sessionStartTime: Instant? = null
    private var totalSteps: Int = 0
    private var previousSteps: Int = 0
    private var sessionStartStepCount: Int = 0

    companion object {
        private const val TAG = "RunningRepositoryImpl"
    }

    override suspend fun startExerciseSession(exerciseType: ExerciseType): Result<String> {
        val startTime = Instant.now()
        sessionStartTime = startTime
        Log.d(TAG, "startExerciseSession called, sessionStartTime=$sessionStartTime")

        return healthConnectDataSource.startExerciseSession(exerciseType, startTime)
            .onSuccess { sessionId ->
                currentSessionId = sessionId
                totalSteps = 0
                previousSteps = 0
                Log.d(TAG, "Health Connect session started: $sessionId")
            }
            .onFailure { error ->
                // Health Connect 실패 시 시뮬레이션 모드로 세션 생성
                val simulationSessionId = "simulation_${System.currentTimeMillis()}"
                currentSessionId = simulationSessionId
                totalSteps = 0
                previousSteps = 0
                Log.d(TAG, "Simulation session started: $simulationSessionId (Health Connect failed: ${error.message})")
                return Result.success(simulationSessionId)
            }
    }

    override suspend fun stopExerciseSession(sessionId: String): Result<Unit> {
        // 시뮬레이션 세션인 경우
        if (sessionId.startsWith("simulation_")) {
            currentSessionId = null
            sessionStartTime = null
            totalSteps = 0
            previousSteps = 0
            stepCounterSensor.resetSession()
            return Result.success(Unit)
        }

        // Health Connect 세션인 경우
        val endTime = Instant.now()
        return healthConnectDataSource.stopExerciseSession(sessionId, endTime)
            .onSuccess {
                currentSessionId = null
                sessionStartTime = null
                totalSteps = 0
                previousSteps = 0
                stepCounterSensor.resetSession()
            }
    }

    override fun observeRunningMetrics(): Flow<RunningMetrics> = flow {
        val startTime = sessionStartTime ?: Instant.now()
        stepCounterSensor.resetSession()
        Log.d(TAG, "observeRunningMetrics started, startTime=$startTime, currentSessionId=$currentSessionId")

        // 걸음 센서와 타이머를 결합
        stepCounterSensor.observeSteps().collect { steps ->
            Log.d(TAG, "Received steps from sensor: $steps, currentSessionId=$currentSessionId")

            if (currentSessionId == null) {
                Log.w(TAG, "currentSessionId is null, skipping metrics emission")
                return@collect
            }

            val currentTime = Instant.now()
            val elapsedSeconds = java.time.Duration.between(startTime, currentTime).seconds

            // 걸음 수 기반 계산
            totalSteps = steps
            val stepsInLast2Seconds = max(0, totalSteps - previousSteps)
            previousSteps = totalSteps

            // 거리 계산 (평균 보폭 0.75m 가정)
            val distance = totalSteps * 0.75 // 미터

            // 속도 계산 (m/s)
            val speed = if (elapsedSeconds > 0) {
                distance / elapsedSeconds
            } else 0.0

            // 페이스 계산 (min/km)
            val pace = if (speed > 0) {
                RunningMetrics.speedToPace(speed)
            } else null

            // 케이던스 계산 (최근 2초간 걸음 수를 분당으로 변환)
            val cadence = if (stepsInLast2Seconds > 0) {
                (stepsInLast2Seconds / 2.0) * 60.0
            } else null

            // 심박수 시뮬레이션 (속도 기반으로 계산)
            // 느리게 걸을 때: 90-120, 빠르게 달릴 때: 150-180
            val heartRate = when {
                speed < 1.0 -> (90..120).random() // 천천히 걷기
                speed < 2.0 -> (110..140).random() // 빠르게 걷기
                speed < 3.0 -> (130..160).random() // 조깅
                else -> (150..180).random() // 달리기
            }

            val metrics = RunningMetrics(
                timestamp = currentTime.toEpochMilli(),
                heartRate = heartRate,
                speed = speed,
                distance = distance,
                cadence = cadence,
                pace = pace
            )

            Log.d(TAG, "Emitting metrics: steps=$totalSteps, distance=$distance, heartRate=$heartRate")
            emit(metrics)
        }
    }

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
            // 권한 요청은 Activity에서 처리해야 하므로
            // 이 메서드는 필요한 권한 목록만 반환합니다
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isHealthConnectAvailable(): Boolean {
        return healthConnectDataSource.isAvailable()
    }
}
