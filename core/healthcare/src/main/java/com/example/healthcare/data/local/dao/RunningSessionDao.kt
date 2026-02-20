package com.example.healthcare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.healthcare.data.local.entity.LocationPointEntity
import com.example.healthcare.data.local.entity.RunningSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunningSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RunningSessionEntity)

    @Update
    suspend fun updateSession(session: RunningSessionEntity)

    @Query("SELECT * FROM running_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<RunningSessionEntity>>

    @Query("SELECT * FROM running_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): RunningSessionEntity?

    @Insert
    suspend fun insertLocationPoint(point: LocationPointEntity)

    @Insert
    suspend fun insertLocationPoints(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getLocationPoints(sessionId: String): List<LocationPointEntity>

    @Query("DELETE FROM running_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM location_points WHERE sessionId = :sessionId")
    suspend fun deleteLocationPoints(sessionId: String)

    @Query("SELECT COUNT(*) FROM running_sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT SUM(distanceMeters) FROM running_sessions")
    suspend fun getTotalDistance(): Double?

    @Query("SELECT SUM(durationMs) FROM running_sessions")
    suspend fun getTotalDuration(): Long?
}
