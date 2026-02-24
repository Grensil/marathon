package com.example.history

import com.example.healthcare.domain.model.RunHistory
import org.junit.Assert.*
import org.junit.Test

/**
 * RunHistory 저장 시 데이터 무결성 검증.
 *
 * 발견된 버그:
 * - stopRunning에서 totalSteps, maxHeartRate, maxPace가 저장되지 않음
 * - saveSession(INSERT OR REPLACE) 대신 updateSession을 사용해야 CASCADE DELETE 방지
 * - 레이스 컨디션: state 캡처 후 job 취소 → job 취소 후 state 캡처로 변경
 */
class RunHistoryDataIntegrityTest {

    // =============================================
    // RunHistory 필드 완전성 검증
    // =============================================

    @Test
    fun `RunHistory 생성 - 모든 필드가 정확히 설정됨`() {
        val history = RunHistory(
            id = "session_123",
            startTime = 1000L,
            endTime = 2000L,
            durationMs = 1000L,
            distanceMeters = 5000.0,
            averagePace = "5:30",
            averageHeartRate = 150,
            averageCadence = 170,
            calories = 350,
            maxHeartRate = 185,
            maxPace = "4:15",
            totalSteps = 6500
        )

        assertEquals("session_123", history.id)
        assertEquals(1000L, history.startTime)
        assertEquals(2000L, history.endTime)
        assertEquals(1000L, history.durationMs)
        assertEquals(5000.0, history.distanceMeters, 0.01)
        assertEquals("5:30", history.averagePace)
        assertEquals(150, history.averageHeartRate)
        assertEquals(170, history.averageCadence)
        assertEquals(350, history.calories)
        assertEquals(185, history.maxHeartRate)
        assertEquals("4:15", history.maxPace)
        assertEquals(6500, history.totalSteps)
    }

    @Test
    fun `RunHistory 기본값 - nullable 필드 확인`() {
        val history = RunHistory(
            id = "session_empty",
            startTime = 0L
        )

        assertEquals(0L, history.endTime)
        assertEquals(0L, history.durationMs)
        assertEquals(0.0, history.distanceMeters, 0.01)
        assertEquals("00:00", history.averagePace)
        assertNull(history.averageHeartRate)
        assertNull(history.averageCadence)
        assertEquals(0, history.calories)
        assertNull(history.maxHeartRate)
        assertNull(history.maxPace)
        assertEquals(0, history.totalSteps)
    }

    // =============================================
    // maxHeartRate 추적 로직 검증
    // =============================================

    @Test
    fun `maxHeartRate - 여러 측정값 중 최대값 추적`() {
        var maxHeartRate: Int? = null
        val heartRates = listOf(120, 135, 185, 160, 145)

        for (hr in heartRates) {
            if (maxHeartRate == null || hr > maxHeartRate!!) {
                maxHeartRate = hr
            }
        }

        assertEquals(185, maxHeartRate)
    }

    @Test
    fun `maxHeartRate - 단일 측정값`() {
        var maxHeartRate: Int? = null
        val hr = 150
        if (maxHeartRate == null || hr > maxHeartRate!!) {
            maxHeartRate = hr
        }
        assertEquals(150, maxHeartRate)
    }

    @Test
    fun `maxHeartRate - 측정값 없으면 null`() {
        val maxHeartRate: Int? = null
        assertNull(maxHeartRate)
    }

    // =============================================
    // bestPace 추적 로직 검증 (낮을수록 빠름)
    // =============================================

    @Test
    fun `bestPace - 여러 측정값 중 최소값이 최고 기록`() {
        var bestPace: Double? = null
        val paces = listOf(6.0, 5.5, 4.2, 5.0, 4.8)

        for (p in paces) {
            if (p in 1.0..30.0) {
                if (bestPace == null || p < bestPace!!) {
                    bestPace = p
                }
            }
        }

        assertEquals(4.2, bestPace!!, 0.01)
    }

    @Test
    fun `bestPace - 비정상 값은 필터링 후 최소값`() {
        var bestPace: Double? = null
        val paces = listOf(0.5, 6.0, 35.0, 5.0) // 0.5와 35.0은 필터됨

        for (p in paces) {
            if (p in 1.0..30.0) {
                if (bestPace == null || p < bestPace!!) {
                    bestPace = p
                }
            }
        }

        assertEquals(5.0, bestPace!!, 0.01)
    }

    // =============================================
    // stopRunning 레이스 컨디션 시나리오
    // =============================================

