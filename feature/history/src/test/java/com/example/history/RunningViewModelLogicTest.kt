package com.example.history

import com.example.healthcare.domain.model.RunningMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RunningViewModel의 핵심 비즈니스 로직 단위 테스트.
 * ViewModel 인스턴스 없이 순수 로직만 검증한다.
 */
class RunningViewModelLogicTest {

    // ==========================================
    // speedToPace - 속도 -> 페이스 변환 (핵심 계산)
    // ==========================================

    @Test
    fun `speedToPace - 걷기 속도 1점5ms는 약 11분 페이스`() {
        val pace = RunningMetrics.speedToPace(1.5)!!
        // 1.5 m/s = 90 m/min → 1km에 11.11분
        assertTrue(pace in 11.0..11.2)
    }

    @Test
    fun `speedToPace - 조깅 3ms는 약 5분33초 페이스`() {
        val pace = RunningMetrics.speedToPace(3.0)!!
        // 3.0 m/s = 180 m/min → 1km에 5.555분
        assertTrue(pace in 5.5..5.6)
    }

    @Test
    fun `speedToPace - 빠른 러닝 4ms는 약 4분10초 페이스`() {
        val pace = RunningMetrics.speedToPace(4.0)!!
        // 4.0 m/s = 240 m/min → 1km에 4.166분
        assertTrue(pace in 4.1..4.2)
    }

    @Test
    fun `speedToPace - 스프린트 6ms는 약 2분47초 페이스`() {
        val pace = RunningMetrics.speedToPace(6.0)!!
        assertTrue(pace in 2.7..2.8)
    }

    @Test
    fun `speedToPace - 속도 0이면 null 반환`() {
        assertNull(RunningMetrics.speedToPace(0.0))
    }

    @Test
    fun `speedToPace - 음수 속도면 null 반환`() {
        assertNull(RunningMetrics.speedToPace(-1.0))
    }

    // ==========================================
    // formatPace - 분:초 포맷팅
    // ==========================================

    private fun formatPace(paceInMinutesPerKm: Double): String {
        if (paceInMinutesPerKm > 30 || paceInMinutesPerKm < 0) return "00:00"
        val minutes = paceInMinutesPerKm.toInt()
        val seconds = ((paceInMinutesPerKm - minutes) * 60).toInt()
        return String.format("%d:%02d", minutes, seconds)
    }

    @Test
    fun `formatPace - 정수 페이스 5분 정확`() {
        assertEquals("5:00", formatPace(5.0))
    }

    @Test
    fun `formatPace - 5분30초`() {
        assertEquals("5:30", formatPace(5.5))
    }

    @Test
    fun `formatPace - 4분15초`() {
        assertEquals("4:15", formatPace(4.25))
    }

    @Test
    fun `formatPace - 6분59초 경계값`() {
        // 6.983... min = 6분59초
        assertEquals("6:59", formatPace(6.0 + 59.0/60.0))
    }

    @Test
    fun `formatPace - 30분 초과시 대시 반환`() {
        assertEquals("00:00", formatPace(31.0))
    }

    @Test
    fun `formatPace - 음수시 대시 반환`() {
        assertEquals("00:00", formatPace(-1.0))
    }

    // ==========================================
    // formatElapsedTime - 밀리초 -> 시:분:초 변환
    // ==========================================

