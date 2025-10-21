package com.example.history

import com.example.healthcare.domain.model.RunningMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Current Pace, Average Pace, Cadence 계산 로직 테스트
 */
class PaceAndCadenceCalculationTest {

    @Test
    fun `test pace formatting - 5 minutes per km`() {
        // Given: 5.0 min/km
        val paceInMinutesPerKm = 5.0

        // When: MM:SS 형식으로 변환
        val minutes = paceInMinutesPerKm.toInt() // 5
        val seconds = ((paceInMinutesPerKm - minutes) * 60).toInt() // 0
        val formatted = String.format("%d:%02d", minutes, seconds)

        // Then
        assertEquals("5:00", formatted)
    }

    @Test
    fun `test pace formatting - 5 minutes 30 seconds per km`() {
        // Given: 5.5 min/km
        val paceInMinutesPerKm = 5.5

        // When
        val minutes = paceInMinutesPerKm.toInt() // 5
        val seconds = ((paceInMinutesPerKm - minutes) * 60).toInt() // 30
        val formatted = String.format("%d:%02d", minutes, seconds)

        // Then
        assertEquals("5:30", formatted)
    }

    @Test
    fun `test pace formatting - 6 minutes 15 seconds per km`() {
        // Given: 6.25 min/km
        val paceInMinutesPerKm = 6.25

        // When
        val minutes = paceInMinutesPerKm.toInt() // 6
        val seconds = ((paceInMinutesPerKm - minutes) * 60).toInt() // 15
        val formatted = String.format("%d:%02d", minutes, seconds)

        // Then
        assertEquals("6:15", formatted)
    }

    @Test
    fun `test speed to pace conversion`() {
        // Given: 3.33 m/s 속도
        val speedInMetersPerSecond = 3.33

        // When: 페이스 계산 (min/km)
        val pace = RunningMetrics.speedToPace(speedInMetersPerSecond)

        // Then: 약 5.0 min/km
        // 3.33 m/s = 199.8 m/min = 1 km in 5.005 min
        assertTrue(pace != null)
        assertTrue(pace!! >= 4.9 && pace <= 5.1)
    }

    @Test
    fun `test average pace calculation from multiple measurements`() {
        // Given: 여러 페이스 측정 값
        val paceList = listOf(4.0, 5.0, 6.0, 5.5) // min/km

        // When: 평균 계산
        val avgPace = paceList.average()

        // Then: (4.0 + 5.0 + 6.0 + 5.5) / 4 = 5.125
        assertEquals(5.125, avgPace, 0.001)
    }

    @Test
    fun `test cadence calculation - 30 steps in 10 seconds`() {
        // Given: 30걸음을 10초 동안
        val totalSteps = 30
        val elapsedSeconds = 10

        // When: 케이던스 계산 (steps per minute)
        val cadence = (totalSteps.toDouble() / elapsedSeconds) * 60.0

        // Then: 180 spm
        assertEquals(180.0, cadence, 0.01)
    }

    @Test
    fun `test cadence calculation - 50 steps in 20 seconds`() {
        // Given: 50걸음을 20초 동안
        val totalSteps = 50
        val elapsedSeconds = 20

        // When
        val cadence = (totalSteps.toDouble() / elapsedSeconds) * 60.0

        // Then: 150 spm
        assertEquals(150.0, cadence, 0.01)
    }

    @Test
    fun `test cadence calculation - 100 steps in 30 seconds`() {
        // Given: 100걸음을 30초 동안
        val totalSteps = 100
        val elapsedSeconds = 30

        // When
        val cadence = (totalSteps.toDouble() / elapsedSeconds) * 60.0

        // Then: 200 spm
        assertEquals(200.0, cadence, 0.01)
    }

