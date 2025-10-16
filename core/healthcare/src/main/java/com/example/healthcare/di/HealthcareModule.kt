package com.example.healthcare.di

import android.content.Context
import com.example.healthcare.data.repository.RunningRepositoryImpl
import com.example.healthcare.data.sensor.StepCounterSensor
import com.example.healthcare.domain.repository.RunningRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    companion object {
        @Provides
        @Singleton
        fun provideStepCounterSensor(
            @ApplicationContext context: Context
        ): StepCounterSensor {
            return StepCounterSensor(context)
        }
    }
}
