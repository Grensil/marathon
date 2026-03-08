package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.repository.RunHistoryRepository
import javax.inject.Inject

class DeleteRunSessionUseCase @Inject constructor(
    private val runHistoryRepository: RunHistoryRepository
) {
    suspend operator fun invoke(sessionId: String) {
        runHistoryRepository.deleteSession(sessionId)
    }
}
