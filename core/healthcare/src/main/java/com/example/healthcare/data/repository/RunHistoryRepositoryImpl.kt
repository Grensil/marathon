package com.example.healthcare.data.repository

import com.example.healthcare.data.local.dao.RunningSessionDao
import com.example.healthcare.data.mapper.toDomain
import com.example.healthcare.data.mapper.toEntity
import com.example.healthcare.domain.model.RoutePoint
import com.example.healthcare.domain.model.RunHistory
import com.example.healthcare.domain.repository.RunHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunHistoryRepositoryImpl @Inject constructor(
    private val dao: RunningSessionDao
) : RunHistoryRepository {

    override suspend fun saveSession(session: RunHistory) {
        dao.insertSession(session.toEntity())
    }

    override suspend fun updateSession(session: RunHistory) {
        dao.updateSession(session.toEntity())
    }

    override fun getAllSessions(): Flow<List<RunHistory>> {
        return dao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSessionById(id: String): RunHistory? {
        return dao.getSessionById(id)?.toDomain()
    }

    override suspend fun saveLocationPoints(sessionId: String, points: List<RoutePoint>) {
        dao.insertLocationPoints(points.map { it.toEntity(sessionId) })
    }

    override suspend fun getLocationPoints(sessionId: String): List<RoutePoint> {
        return dao.getLocationPoints(sessionId).map { it.toDomain() }
    }

    override suspend fun deleteSession(sessionId: String) {
        dao.deleteLocationPoints(sessionId)
        dao.deleteSession(sessionId)
    }

    override suspend fun getTotalDistance(): Double {
        return dao.getTotalDistance() ?: 0.0
    }

    override suspend fun getTotalDuration(): Long {
        return dao.getTotalDuration() ?: 0L
    }

    override suspend fun getSessionCount(): Int {
        return dao.getSessionCount()
    }
}
