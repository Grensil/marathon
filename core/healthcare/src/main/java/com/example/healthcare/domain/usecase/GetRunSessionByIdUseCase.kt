package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.model.RunHistory
import com.example.healthcare.domain.repository.RunHistoryRepository
import javax.inject.Inject

class GetRunSessionByIdUseCase @Inject constructor(
    private val runHistoryRepository: RunHistoryRepository
) {
    suspend operator fun invoke(sessionId: String): RunHistory? {
        return runHistoryRepository.getSessionById(sessionId)
    }
}
