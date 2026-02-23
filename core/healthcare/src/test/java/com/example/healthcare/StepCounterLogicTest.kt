package com.example.healthcare

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * StepCounterSensor의 걸음 수 계산 로직 검증.
 *
 * 발견된 버그:
 * - TYPE_STEP_COUNTER와 TYPE_STEP_DETECTOR가 동시에 sessionSteps를 수정하여
 *   걸음 수가 이중으로 카운트됨
 *
 * 수정: TYPE_STEP_COUNTER만 사용하고 TYPE_STEP_DETECTOR 제거
 */
class StepCounterLogicTest {

    // StepCounterSensor의 내부 상태 시뮬레이션
    private var initialStepCount: Int? = null
    private var currentTotalSteps: Int = 0
    private var sessionSteps: Int = 0

    @Before
    fun setup() {
        initialStepCount = null
        currentTotalSteps = 0
        sessionSteps = 0
    }

    /**
     * TYPE_STEP_COUNTER 이벤트 처리 (수정된 로직)
     * 부팅 이후 누적 걸음수에서 세션 시작 시 걸음수를 빼서 세션 걸음수 계산
     */
    private fun onStepCounterEvent(totalStepsSinceBoot: Int): Int {
        currentTotalSteps = totalStepsSinceBoot
        val initCount = initialStepCount
        if (initCount == null) {
            initialStepCount = currentTotalSteps - sessionSteps
        } else {
            sessionSteps = currentTotalSteps - initCount
        }
        return sessionSteps
    }

    /**
     * TYPE_STEP_DETECTOR 이벤트 처리 (버그 원인 - 제거됨)
     */
    private fun onStepDetectorEvent(): Int {
        sessionSteps++
        return sessionSteps
    }

    // =============================================
    // 수정된 로직: STEP_COUNTER만 사용
    // =============================================

    @Test
    fun `정상 동작 - STEP_COUNTER만 사용시 정확한 걸음수`() {
        // 부팅 후 이미 5000걸음 걸은 상태에서 세션 시작
        val bootSteps = 5000

        onStepCounterEvent(bootSteps)       // 첫 이벤트: 기준점 설정
        assertEquals(0, sessionSteps)        // 세션 시작이므로 0

        onStepCounterEvent(bootSteps + 10)  // 10걸음
        assertEquals(10, sessionSteps)

        onStepCounterEvent(bootSteps + 50)  // 50걸음
        assertEquals(50, sessionSteps)

        onStepCounterEvent(bootSteps + 100) // 100걸음
        assertEquals(100, sessionSteps)
    }

    @Test
    fun `이중 카운팅 버그 - COUNTER와 DETECTOR 동시 사용시 오류 발생`() {
        val bootSteps = 5000

        onStepCounterEvent(bootSteps)       // 기준점 설정
        assertEquals(0, sessionSteps)

        // Counter: 10걸음
        onStepCounterEvent(bootSteps + 10)
        assertEquals(10, sessionSteps)

        // BUG: Detector가 sessionSteps를 ++ → 11이 됨
        onStepDetectorEvent()
        assertEquals(11, sessionSteps) // 실제로는 10걸음인데 11로 됨!

        // Counter: 다음 이벤트에서 totalSteps - initialCount 계산
        // initialStepCount = 5000, currentTotalSteps = 5020 → sessionSteps = 20
        // 하지만 detector가 중간에 ++ 한 만큼 initialStepCount가 왜곡되었을 수 있음
        onStepCounterEvent(bootSteps + 20)
        assertEquals(20, sessionSteps) // Counter가 덮어씌워서 20으로 보정됨

        // 하지만 detector가 또 발생하면...
        onStepDetectorEvent() // 21
        onStepDetectorEvent() // 22
        assertEquals(22, sessionSteps) // 실제로는 20걸음인데 22!

        // 이것이 이중 카운팅 버그 - DETECTOR 제거로 해결
    }

    @Test
    fun `세션 리셋 후 새 세션 시작`() {
        val bootSteps = 5000

        // 첫 세션: 50걸음
        onStepCounterEvent(bootSteps)
        onStepCounterEvent(bootSteps + 50)
        assertEquals(50, sessionSteps)

        // 세션 리셋
        initialStepCount = null
        sessionSteps = 0

        // 새 세션: 다시 0부터
        onStepCounterEvent(bootSteps + 50)  // 현재 총 걸음수에서 새 기준점 설정
        assertEquals(0, sessionSteps)

        onStepCounterEvent(bootSteps + 80)  // 새 세션에서 30걸음
        assertEquals(30, sessionSteps)
    }

    @Test
    fun `걸음수 0 - 세션 시작 직후`() {
        onStepCounterEvent(10000)
        assertEquals(0, sessionSteps)
    }

    @Test
    fun `대량 걸음수 - 마라톤 수준`() {
        val bootSteps = 1000
        onStepCounterEvent(bootSteps) // 기준점

        // 마라톤 = 약 42km, 걸음 당 0.75m → 약 56,000걸음
        onStepCounterEvent(bootSteps + 56000)
        assertEquals(56000, sessionSteps)
    }

    @Test
    fun `연속 이벤트 - 매 걸음마다`() {
        val bootSteps = 100
        onStepCounterEvent(bootSteps) // 기준점

        for (i in 1..100) {
            onStepCounterEvent(bootSteps + i)
            assertEquals(i, sessionSteps)
        }
    }

    // =============================================
    // 걸음 기반 거리 추정 검증
    // =============================================

    @Test
    fun `걸음 거리 변환 - 표준 보폭 0점75m`() {
        val stepLength = 0.75

        assertEquals(750.0, 1000 * stepLength, 0.01)   // 1000걸음 = 750m
        assertEquals(7500.0, 10000 * stepLength, 0.01)  // 10000걸음 = 7500m = 7.5km
        assertEquals(0.0, 0 * stepLength, 0.01)         // 0걸음 = 0m
    }

    @Test
    fun `걸음 기반 케이던스 - 분당 걸음수`() {
        // 10분 동안 1700걸음
        val totalSteps = 1700
        val elapsedSeconds = 600L // 10분

        val cadence = if (totalSteps > 0 && elapsedSeconds > 0) {
            (totalSteps.toDouble() / elapsedSeconds) * 60.0
        } else null

        assertNotNull(cadence)
        assertEquals(170.0, cadence!!, 0.01) // 170 spm (정상 러닝)
    }

    @Test
    fun `케이던스 범위 - 걷기 vs 러닝 vs 스프린트`() {
        // 걷기: ~100-120 spm
        val walkingCadence = (100.0 / 60.0) * 60.0
        assertTrue(walkingCadence in 90.0..130.0)

        // 러닝: ~160-180 spm
        val runningCadence = (170.0 / 60.0) * 60.0
        assertTrue(runningCadence in 150.0..190.0)

        // 스프린트: ~200+ spm
        val sprintCadence = (210.0 / 60.0) * 60.0
        assertTrue(sprintCadence >= 200.0)
    }
}