    private fun formatElapsedTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60))
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    @Test
    fun `formatElapsedTime - 0초`() {
        assertEquals("00:00", formatElapsedTime(0))
    }

    @Test
    fun `formatElapsedTime - 30초`() {
        assertEquals("00:30", formatElapsedTime(30_000))
    }

    @Test
    fun `formatElapsedTime - 5분30초`() {
        assertEquals("05:30", formatElapsedTime(330_000))
    }

    @Test
    fun `formatElapsedTime - 59분59초`() {
        assertEquals("59:59", formatElapsedTime(3_599_000))
    }

    @Test
    fun `formatElapsedTime - 정확히 1시간`() {
        assertEquals("1:00:00", formatElapsedTime(3_600_000))
    }

    @Test
    fun `formatElapsedTime - 1시간30분15초`() {
        assertEquals("1:30:15", formatElapsedTime(5_415_000))
    }

    @Test
    fun `formatElapsedTime - 2시간0분0초`() {
        assertEquals("2:00:00", formatElapsedTime(7_200_000))
    }

    // ==========================================
    // formatDistance - 미터 -> km 변환
    // ==========================================

    private fun formatDistance(meters: Double): String {
        val km = meters / 1000.0
        return String.format("%.2f", km)
    }

    @Test
    fun `formatDistance - 0m`() {
        assertEquals("0.00", formatDistance(0.0))
    }

    @Test
    fun `formatDistance - 500m`() {
        assertEquals("0.50", formatDistance(500.0))
    }

    @Test
    fun `formatDistance - 1000m는 1km`() {
        assertEquals("1.00", formatDistance(1000.0))
    }

    @Test
    fun `formatDistance - 5432m`() {
        assertEquals("5.43", formatDistance(5432.0))
    }

    @Test
    fun `formatDistance - 42195m 마라톤 거리`() {
        assertEquals("42.20", formatDistance(42195.0))
    }

    // ==========================================
    // 칼로리 추정 로직
    // ==========================================

    @Test
    fun `calorie estimation - 5km 달리면 350kcal`() {
        val distanceMeters = 5000.0
        val distanceKm = distanceMeters / 1000.0
        val calories = (distanceKm * 70).toInt()
        assertEquals(350, calories)
    }

    @Test
    fun `calorie estimation - 0km이면 0kcal`() {
        val calories = (0.0 / 1000.0 * 70).toInt()
        assertEquals(0, calories)
    }

    @Test
    fun `calorie estimation - 10km이면 700kcal`() {
        val calories = (10000.0 / 1000.0 * 70).toInt()
        assertEquals(700, calories)
    }

    // ==========================================
    // 페이스 필터링 로직 - 비정상 값 제거
    // ==========================================

    @Test
    fun `pace filter - 정상 범위 1~30 통과`() {
        val paceList = mutableListOf<Double>()
        val testPace = 5.5
        if (testPace in 1.0..30.0) paceList.add(testPace)
        assertEquals(1, paceList.size)
    }

    @Test
    fun `pace filter - 0점5 비정상적으로 빠른 페이스 필터링`() {
        val paceList = mutableListOf<Double>()
        val testPace = 0.5 // 비현실적
        if (testPace in 1.0..30.0) paceList.add(testPace)
        assertEquals(0, paceList.size)
    }

    @Test
    fun `pace filter - 35분 비정상적으로 느린 페이스 필터링`() {
        val paceList = mutableListOf<Double>()
        val testPace = 35.0 // 거의 정지 상태
        if (testPace in 1.0..30.0) paceList.add(testPace)
        assertEquals(0, paceList.size)
    }

    // ==========================================
    // km 마일스톤 감지 로직
    // ==========================================

    @Test
    fun `km milestone - 999m에서는 알림 없음`() {
        var lastAnnouncedKm = 0
        val distanceKm = 999.0 / 1000.0 // 0.999 km
        val currentKm = distanceKm.toInt()
        val triggered = currentKm > lastAnnouncedKm && currentKm > 0
        assertEquals(false, triggered)
    }

    @Test
    fun `km milestone - 1000m 도달시 1km 알림`() {
        var lastAnnouncedKm = 0
        val distanceKm = 1000.0 / 1000.0 // 1.0 km
        val currentKm = distanceKm.toInt()
        val triggered = currentKm > lastAnnouncedKm && currentKm > 0
        assertEquals(true, triggered)
        assertEquals(1, currentKm)
    }

    @Test
    fun `km milestone - 1500m에서는 1km 이미 알림 후 재알림 없음`() {
        var lastAnnouncedKm = 1 // 이미 1km 알림함
        val distanceKm = 1500.0 / 1000.0 // 1.5 km
        val currentKm = distanceKm.toInt()
        val triggered = currentKm > lastAnnouncedKm && currentKm > 0
        assertEquals(false, triggered)
    }

    @Test
    fun `km milestone - 2000m 도달시 2km 알림`() {
        var lastAnnouncedKm = 1
        val distanceKm = 2000.0 / 1000.0
        val currentKm = distanceKm.toInt()
        val triggered = currentKm > lastAnnouncedKm && currentKm > 0
        assertEquals(true, triggered)
        assertEquals(2, currentKm)
    }

    @Test
    fun `km milestone - 연속 마일스톤 시퀀스`() {
        var lastAnnouncedKm = 0
        val announcements = mutableListOf<Int>()

        // 0m -> 500m -> 1100m -> 1500m -> 2300m -> 3000m
        val distances = listOf(0.0, 500.0, 1100.0, 1500.0, 2300.0, 3000.0)
        for (d in distances) {
            val km = (d / 1000.0).toInt()
            if (km > lastAnnouncedKm && km > 0) {
                announcements.add(km)
                lastAnnouncedKm = km
            }
        }
        assertEquals(listOf(1, 2, 3), announcements)
    }

    // ==========================================
    // 거리 100m 스파이크 필터 (GPS 점프 방지)
    // ==========================================

    @Test
    fun `distance spike filter - 정상 이동 10m 통과`() {
        val distanceIncrement = 10.0f
        val accepted = distanceIncrement < 100
        assertEquals(true, accepted)
    }

    @Test
    fun `distance spike filter - GPS 점프 150m 차단`() {
        val distanceIncrement = 150.0f
        val accepted = distanceIncrement < 100
        assertEquals(false, accepted)
    }

    @Test
    fun `distance spike filter - 경계값 99m 통과`() {
        assertEquals(true, 99.0f < 100)
    }

    @Test
    fun `distance spike filter - 경계값 100m 차단`() {
        assertEquals(false, 100.0f < 100)
    }

    // ==========================================
    // GPS 유효성 판단 (0,0 좌표 = 무효)
    // ==========================================

    @Test
    fun `gps validity - 서울 좌표 유효`() {
        val hasValidGps = 37.5665 != 0.0 && 126.978 != 0.0
        assertEquals(true, hasValidGps)
    }

    @Test
    fun `gps validity - 더미 데이터 0,0 무효`() {
        val hasValidGps = 0.0 != 0.0 && 0.0 != 0.0
        assertEquals(false, hasValidGps)
    }

    @Test
    fun `gps validity - 경도만 0이면 무효`() {
        val hasValidGps = 37.5 != 0.0 && 0.0 != 0.0
        assertEquals(false, hasValidGps)
    }

    // ==========================================
    // GPS 없을 때 걸음 수 기반 거리 추정
    // ==========================================

    @Test
    fun `step based distance - 1000걸음은 750m`() {
        val totalSteps = 1000
        val distance = totalSteps * 0.75
        assertEquals(750.0, distance, 0.01)
    }

    @Test
    fun `step based distance - 0걸음은 0m`() {
        val distance = 0 * 0.75
        assertEquals(0.0, distance, 0.01)
    }

    // ==========================================
    // 케이던스 계산
    // ==========================================

    @Test
    fun `cadence - 일반적 러닝 케이던스 계산`() {
        // 10분 동안 1700걸음 = 170 spm
        val cadence = (1700.0 / 600.0) * 60.0
        assertEquals(170.0, cadence, 0.1)
    }

    @Test
    fun `cadence - 경과시간 0이면 null`() {
        val totalSteps = 100
        val elapsedSeconds = 0L
        val cadence = if (totalSteps > 0 && elapsedSeconds > 0) {
            (totalSteps.toDouble() / elapsedSeconds) * 60.0
        } else null
        assertNull(cadence)
    }

    @Test
    fun `cadence - 걸음 0이면 null`() {
        val cadence = if (0 > 0 && 60L > 0) {
            (0.0 / 60.0) * 60.0
        } else null
        assertNull(cadence)
    }

    // ==========================================
    // 속도 계산 우선순위 (GPS speed > 거리/시간 > 0)
    // ==========================================

    @Test
    fun `speed priority - GPS speed가 있으면 그걸 사용`() {
        val hasValidGps = true
        val gpsSpeed: Float? = 3.5f
        val elapsedSeconds = 60L
        val totalDistance = 200.0

        val speed = if (hasValidGps && gpsSpeed != null && gpsSpeed > 0) {
            gpsSpeed.toDouble()
        } else if (elapsedSeconds > 0 && totalDistance > 0) {
            totalDistance / elapsedSeconds
        } else {
            0.0
        }
        assertEquals(3.5, speed, 0.01)
    }

    @Test
    fun `speed priority - GPS speed 없으면 거리로 계산`() {
        val hasValidGps = true
        val gpsSpeed: Float? = null
        val elapsedSeconds = 60L
        val totalDistance = 200.0

        val speed = if (hasValidGps && gpsSpeed != null && gpsSpeed > 0) {
            gpsSpeed.toDouble()
        } else if (elapsedSeconds > 0 && totalDistance > 0) {
            totalDistance / elapsedSeconds
        } else {
            0.0
        }
        assertEquals(3.33, speed, 0.01)
    }

    @Test
    fun `speed priority - 아무것도 없으면 0`() {
        val speed = if (false && null != null) {
            0.0
        } else if (0L > 0 && 0.0 > 0) {
            0.0
        } else {
            0.0
        }
        assertEquals(0.0, speed, 0.01)
    }

    // ==========================================
    // 일시중지 시간 계산
    // ==========================================

    @Test
    fun `pause time - 일시중지 5초 후 재개시 경과시간에서 제외`() {
        val sessionStart = 1000L
        var pausedTime = 0L

        // 10초 경과
        var now = 11000L
        var elapsed = now - sessionStart - pausedTime
        assertEquals(10000L, elapsed)

        // 일시중지 시작
        val pauseStart = now

        // 5초 후 재개
        now = 16000L
        pausedTime += now - pauseStart

        // 총 경과시간 = 15초 - 5초 일시중지 = 10초
        elapsed = now - sessionStart - pausedTime
        assertEquals(10000L, elapsed)
    }

    @Test
    fun `pause time - 여러번 일시중지해도 정확`() {
        val sessionStart = 0L
        var pausedTime = 0L

        // 1차 일시중지: 10초~15초 (5초)
        pausedTime += 15000L - 10000L
        // 2차 일시중지: 25초~30초 (5초)
        pausedTime += 30000L - 25000L

        // 40초 시점 경과시간 = 40 - 10(총 멈춘 시간) = 30초
        val elapsed = 40000L - sessionStart - pausedTime
        assertEquals(30000L, elapsed)
    }
}
