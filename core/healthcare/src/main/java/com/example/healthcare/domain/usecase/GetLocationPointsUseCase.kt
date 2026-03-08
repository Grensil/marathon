package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.model.RoutePoint
import com.example.healthcare.domain.repository.RunHistoryRepository
import javax.inject.Inject

class GetLocationPointsUseCase @Inject constructor(
    private val runHistoryRepository: RunHistoryRepository
) {
    suspend operator fun invoke(sessionId: String): List<RoutePoint> {
        return runHistoryRepository.getLocationPoints(sessionId)
    }
}
