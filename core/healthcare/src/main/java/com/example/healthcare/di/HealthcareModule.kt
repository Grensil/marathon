package com.example.healthcare.di

import android.content.Context
import androidx.room.Room
import com.example.healthcare.data.local.MarathonDatabase
import com.example.healthcare.data.local.dao.RunningSessionDao
import com.example.healthcare.data.repository.RunHistoryRepositoryImpl
import com.example.healthcare.data.repository.RunningRepositoryImpl
import com.example.healthcare.data.sensor.GpsSensor
import com.example.healthcare.data.sensor.StepCounterSensor
import com.example.healthcare.domain.repository.RunHistoryRepository
import com.example.healthcare.domain.repository.RunningRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthcareModule {

    @Binds
    @Singleton
    abstract fun bindRunningRepository(
        runningRepositoryImpl: RunningRepositoryImpl
    ): RunningRepository

    @Binds
    @Singleton
    abstract fun bindRunHistoryRepository(
        runHistoryRepositoryImpl: RunHistoryRepositoryImpl
    ): RunHistoryRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context
        ): MarathonDatabase {
            return Room.databaseBuilder(
                context,
                MarathonDatabase::class.java,
                "marathon_database"
            ).build()
        }

        @Provides
        @Singleton
        fun provideRunningSessionDao(database: MarathonDatabase): RunningSessionDao {
            return database.runningSessionDao()
        }

        @Provides
        @Singleton
        fun provideStepCounterSensor(
            @ApplicationContext context: Context
        ): StepCounterSensor {
            return StepCounterSensor(context)
        }

        @Provides
        @Singleton
        fun provideGpsSensor(
            @ApplicationContext context: Context
        ): GpsSensor {
            return GpsSensor(context)
        }
    }
}
