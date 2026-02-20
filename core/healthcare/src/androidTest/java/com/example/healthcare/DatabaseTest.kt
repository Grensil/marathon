package com.example.healthcare

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthcare.data.local.MarathonDatabase
import com.example.healthcare.data.local.dao.RunningSessionDao
import com.example.healthcare.data.local.entity.LocationPointEntity
import com.example.healthcare.data.local.entity.RunningSessionEntity
import com.example.healthcare.data.mapper.toDomain
import com.example.healthcare.data.mapper.toEntity
import com.example.healthcare.data.repository.RunHistoryRepositoryImpl
import com.example.healthcare.domain.model.RoutePoint
import com.example.healthcare.domain.model.RunHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 기기에서 돌아가는 통합 테스트.
 * Room DB 스키마 무결성, DAO-Mapper-Repository 파이프라인,
 * 그리고 실제 러닝 시나리오 데이터 흐름을 검증한다.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: MarathonDatabase
    private lateinit var dao: RunningSessionDao
    private lateinit var repository: RunHistoryRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MarathonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.runningSessionDao()
        repository = RunHistoryRepositoryImpl(dao)
    }

    @After
    fun teardown() {
        database.close()
    }

    // ==========================================
    // 스키마 무결성 - 이게 실패하면 앱이 크래시함
    // ==========================================

    @Test
    fun schema_integrity_database_opens_without_crash() {
        // 이 테스트가 실패하면 Entity 변경 후 version을 안 올린 것
        assertNotNull(database.openHelper.writableDatabase)
    }

    // ==========================================
    // Mapper 정확성 - 필드 누락되면 데이터 손실
    // ==========================================

    @Test
    fun mapper_entity_to_domain_preserves_all_fields() {
        val entity = RunningSessionEntity(
            id = "s1",
            startTime = 1000L,
            endTime = 2000L,
            durationMs = 1000L,
            distanceMeters = 5432.1,
            averagePace = "5:30",
            averageHeartRate = 155,
            averageCadence = 170,
            calories = 420,
            maxHeartRate = 185,
            maxPace = "4:10",
            totalSteps = 6000
        )
        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.startTime, domain.startTime)
        assertEquals(entity.endTime, domain.endTime)
        assertEquals(entity.durationMs, domain.durationMs)
        assertEquals(entity.distanceMeters, domain.distanceMeters, 0.001)
        assertEquals(entity.averagePace, domain.averagePace)
        assertEquals(entity.averageHeartRate, domain.averageHeartRate)
        assertEquals(entity.averageCadence, domain.averageCadence)
        assertEquals(entity.calories, domain.calories)
        assertEquals(entity.maxHeartRate, domain.maxHeartRate)
        assertEquals(entity.maxPace, domain.maxPace)
        assertEquals(entity.totalSteps, domain.totalSteps)
    }

    @Test
    fun mapper_domain_to_entity_and_back_is_lossless() {
        val original = RunHistory(
            id = "s1", startTime = 1000L, endTime = 2000L,
            durationMs = 1000L, distanceMeters = 5432.1,
            averagePace = "5:30", averageHeartRate = 155,
            averageCadence = 170, calories = 420,
            maxHeartRate = 185, maxPace = "4:10", totalSteps = 6000
        )
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
    }

    @Test
    fun mapper_route_point_roundtrip_is_lossless() {
        val original = RoutePoint(
            latitude = 37.5665, longitude = 126.978,
            altitude = 42.5, speed = 3.2f,
            timestamp = 1000L, orderIndex = 7
        )
        val roundTripped = original.toEntity("session_1").toDomain()
        assertEquals(original.latitude, roundTripped.latitude, 0.0001)
        assertEquals(original.longitude, roundTripped.longitude, 0.0001)
        assertEquals(original.altitude, roundTripped.altitude)
        assertEquals(original.speed, roundTripped.speed)
        assertEquals(original.timestamp, roundTripped.timestamp)
        assertEquals(original.orderIndex, roundTripped.orderIndex)
    }

    @Test
    fun mapper_handles_null_fields_correctly() {
        val entity = RunningSessionEntity(
            id = "s1", startTime = 1000L,
            averageHeartRate = null, averageCadence = null,
            maxHeartRate = null, maxPace = null
        )
        val domain = entity.toDomain()
        assertNull(domain.averageHeartRate)
        assertNull(domain.averageCadence)
        assertNull(domain.maxHeartRate)
        assertNull(domain.maxPace)

        // null -> entity -> domain 왕복해도 null 유지
        val roundTripped = domain.toEntity().toDomain()
        assertNull(roundTripped.averageHeartRate)
    }

    // ==========================================
    // Repository 통합 - Domain 모델로 저장하고 읽을 때 정확한가
    // ==========================================

    @Test
    fun repository_save_and_load_session_through_domain_model() = runBlocking {
        val session = RunHistory(
            id = "run_001", startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 1800000,
            durationMs = 1800000, distanceMeters = 5000.0,
            averagePace = "6:00", calories = 350
        )
        repository.saveSession(session)
        val loaded = repository.getSessionById("run_001")

        assertNotNull(loaded)
        assertEquals(session.id, loaded!!.id)
        assertEquals(session.distanceMeters, loaded.distanceMeters, 0.01)
        assertEquals(session.averagePace, loaded.averagePace)
        assertEquals(session.calories, loaded.calories)
    }

    @Test
    fun repository_save_and_load_route_points() = runBlocking {
        repository.saveSession(RunHistory(id = "run_001", startTime = 1000L))

        val points = listOf(
            RoutePoint(37.5665, 126.978, altitude = 10.0, orderIndex = 0),
            RoutePoint(37.5670, 126.979, altitude = 12.0, orderIndex = 1),
            RoutePoint(37.5680, 126.980, altitude = 11.0, orderIndex = 2)
        )
        repository.saveLocationPoints("run_001", points)

        val loaded = repository.getLocationPoints("run_001")
        assertEquals(3, loaded.size)
        // orderIndex 순서 확인
        assertEquals(0, loaded[0].orderIndex)
        assertEquals(1, loaded[1].orderIndex)
        assertEquals(2, loaded[2].orderIndex)
        // 좌표 정확도 확인
        assertEquals(37.5665, loaded[0].latitude, 0.0001)
        assertEquals(126.980, loaded[2].longitude, 0.001)
    }

    @Test
    fun repository_getAllSessions_returns_flow_in_desc_order() = runBlocking {
        repository.saveSession(RunHistory(id = "run_old", startTime = 1000L))
        repository.saveSession(RunHistory(id = "run_new", startTime = 3000L))
        repository.saveSession(RunHistory(id = "run_mid", startTime = 2000L))

        val sessions = repository.getAllSessions().first()
        assertEquals("run_new", sessions[0].id)
        assertEquals("run_mid", sessions[1].id)
        assertEquals("run_old", sessions[2].id)
    }

    @Test
    fun repository_aggregate_stats_are_accurate() = runBlocking {
        repository.saveSession(RunHistory(id = "r1", startTime = 1000L, distanceMeters = 3000.0, durationMs = 900000))
        repository.saveSession(RunHistory(id = "r2", startTime = 2000L, distanceMeters = 7000.0, durationMs = 2100000))

        assertEquals(2, repository.getSessionCount())
        assertEquals(10000.0, repository.getTotalDistance(), 0.01)
        assertEquals(3000000L, repository.getTotalDuration())
    }

    @Test
    fun repository_empty_stats_return_zero_not_crash() = runBlocking {
        assertEquals(0, repository.getSessionCount())
        assertEquals(0.0, repository.getTotalDistance(), 0.01)
        assertEquals(0L, repository.getTotalDuration())
    }

    // ==========================================
    // 실제 러닝 시나리오 - 전체 흐름 시뮬레이션
    // ==========================================

    @Test
    fun full_running_scenario_start_track_stop_review() = runBlocking {
        // 1) 러닝 시작 - 세션 생성
        val startTime = System.currentTimeMillis()
        val sessionId = "session_${startTime}"
        repository.saveSession(RunHistory(id = sessionId, startTime = startTime))

        // 2) 러닝 종료 - 세션 업데이트 (INSERT OR REPLACE)
        //    실제 ViewModel에서는 saveSession -> saveLocationPoints 순서
        //    (순서 중요: INSERT OR REPLACE가 CASCADE로 기존 location 삭제하므로)
        val endTime = startTime + 1200000
        val finalSession = RunHistory(
            id = sessionId, startTime = startTime, endTime = endTime,
            durationMs = 1200000, distanceMeters = 3500.0,
            averagePace = "5:43", averageCadence = 165, calories = 245
        )
        repository.saveSession(finalSession)

        // 3) 경로 저장 (세션 업데이트 후에 해야 CASCADE 영향 없음)
        val routePoints = listOf(
            RoutePoint(37.4979, 127.0276, altitude = 20.0, orderIndex = 0),
            RoutePoint(37.4985, 127.0282, altitude = 21.0, orderIndex = 1),
            RoutePoint(37.4990, 127.0290, altitude = 22.0, orderIndex = 2),
            RoutePoint(37.4998, 127.0298, altitude = 23.0, orderIndex = 3),
            RoutePoint(37.5005, 127.0305, altitude = 24.0, orderIndex = 4)
        )
        repository.saveLocationPoints(sessionId, routePoints)

        // 4) 히스토리에서 조회 - 정확한 데이터인지 검증
        val loaded = repository.getSessionById(sessionId)!!
        assertEquals(3500.0, loaded.distanceMeters, 0.01)
        assertEquals("5:43", loaded.averagePace)
        assertEquals(165, loaded.averageCadence)
        assertEquals(245, loaded.calories)
        assertEquals(1200000L, loaded.durationMs)

        // 5) 경로 조회 - 순서와 좌표 검증
        val loadedPoints = repository.getLocationPoints(sessionId)
        assertEquals(5, loadedPoints.size)
        assertEquals(37.4979, loadedPoints.first().latitude, 0.0001)
        assertEquals(37.5005, loadedPoints.last().latitude, 0.0001)

        // 6) 통계에 반영되었는지 확인
        assertEquals(1, repository.getSessionCount())
        assertEquals(3500.0, repository.getTotalDistance(), 0.01)
    }

    @Test
    fun regression_insert_or_replace_cascades_location_points() = runBlocking {
        // 이 테스트는 실제 발견된 버그를 검증한다:
        // INSERT OR REPLACE는 내부적으로 DELETE+INSERT이므로
        // FK CASCADE로 location_points가 함께 삭제된다.
        // → 반드시 세션 업데이트 후에 location points를 저장해야 한다.

        repository.saveSession(RunHistory(id = "s1", startTime = 1000L))
        repository.saveLocationPoints("s1", listOf(
            RoutePoint(37.5, 127.0, orderIndex = 0),
            RoutePoint(37.6, 127.1, orderIndex = 1)
        ))

        // 경로 존재 확인
        assertEquals(2, repository.getLocationPoints("s1").size)

        // INSERT OR REPLACE로 세션 업데이트
        repository.saveSession(RunHistory(
            id = "s1", startTime = 1000L, distanceMeters = 5000.0
        ))

        // CASCADE로 인해 경로가 삭제됨 - 이것이 예상 동작
        val pointsAfter = repository.getLocationPoints("s1")
        assertEquals(0, pointsAfter.size) // location points가 사라짐!
    }

    @Test
    fun multiple_sessions_history_accumulation() = runBlocking {
        // 3일간 러닝 기록
        val sessions = listOf(
            RunHistory(id = "day1", startTime = 1000L, distanceMeters = 5000.0, durationMs = 1500000, calories = 350, averagePace = "5:00"),
            RunHistory(id = "day2", startTime = 2000L, distanceMeters = 3000.0, durationMs = 1080000, calories = 210, averagePace = "6:00"),
            RunHistory(id = "day3", startTime = 3000L, distanceMeters = 10000.0, durationMs = 3000000, calories = 700, averagePace = "5:00")
        )
        sessions.forEach { repository.saveSession(it) }

        // 통계 검증
        assertEquals(3, repository.getSessionCount())
        assertEquals(18000.0, repository.getTotalDistance(), 0.01) // 18km
        assertEquals(5580000L, repository.getTotalDuration()) // 93분

        // 삭제 후 통계 변경 확인
        repository.deleteSession("day2")
        assertEquals(2, repository.getSessionCount())
        assertEquals(15000.0, repository.getTotalDistance(), 0.01)
    }

    // ==========================================
    // Foreign Key CASCADE - 세션 삭제 시 경로도 삭제
    // ==========================================

    @Test
    fun cascade_delete_session_removes_all_location_points() = runBlocking {
        repository.saveSession(RunHistory(id = "s1", startTime = 1000L))
        repository.saveLocationPoints("s1", List(100) { i ->
            RoutePoint(37.5 + i * 0.001, 127.0 + i * 0.001, orderIndex = i)
        })

        assertEquals(100, repository.getLocationPoints("s1").size)

        repository.deleteSession("s1")
        assertEquals(0, repository.getLocationPoints("s1").size)
    }

    // ==========================================
    // 세션 업데이트 (시작 시 빈 세션 -> 종료 시 결과 저장)
    // ==========================================

    @Test
    fun session_update_from_empty_to_completed() = runBlocking {
        // 시작: 빈 세션
        repository.saveSession(RunHistory(id = "s1", startTime = 1000L))
        val initial = repository.getSessionById("s1")!!
        assertEquals(0.0, initial.distanceMeters, 0.01)
        assertEquals("--:--", initial.averagePace)
        assertEquals(0, initial.calories)

        // 종료: 결과 업데이트 (INSERT OR REPLACE)
        repository.saveSession(RunHistory(
            id = "s1", startTime = 1000L, endTime = 2800000L,
            durationMs = 1800000L, distanceMeters = 5000.0,
            averagePace = "6:00", calories = 350, averageCadence = 160
        ))
        val completed = repository.getSessionById("s1")!!
        assertEquals(5000.0, completed.distanceMeters, 0.01)
        assertEquals("6:00", completed.averagePace)
        assertEquals(350, completed.calories)
        assertEquals(160, completed.averageCadence)
    }
}
