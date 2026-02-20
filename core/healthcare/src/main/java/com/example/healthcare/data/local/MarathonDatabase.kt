package com.example.healthcare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.healthcare.data.local.dao.RunningSessionDao
import com.example.healthcare.data.local.entity.LocationPointEntity
import com.example.healthcare.data.local.entity.RunningSessionEntity

@Database(
    entities = [RunningSessionEntity::class, LocationPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MarathonDatabase : RoomDatabase() {
    abstract fun runningSessionDao(): RunningSessionDao
}
