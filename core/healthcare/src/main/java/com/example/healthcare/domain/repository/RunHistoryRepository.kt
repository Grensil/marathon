package com.example.healthcare.domain.repository

import com.example.healthcare.data.local.entity.LocationPointEntity
import com.example.healthcare.data.local.entity.RunningSessionEntity
import kotlinx.coroutines.flow.Flow

interface RunHistoryRepository {
    suspend fun saveSession(session: RunningSessionEntity)
    suspend fun updateSession(session: RunningSessionEntity)
    fun getAllSessions(): Flow<List<RunningSessionEntity>>
    suspend fun getSessionById(id: String): RunningSessionEntity?
    suspend fun saveLocationPoint(point: LocationPointEntity)
    suspend fun saveLocationPoints(points: List<LocationPointEntity>)
    suspend fun getLocationPoints(sessionId: String): List<LocationPointEntity>
    suspend fun deleteSession(sessionId: String)
    suspend fun getTotalDistance(): Double
    suspend fun getTotalDuration(): Long
    suspend fun getSessionCount(): Int
}
