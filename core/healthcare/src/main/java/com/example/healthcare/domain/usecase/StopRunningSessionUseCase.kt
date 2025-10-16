package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.repository.RunningRepository
import javax.inject.Inject

/**
 * 러닝 세션 종료 UseCase
 */
class StopRunningSessionUseCase @Inject constructor(
    private val runningRepository: RunningRepository
) {
    suspend operator fun invoke(sessionId: String): Result<Unit> {
        return runningRepository.stopExerciseSession(sessionId)
    }
}