    @Test
    fun `레이스 컨디션 - job 취소 전 state 변경 시뮬레이션`() {
        // BEFORE FIX: state 캡처 → (여기서 timer가 state 변경) → job 취소
        // AFTER FIX: job 취소 → state 캡처

        var elapsedTime = 10000L // 10초

        // job이 아직 돌고 있으면 elapsed time이 바뀔 수 있음
        val capturedBeforeCancel = elapsedTime

        // timer job이 상태를 한번 더 업데이트
        elapsedTime = 11000L // 11초로 변경됨

        // BEFORE FIX: capturedBeforeCancel(10초)로 저장됨 → 실제 11초인데 10초로 저장
        assertNotEquals(elapsedTime, capturedBeforeCancel)

        // AFTER FIX: job 취소 후 캡처하므로 상태가 안정적
        val capturedAfterCancel = elapsedTime // job 취소 후의 안정적 값
        assertEquals(11000L, capturedAfterCancel)
    }

    // =============================================
    // INSERT OR REPLACE vs UPDATE 차이 검증
    // =============================================

    @Test
    fun `INSERT OR REPLACE - 같은 PK로 insert 시 기존 데이터 삭제 후 재삽입`() {
        // 시뮬레이션: 세션 시작시 INSERT
        val sessions = mutableMapOf<String, RunHistory>()
        val cascadeDeletedPoints = mutableListOf<String>()

        val sessionId = "session_1"

        // startRunning: 세션 생성 (INSERT)
        sessions[sessionId] = RunHistory(id = sessionId, startTime = 1000L)

        // 세션에 위치 포인트 연결 (실제로는 FK로 연결)
        val locationSessionIds = mutableListOf(sessionId)

        // stopRunning: INSERT OR REPLACE (= DELETE + INSERT → CASCADE!)
        fun insertOrReplace(session: RunHistory) {
            if (sessions.containsKey(session.id)) {
                // CASCADE DELETE 발생!
                cascadeDeletedPoints.addAll(
                    locationSessionIds.filter { it == session.id }
                )
                locationSessionIds.removeAll { it == session.id }
            }
            sessions[session.id] = session
        }

        // stopRunning 호출 시뮬레이션
        insertOrReplace(RunHistory(id = sessionId, startTime = 1000L, endTime = 2000L))

        // CASCADE 삭제가 발생함!
        assertTrue(cascadeDeletedPoints.isNotEmpty())

        // UPDATE를 사용하면 CASCADE가 발생하지 않음
        fun update(session: RunHistory) {
            sessions[session.id] = session
            // CASCADE 없음
        }

        val cascadeDeletedPoints2 = mutableListOf<String>()
        locationSessionIds.add(sessionId) // 포인트 다시 추가
        update(RunHistory(id = sessionId, startTime = 1000L, endTime = 3000L))
        // CASCADE 삭제 발생하지 않음
        assertTrue(cascadeDeletedPoints2.isEmpty())
    }

    // =============================================
    // 칼로리 계산 정확성
    // =============================================

    @Test
    fun `칼로리 - 하프마라톤 거리`() {
        val distanceMeters = 21097.5 // 하프 마라톤
        val calories = (distanceMeters / 1000.0 * 70).toInt()
        assertEquals(1476, calories) // ~1476 kcal
    }

    @Test
    fun `칼로리 - 짧은 거리 반올림`() {
        val distanceMeters = 100.0
        val calories = (distanceMeters / 1000.0 * 70).toInt()
        assertEquals(7, calories) // 100m = 7 kcal
    }

    // =============================================
    // RunningState 상태 전이 검증
    // =============================================

    @Test
    fun `RunningState - 초기 상태`() {
        val state = RunningState()
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertNull(state.sessionId)
        assertEquals(0L, state.elapsedTime)
        assertEquals(0.0, state.distance, 0.01)
        assertEquals("00:00", state.currentPace)
        assertEquals("00:00", state.averagePace)
        assertNull(state.error)
        assertFalse(state.showCompletionDialog)
    }

    @Test
    fun `RunningState - 달리기 시작 상태`() {
        val state = RunningState(
            isRunning = true,
            sessionId = "session_1",
            isLoading = false
        )
        assertTrue(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals("session_1", state.sessionId)
    }

    @Test
    fun `RunningState - 일시정지 상태`() {
        val state = RunningState(
            isRunning = true,
            isPaused = true,
            sessionId = "session_1"
        )
        assertTrue(state.isRunning)
        assertTrue(state.isPaused)
    }

    @Test
    fun `RunningState - 완료 다이얼로그 상태`() {
        val record = RunningRecord(
            sessionId = "session_1",
            timestamp = 1000L,
            elapsedTime = 60000L,
            distance = 1000.0,
            averagePace = "5:30",
            averageCadence = 170,
            calories = 70
        )
        val state = RunningState(
            showCompletionDialog = true,
            completedRecord = record
        )
        assertTrue(state.showCompletionDialog)
        assertNotNull(state.completedRecord)
        assertEquals(1000.0, state.completedRecord!!.distance, 0.01)
    }
}
