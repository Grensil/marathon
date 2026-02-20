package com.example.healthcare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "running_sessions")
data class RunningSessionEntity(
    @PrimaryKey val id: String,
    val startTime: Long,
    val endTime: Long = 0L,
    val durationMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val averagePace: String = "--:--",
    val averageHeartRate: Int? = null,
    val averageCadence: Int? = null,
    val calories: Int = 0,
    val maxHeartRate: Int? = null,
    val maxPace: String? = null,
    val totalSteps: Int = 0
)
