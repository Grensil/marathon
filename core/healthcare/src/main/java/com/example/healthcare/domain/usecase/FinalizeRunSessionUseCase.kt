package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.model.RoutePoint
import com.example.healthcare.domain.model.RunHistory
import com.example.healthcare.domain.repository.RunHistoryRepository
import javax.inject.Inject

class FinalizeRunSessionUseCase @Inject constructor(
    private val runHistoryRepository: RunHistoryRepository
) {
    suspend operator fun invoke(session: RunHistory, locationPoints: List<RoutePoint>) {
        runHistoryRepository.updateSession(session)
        runHistoryRepository.saveLocationPoints(session.id, locationPoints)
    }
}
