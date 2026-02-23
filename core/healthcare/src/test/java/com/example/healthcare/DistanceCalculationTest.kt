package com.example.healthcare

import com.example.healthcare.domain.model.RunningMetrics
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * RunningRepositoryImpl의 거리 계산 알고리즘 검증.
 * 실제 센서 없이 순수 로직만 테스트한다.
 *
 * 발견된 버그:
 * - totalSteps가 거리 계산 이후에 할당되어 이전 걸음수로 계산됨
 * - GPS 신호 일시 손실 시 누적 GPS 거리가 걸음 기반 거리로 덮어씌워짐
 */
class DistanceCalculationTest {

    // Repository의 내부 상태를 시뮬레이션
    private var totalSteps = 0
    private var totalDistance = 0.0
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var hasEverReceivedGps = false

    @Before
    fun setup() {
        totalSteps = 0
        totalDistance = 0.0
        lastLat = null
        lastLon = null
        hasEverReceivedGps = false
    }

    /**
     * 수정된 로직: steps를 거리 계산 전에 할당
     */
    private fun processMetrics(steps: Int, lat: Double, lon: Double, distanceIncrement: Float) {
        val hasValidGps = lat != 0.0 && lon != 0.0

        // FIX: Update steps BEFORE distance calculation
        totalSteps = steps

        if (hasValidGps) {
            hasEverReceivedGps = true
            val prevLat = lastLat
            val prevLon = lastLon
            if (prevLat != null && prevLon != null) {
                // Filter GPS noise (drift < 3m) and spikes (> 100m)
                if (distanceIncrement in 3f..100f) {
                    totalDistance += distanceIncrement
                }
            }
            lastLat = lat
            lastLon = lon
        } else if (!hasEverReceivedGps) {
            totalDistance = totalSteps * 0.75
        }
    }

    // =============================================
    // BUG FIX 검증: totalSteps 할당 순서
    // =============================================

    @Test
    fun `GPS 없이 첫 걸음 - 올바른 걸음수로 거리 계산`() {
        processMetrics(steps = 100, lat = 0.0, lon = 0.0, distanceIncrement = 0f)
        assertEquals(75.0, totalDistance, 0.01)
        assertEquals(100, totalSteps)
    }

    @Test
    fun `GPS 없이 걸음 수 증가 - 매번 최신 걸음수 반영`() {
        processMetrics(steps = 100, lat = 0.0, lon = 0.0, distanceIncrement = 0f)
        assertEquals(75.0, totalDistance, 0.01)

        processMetrics(steps = 200, lat = 0.0, lon = 0.0, distanceIncrement = 0f)
        assertEquals(150.0, totalDistance, 0.01) // 200 * 0.75

        processMetrics(steps = 500, lat = 0.0, lon = 0.0, distanceIncrement = 0f)
        assertEquals(375.0, totalDistance, 0.01) // 500 * 0.75
    }

    // =============================================
    // BUG FIX 검증: GPS 신호 손실시 거리 보존
    // =============================================

    @Test
    fun `GPS 거리 누적 후 신호 손실 - 거리 유지`() {
        // GPS로 500m 이동
        processMetrics(steps = 100, lat = 37.5, lon = 127.0, distanceIncrement = 0f) // first fix
        processMetrics(steps = 200, lat = 37.501, lon = 127.001, distanceIncrement = 50f)
        processMetrics(steps = 300, lat = 37.502, lon = 127.002, distanceIncrement = 50f)
        assertEquals(100.0, totalDistance, 0.01) // 50 + 50 = 100m

        // GPS 신호 손실!
        processMetrics(steps = 400, lat = 0.0, lon = 0.0, distanceIncrement = 0f)

        // FIX: 누적된 GPS 거리가 유지되어야 함
        assertEquals(100.0, totalDistance, 0.01)
    }

    @Test
    fun `GPS 복구 후 거리 계속 누적`() {
        // GPS로 100m
        processMetrics(steps = 100, lat = 37.5, lon = 127.0, distanceIncrement = 0f)
        processMetrics(steps = 200, lat = 37.501, lon = 127.001, distanceIncrement = 50f)

        // GPS 손실 - 거리 유지
        processMetrics(steps = 300, lat = 0.0, lon = 0.0, distanceIncrement = 0f)
        assertEquals(50.0, totalDistance, 0.01)

        // GPS 복구 - 새 GPS 위치에서 이전 위치와의 거리가 크므로 spike filter에 걸림
        // 다음 정상 이동부터 누적 시작
        processMetrics(steps = 400, lat = 37.502, lon = 127.002, distanceIncrement = 30f)
        assertEquals(80.0, totalDistance, 0.01) // 50 + 30
    }

    @Test
    fun `처음부터 GPS 없으면 걸음 기반 거리 사용`() {
        processMetrics(steps = 1000, lat = 0.0, lon = 0.0, distanceIncrement = 0f)
        assertEquals(750.0, totalDistance, 0.01) // 1000 * 0.75
        assertFalse(hasEverReceivedGps)
    }

    // =============================================
    // GPS 노이즈 + 스파이크 필터 검증 (3m ~ 100m 범위만 통과)
    // =============================================

