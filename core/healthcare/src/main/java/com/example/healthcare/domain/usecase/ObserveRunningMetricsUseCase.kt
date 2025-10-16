package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.model.RunningMetrics
import com.example.healthcare.domain.repository.RunningRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 실시간 러닝 메트릭 관찰 UseCase
 */
class ObserveRunningMetricsUseCase @Inject constructor(
    private val runningRepository: RunningRepository
) {
    operator fun invoke(): Flow<RunningMetrics> {
        return runningRepository.observeRunningMetrics()
    }
}
