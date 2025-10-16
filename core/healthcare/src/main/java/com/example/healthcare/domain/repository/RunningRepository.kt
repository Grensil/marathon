package com.example.healthcare.domain.repository

import com.example.healthcare.domain.model.ExerciseType
import com.example.healthcare.domain.model.RunningMetrics
import com.example.healthcare.domain.model.RunningSession
import kotlinx.coroutines.flow.Flow

/**
 * 러닝 데이터 리포지토리 인터페이스
 */
interface RunningRepository {
    /**
     * 운동 세션 시작
     */
    suspend fun startExerciseSession(exerciseType: ExerciseType): Result<String>

    /**
     * 운동 세션 종료
     */
    suspend fun stopExerciseSession(sessionId: String): Result<Unit>

    /**
     * 실시간 메트릭 구독
     */
    fun observeRunningMetrics(): Flow<RunningMetrics>

    /**
     * 현재 활성 세션 정보 조회
     */
    suspend fun getActiveSession(): Result<RunningSession?>

    /**
     * Health Connect 권한 확인
     */
    suspend fun checkPermissions(): Boolean

    /**
     * Health Connect 권한 요청
     */
    suspend fun requestPermissions(): Result<Unit>

    /**
     * Health Connect 사용 가능 여부
     */
    suspend fun isHealthConnectAvailable(): Boolean
}
