package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.model.RunningSession
import com.example.healthcare.domain.repository.RunningRepository
import javax.inject.Inject

/**
 * 활성 세션 조회 UseCase
 */
class GetActiveSessionUseCase @Inject constructor(
    private val runningRepository: RunningRepository
) {
    suspend operator fun invoke(): Result<RunningSession?> {
        return runningRepository.getActiveSession()
    }
}
