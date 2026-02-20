package com.example.healthcare.data.mapper

import com.example.healthcare.data.local.entity.LocationPointEntity
import com.example.healthcare.data.local.entity.RunningSessionEntity
import com.example.healthcare.domain.model.RoutePoint
import com.example.healthcare.domain.model.RunHistory

fun RunningSessionEntity.toDomain(): RunHistory = RunHistory(
    id = id,
    startTime = startTime,
    endTime = endTime,
    durationMs = durationMs,
    distanceMeters = distanceMeters,
    averagePace = averagePace,
    averageHeartRate = averageHeartRate,
    averageCadence = averageCadence,
    calories = calories,
    maxHeartRate = maxHeartRate,
    maxPace = maxPace,
    totalSteps = totalSteps
)

fun RunHistory.toEntity(): RunningSessionEntity = RunningSessionEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    durationMs = durationMs,
    distanceMeters = distanceMeters,
    averagePace = averagePace,
    averageHeartRate = averageHeartRate,
    averageCadence = averageCadence,
    calories = calories,
    maxHeartRate = maxHeartRate,
    maxPace = maxPace,
    totalSteps = totalSteps
)

fun LocationPointEntity.toDomain(): RoutePoint = RoutePoint(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    timestamp = timestamp,
    orderIndex = orderIndex
)

fun RoutePoint.toEntity(sessionId: String): LocationPointEntity = LocationPointEntity(
    sessionId = sessionId,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    timestamp = timestamp,
    orderIndex = orderIndex
)