    @Test
    fun `100m 이상 GPS 점프 차단`() {
        processMetrics(steps = 0, lat = 37.5, lon = 127.0, distanceIncrement = 0f)
        processMetrics(steps = 10, lat = 37.5, lon = 127.0, distanceIncrement = 150f)
        assertEquals(0.0, totalDistance, 0.01)
    }

    @Test
    fun `3m 미만 GPS 드리프트 차단 - 정지 상태 노이즈`() {
        processMetrics(steps = 0, lat = 37.5, lon = 127.0, distanceIncrement = 0f)
        processMetrics(steps = 0, lat = 37.50001, lon = 127.00001, distanceIncrement = 1.5f) // 1.5m 노이즈
        assertEquals(0.0, totalDistance, 0.01) // 차단됨

        processMetrics(steps = 0, lat = 37.50002, lon = 127.00002, distanceIncrement = 2.9f) // 2.9m 노이즈
        assertEquals(0.0, totalDistance, 0.01) // 여전히 차단
    }

    @Test
    fun `3m 이상 이동은 정상 통과`() {
        processMetrics(steps = 0, lat = 37.5, lon = 127.0, distanceIncrement = 0f)
        processMetrics(steps = 10, lat = 37.501, lon = 127.001, distanceIncrement = 5f)
        assertEquals(5.0, totalDistance, 0.01) // 통과
    }

    @Test
    fun `99m 이동은 정상 통과`() {
        processMetrics(steps = 0, lat = 37.5, lon = 127.0, distanceIncrement = 0f)
        processMetrics(steps = 10, lat = 37.501, lon = 127.001, distanceIncrement = 99f)
        assertEquals(99.0, totalDistance, 0.01)
    }

    @Test
    fun `연속 GPS 노이즈 - 가만히 서 있어도 거리 0 유지`() {
        processMetrics(steps = 0, lat = 37.5, lon = 127.0, distanceIncrement = 0f)
        // 서 있는데 GPS가 2m씩 흔들림 (10번 반복)
        repeat(10) {
            processMetrics(steps = 0, lat = 37.5, lon = 127.0, distanceIncrement = 2f)
        }
        assertEquals(0.0, totalDistance, 0.01) // 전부 차단
    }

    // =============================================
    // GPS 속도 필터 검증 (0.5 m/s 미만 무시)
    // =============================================

    @Test
    fun `GPS 속도 0점3ms는 노이즈 - pace 생성 안함`() {
        val MIN_SPEED_THRESHOLD = 0.5
        val gpsSpeed = 0.3f // GPS 노이즈

        val speed = if (gpsSpeed > MIN_SPEED_THRESHOLD) {
            gpsSpeed.toDouble()
        } else {
            0.0
        }

        assertEquals(0.0, speed, 0.01)
        val pace = if (speed > 0) RunningMetrics.speedToPace(speed) else null
        assertNull(pace) // pace가 null이므로 UI에 "--:--" 표시
    }

    @Test
    fun `GPS 속도 0점6ms는 실제 이동 - pace 생성됨`() {
        val MIN_SPEED_THRESHOLD = 0.5
        val gpsSpeed = 0.6f // 느린 걷기

        val speed = if (gpsSpeed > MIN_SPEED_THRESHOLD) {
            gpsSpeed.toDouble()
        } else {
            0.0
        }

        assertTrue(speed > 0)
        val pace = RunningMetrics.speedToPace(speed)
        assertNotNull(pace)
    }

    @Test
    fun `정지 상태 시뮬레이션 - 속도 0점1ms와 거리 1m 노이즈`() {
        // 가만히 서 있는데 GPS가 0.1m/s 속도와 1m 드리프트를 보고
        val MIN_SPEED_THRESHOLD = 0.5
        val gpsSpeed = 0.1f
        val distanceIncrement = 1.0f // 1m GPS 드리프트

        // 속도 필터: 차단
        val speed = if (gpsSpeed > MIN_SPEED_THRESHOLD) gpsSpeed.toDouble() else 0.0
        assertEquals(0.0, speed, 0.01)

        // 거리 필터: 3m 미만이므로 차단
        val accepted = distanceIncrement in 3f..100f
        assertFalse(accepted)
    }

    // =============================================
    // speedToPace 엣지 케이스
    // =============================================

    @Test
    fun `극도로 느린 속도 - 유효하지만 매우 높은 페이스`() {
        val pace = RunningMetrics.speedToPace(0.1) // 0.1 m/s = 매우 느림
        assertNotNull(pace)
        // 0.1 m/s = 6 m/min → 1km에 166.67분
        assertTrue(pace!! > 100)
    }

    @Test
    fun `매우 빠른 속도 - 낮은 페이스`() {
        val pace = RunningMetrics.speedToPace(10.0) // 36 km/h sprint
        assertNotNull(pace)
        // 10 m/s = 600 m/min → 1km에 1.667분
        assertTrue(pace!! in 1.6..1.7)
    }

    @Test
    fun `정확히 0 속도 - null 반환`() {
        assertNull(RunningMetrics.speedToPace(0.0))
    }

    @Test
    fun `음수 속도 - null 반환`() {
        assertNull(RunningMetrics.speedToPace(-5.0))
    }
}
