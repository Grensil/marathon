package com.example.healthcare.data.repository

import com.example.healthcare.data.datasource.HealthConnectDataSource
import com.example.healthcare.domain.model.ExerciseType
import com.example.healthcare.domain.model.RunningMetrics
import com.example.healthcare.domain.model.RunningSession
import com.example.healthcare.domain.repository.RunningRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 러닝 리포지토리 구현
 */
@Singleton
class RunningRepositoryImpl @Inject constructor(
    private val healthConnectDataSource: HealthConnectDataSource
) : RunningRepository {

    private var currentSessionId: String? = null
    private var sessionStartTime: Instant? = null

    override suspend fun startExerciseSession(exerciseType: ExerciseType): Result<String> {
        val startTime = Instant.now()
        sessionStartTime = startTime

        return healthConnectDataSource.startExerciseSession(exerciseType, startTime)
            .onSuccess { sessionId ->
                currentSessionId = sessionId
            }
    }

    override suspend fun stopExerciseSession(sessionId: String): Result<Unit> {
        val endTime = Instant.now()
        return healthConnectDataSource.stopExerciseSession(sessionId, endTime)
            .onSuccess {
                currentSessionId = null
                sessionStartTime = null
            }
    }

    override fun observeRunningMetrics(): Flow<RunningMetrics> = flow {
        val startTime = sessionStartTime ?: Instant.now()

        // 2초마다 최신 데이터를 폴링
        while (currentSessionId != null) {
            val currentTime = Instant.now()
            val pollingStartTime = currentTime.minusSeconds(5) // 최근 5초 데이터

            // 심박수 데이터
            val heartRateResult = healthConnectDataSource.getHeartRateData(
                pollingStartTime,
                currentTime
            )
            val latestHeartRate = heartRateResult.getOrNull()
                ?.flatMap { it.samples }
                ?.maxByOrNull { it.time }
                ?.beatsPerMinute?.toInt()

            // 속도 데이터
            val speedResult = healthConnectDataSource.getSpeedData(
                pollingStartTime,
                currentTime
            )
            val latestSpeed = speedResult.getOrNull()
                ?.flatMap { it.samples }
                ?.maxByOrNull { it.time }
                ?.speed?.inMetersPerSecond

            // 걸음 수 데이터 (케이던스 계산용)
            val stepsResult = healthConnectDataSource.getStepsData(
                pollingStartTime,
                currentTime
            )
            val totalSteps = stepsResult.getOrNull()?.sumOf { it.count } ?: 0
            val cadence = if (totalSteps > 0) {
                // 5초 동안의 걸음 수를 분당으로 변환
                (totalSteps / 5.0) * 60.0
            } else null

            // 페이스 계산
            val pace = latestSpeed?.let { RunningMetrics.speedToPace(it) }

            val metrics = RunningMetrics(
                timestamp = currentTime.toEpochMilli(),
                heartRate = latestHeartRate,
                speed = latestSpeed,
                cadence = cadence,
                pace = pace
            )

            emit(metrics)
            delay(2000) // 2초마다 업데이트
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
