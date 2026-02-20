package com.example.healthcare.data.repository

import com.example.healthcare.data.local.dao.RunningSessionDao
import com.example.healthcare.data.local.entity.LocationPointEntity
import com.example.healthcare.data.local.entity.RunningSessionEntity
import com.example.healthcare.domain.repository.RunHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunHistoryRepositoryImpl @Inject constructor(
    private val dao: RunningSessionDao
) : RunHistoryRepository {

    override suspend fun saveSession(session: RunningSessionEntity) {
        dao.insertSession(session)
    }

    override suspend fun updateSession(session: RunningSessionEntity) {
        dao.updateSession(session)
    }

    override fun getAllSessions(): Flow<List<RunningSessionEntity>> {
        return dao.getAllSessions()
    }

    override suspend fun getSessionById(id: String): RunningSessionEntity? {
        return dao.getSessionById(id)
    }

    override suspend fun saveLocationPoint(point: LocationPointEntity) {
        dao.insertLocationPoint(point)
    }

    override suspend fun saveLocationPoints(points: List<LocationPointEntity>) {
        dao.insertLocationPoints(points)
    }

    override suspend fun getLocationPoints(sessionId: String): List<LocationPointEntity> {
        return dao.getLocationPoints(sessionId)
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
