package com.example.healthcare.domain.usecase

import com.example.healthcare.domain.repository.RunningRepository
import javax.inject.Inject

/**
 * Health Connect 권한 확인 UseCase
 */
class CheckHealthConnectPermissionsUseCase @Inject constructor(
    private val runningRepository: RunningRepository
) {
    suspend operator fun invoke(): Boolean {
        return runningRepository.checkPermissions()
    }
}
