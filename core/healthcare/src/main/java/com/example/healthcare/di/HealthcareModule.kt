package com.example.healthcare.di

import com.example.healthcare.data.repository.RunningRepositoryImpl
import com.example.healthcare.domain.repository.RunningRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Healthcare 모듈 의존성 주입
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthcareModule {

    @Binds
    @Singleton
    abstract fun bindRunningRepository(
        runningRepositoryImpl: RunningRepositoryImpl
    ): RunningRepository
}