    @Test
    fun `test current pace vs average pace difference`() {
        // Given: 변동하는 페이스 값들
        val paceList = mutableListOf<Double>()

        // When: 첫 번째 측정 - 빠르게 시작
        paceList.add(4.0)
        val currentPace1 = 4.0
        val avgPace1 = paceList.average()

        // Then: 현재와 평균이 같음
        assertEquals(currentPace1, avgPace1, 0.01)

        // When: 두 번째 측정 - 느려짐
        paceList.add(6.0)
        val currentPace2 = 6.0
        val avgPace2 = paceList.average()

        // Then: 현재는 6.0, 평균은 5.0
        assertEquals(6.0, currentPace2, 0.01)
        assertEquals(5.0, avgPace2, 0.01)
        assertTrue(currentPace2 != avgPace2)

        // When: 세 번째 측정 - 안정화
        paceList.add(5.0)
        val currentPace3 = 5.0
        val avgPace3 = paceList.average()

        // Then: 현재는 5.0, 평균은 5.0
        assertEquals(5.0, currentPace3, 0.01)
        assertEquals(5.0, avgPace3, 0.01)
    }

    @Test
    fun `test realistic running scenario`() {
        // Given: 실제 러닝 시나리오
        // 100m를 30초에 달림 (3.33 m/s)
        val distance1 = 100.0 // meters
        val elapsedTime1 = 30 // seconds
        val speed1 = distance1 / elapsedTime1 // 3.33 m/s

        // When: 페이스 계산
        val pace1 = RunningMetrics.speedToPace(speed1)

        // Then: 약 5분/km
        assertTrue(pace1 != null)
        assertTrue(pace1!! >= 4.9 && pace1 <= 5.1)

        // Given: 200m를 60초에 달림 (속도 동일 유지)
        val distance2 = 200.0
        val elapsedTime2 = 60
        val speed2 = distance2 / elapsedTime2 // 3.33 m/s

        // When
        val pace2 = RunningMetrics.speedToPace(speed2)

        // Then: 여전히 약 5분/km
        assertTrue(pace2 != null)
        assertTrue(pace2!! >= 4.9 && pace2 <= 5.1)

        // Given: 걸음 수도 측정
        val steps1 = 50 // 30초 동안 50걸음
        val cadence1 = (steps1.toDouble() / elapsedTime1) * 60.0

        // Then: 100 spm
        assertEquals(100.0, cadence1, 0.01)

        val steps2 = 100 // 총 60초 동안 100걸음
        val cadence2 = (steps2.toDouble() / elapsedTime2) * 60.0

        // Then: 100 spm (일정 유지)
        assertEquals(100.0, cadence2, 0.01)
    }

    @Test
    fun `test pace list average with many values`() {
        // Given: 많은 페이스 측정 값
        val paceList = mutableListOf<Double>()

        // 5분/km으로 안정적으로 달리다가...
        repeat(10) { paceList.add(5.0) }

        // 갑자기 빨라짐
        paceList.add(4.0)

        // 다시 안정화
        repeat(10) { paceList.add(5.0) }

        // When: 평균 계산
        val avgPace = paceList.average()

        // Then: 평균은 약 4.95분/km (한 번의 스파이크가 평균에 영향)
        // (5.0 * 20 + 4.0) / 21 = 104.0 / 21 = 4.95
        assertTrue(avgPace >= 4.9 && avgPace <= 5.0)
    }

    @Test
    fun `test zero cadence when no steps`() {
        // Given: 걸음이 없음
        val totalSteps = 0
        val elapsedSeconds = 10

        // When: 케이던스 계산
        val cadence = if (totalSteps > 0 && elapsedSeconds > 0) {
            (totalSteps.toDouble() / elapsedSeconds) * 60.0
        } else null

        // Then: null이어야 함
        assertEquals(null, cadence)
    }

    @Test
    fun `test cadence always displayed when steps exist`() {
        // Given: 최소 1걸음이라도 있음
        val totalSteps = 1
        val elapsedSeconds = 10

        // When
        val cadence = if (totalSteps > 0 && elapsedSeconds > 0) {
            (totalSteps.toDouble() / elapsedSeconds) * 60.0
        } else null

        // Then: 6 spm (1걸음/10초 * 60)
        assertEquals(6.0, cadence!!, 0.01)
    }
}
