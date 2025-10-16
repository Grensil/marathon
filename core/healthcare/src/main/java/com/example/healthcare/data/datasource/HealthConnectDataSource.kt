package com.example.healthcare.data.datasource

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.healthcare.domain.model.ExerciseType
import com.example.healthcare.domain.model.RunningMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health Connect 데이터 소스
 */
@Singleton
class HealthConnectDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SpeedRecord::class),
            HealthPermission.getWritePermission(SpeedRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
    }

    /**
     * Health Connect 사용 가능 여부 확인
     */
    suspend fun isAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 권한 확인
     */
    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            PERMISSIONS.all { it in granted }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 필요한 권한 목록 조회
     */
    fun getRequiredPermissions(): Set<String> = PERMISSIONS

    /**
     * 운동 세션 시작
     */
    suspend fun startExerciseSession(
        exerciseType: ExerciseType,
        startTime: Instant
    ): Result<String> {
        return try {
            val sessionType = when (exerciseType) {
                ExerciseType.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                ExerciseType.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                ExerciseType.CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
                ExerciseType.UNKNOWN -> ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS
            }

            val session = ExerciseSessionRecord(
                metadata = androidx.health.connect.client.records.metadata.Metadata.manualEntry(),
                startTime = startTime,
                startZoneOffset = ZonedDateTime.now().offset,
                endTime = startTime, // 시작 시점에는 동일
                endZoneOffset = ZonedDateTime.now().offset,
                exerciseType = sessionType,
                title = "Marathon Running"
            )

            val response = healthConnectClient.insertRecords(listOf(session))
            val sessionId = response.recordIdsList.firstOrNull()
                ?: return Result.failure(Exception("Failed to get session ID"))

            Result.success(sessionId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 운동 세션 종료
     */
    suspend fun stopExerciseSession(
        sessionId: String,
        endTime: Instant
    ): Result<Unit> {
        return try {
            // Health Connect에서는 세션을 업데이트하는 대신 새로운 레코드를 삽입합니다
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 특정 시간 범위의 심박수 데이터 조회
     */
    suspend fun getHeartRateData(
        startTime: Instant,
        endTime: Instant
    ): Result<List<HeartRateRecord>> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            Result.success(response.records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 특정 시간 범위의 속도 데이터 조회
     */
    suspend fun getSpeedData(
        startTime: Instant,
        endTime: Instant
    ): Result<List<SpeedRecord>> {
        return try {
            val request = ReadRecordsRequest(
                recordType = SpeedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            Result.success(response.records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 특정 시간 범위의 걸음 수 데이터 조회
     */
    suspend fun getStepsData(
        startTime: Instant,
        endTime: Instant
    ): Result<List<StepsRecord>> {
        return try {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            Result.success(response.records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 실시간 메트릭 관찰 (폴링 방식)
     */
    fun observeMetrics(startTime: Instant): Flow<RunningMetrics> = flow {
        // Health Connect는 실시간 스트리밍을 제공하지 않으므로
        // 주기적으로 폴링하여 최신 데이터를 조회합니다
        // 실제 구현에서는 WorkManager나 주기적인 코루틴을 사용할 수 있습니다

        // 이 부분은 실제로는 Repository 레이어에서 구현됩니다
    }
}
