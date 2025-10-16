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
        // 먼저 권한 확인
        if (!runningRepository.checkPermissions()) {
            return Result.failure(Exception("Health Connect permissions not granted"))
        }

        // 세션 시작
        return runningRepository.startExerciseSession(exerciseType)
    }
}
