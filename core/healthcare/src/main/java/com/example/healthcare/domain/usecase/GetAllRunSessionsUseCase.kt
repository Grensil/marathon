package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.model.RunHistory
import com.example.healthcare.domain.repository.RunHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllRunSessionsUseCase @Inject constructor(
    private val runHistoryRepository: RunHistoryRepository
) {
    operator fun invoke(): Flow<List<RunHistory>> {
        return runHistoryRepository.getAllSessions()
    }
}
