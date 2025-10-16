package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.model.ExerciseType
import com.example.healthcare.domain.repository.RunningRepository
import javax.inject.Inject

/**
 * 러닝 세션 시작 UseCase
 */
class StartRunningSessionUseCase @Inject constructor(
    private val runningRepository: RunningRepository
) {
    suspend operator fun invoke(exerciseType: ExerciseType = ExerciseType.RUNNING): Result<String> {
        // Health Connect 권한 체크 제거 - 폰 센서만 사용
        // 세션 시작
        return runningRepository.startExerciseSession(exerciseType)
    }
}
