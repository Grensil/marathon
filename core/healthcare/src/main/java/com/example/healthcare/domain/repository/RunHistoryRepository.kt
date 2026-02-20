package com.example.healthcare.domain.repository

import com.example.healthcare.domain.model.RoutePoint
import com.example.healthcare.domain.model.RunHistory
import kotlinx.coroutines.flow.Flow

interface RunHistoryRepository {
    suspend fun saveSession(session: RunHistory)
    suspend fun updateSession(session: RunHistory)
    fun getAllSessions(): Flow<List<RunHistory>>
    suspend fun getSessionById(id: String): RunHistory?
    suspend fun saveLocationPoints(sessionId: String, points: List<RoutePoint>)
    suspend fun getLocationPoints(sessionId: String): List<RoutePoint>
    suspend fun deleteSession(sessionId: String)
    suspend fun getTotalDistance(): Double
    suspend fun getTotalDuration(): Long
    suspend fun getSessionCount(): Int
}
