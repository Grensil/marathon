package com.example.history

import androidx.lifecycle.viewModelScope
import com.example.healthcare.domain.model.RunningMetrics
import com.example.healthcare.domain.model.RunningSession
import com.example.healthcare.domain.usecase.CheckHealthConnectPermissionsUseCase
import com.example.healthcare.domain.usecase.FinalizeRunSessionUseCase
import com.example.healthcare.domain.usecase.GetActiveSessionUseCase
import com.example.healthcare.domain.usecase.ObserveRunningMetricsUseCase
import com.example.healthcare.domain.usecase.SaveRunSessionUseCase
import com.example.healthcare.domain.usecase.StartRunningSessionUseCase
import com.example.healthcare.domain.usecase.StopRunningSessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunningViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var startRunningSessionUseCase: StartRunningSessionUseCase
    private lateinit var stopRunningSessionUseCase: StopRunningSessionUseCase
    private lateinit var observeRunningMetricsUseCase: ObserveRunningMetricsUseCase
    private lateinit var checkHealthConnectPermissionsUseCase: CheckHealthConnectPermissionsUseCase
    private lateinit var getActiveSessionUseCase: GetActiveSessionUseCase
    private lateinit var saveRunSessionUseCase: SaveRunSessionUseCase
    private lateinit var finalizeRunSessionUseCase: FinalizeRunSessionUseCase

    private lateinit var metricsFlow: MutableSharedFlow<RunningMetrics>

    @Before
    fun setup() {
        startRunningSessionUseCase = mockk()
        stopRunningSessionUseCase = mockk()
        observeRunningMetricsUseCase = mockk()
        checkHealthConnectPermissionsUseCase = mockk()
        getActiveSessionUseCase = mockk()
        saveRunSessionUseCase = mockk(relaxed = true)
        finalizeRunSessionUseCase = mockk(relaxed = true)

        metricsFlow = MutableSharedFlow()

        coEvery { checkHealthConnectPermissionsUseCase() } returns true
        coEvery { getActiveSessionUseCase() } returns Result.success(null)
        every { observeRunningMetricsUseCase() } returns metricsFlow
    }

    private fun createViewModel(): RunningViewModel {
        return RunningViewModel(
            startRunningSessionUseCase, stopRunningSessionUseCase,
            observeRunningMetricsUseCase, checkHealthConnectPermissionsUseCase,
            getActiveSessionUseCase, saveRunSessionUseCase, finalizeRunSessionUseCase
        )
    }

    // --- 초기 상태 ---

    @Test
    fun `초기 상태에서 러닝 중이 아님`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isPaused)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `초기화 시 권한 확인 - true`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()
        assertTrue(vm.state.value.hasPermissions)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `권한이 없으면 hasPermissions가 false`() = runTest(testDispatcher) {
        coEvery { checkHealthConnectPermissionsUseCase() } returns false
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()
        assertFalse(vm.state.value.hasPermissions)
        vm.viewModelScope.cancel()
    }

    // --- 러닝 시작 ---

    @Test
    fun `startRunning 성공 시 isRunning이 true`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        assertTrue(vm.state.value.isRunning)
        assertEquals("session_123", vm.state.value.sessionId)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `startRunning 성공 시 RunStarted 오디오 이벤트 발생`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        val events = mutableListOf<AudioEvent>()
        val job = backgroundScope.launch { vm.audioEvents.collect { events.add(it) } }

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        assertTrue(events.any { it is AudioEvent.RunStarted })
        job.cancel()
        vm.viewModelScope.cancel()
    }

    @Test
    fun `startRunning 성공 시 RunHistory가 저장됨`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        coVerify { saveRunSessionUseCase(match { it.id == "session_123" }) }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `startRunning 실패 시 error 상태 설정`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.failure(RuntimeException("Sensor error"))
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        assertFalse(vm.state.value.isRunning)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Sensor error"))
        vm.viewModelScope.cancel()
    }

    // --- 일시정지/재개 ---

    @Test
    fun `pauseRunning 시 isPaused가 true`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        vm.pauseRunning()
        advanceTimeBy(100)
        runCurrent()

        assertTrue(vm.state.value.isPaused)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `pauseRunning 시 RunPaused 이벤트 발생`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        val events = mutableListOf<AudioEvent>()
        val job = backgroundScope.launch { vm.audioEvents.collect { events.add(it) } }

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        vm.pauseRunning()
        advanceTimeBy(100)
        runCurrent()

        assertTrue(events.any { it is AudioEvent.RunPaused })
        job.cancel()
        vm.viewModelScope.cancel()
    }

    @Test
    fun `resumeRunning 시 isPaused가 false`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        vm.pauseRunning()
        advanceTimeBy(100)
        runCurrent()
        assertTrue(vm.state.value.isPaused)

        vm.resumeRunning()
        advanceTimeBy(100)
        runCurrent()
        assertFalse(vm.state.value.isPaused)
        vm.viewModelScope.cancel()
    }

    // --- 러닝 종료 ---

    @Test
    fun `stopRunning 성공 시 완료 다이얼로그 표시`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        coEvery { stopRunningSessionUseCase("session_123") } returns Result.success(Unit)
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        vm.stopRunning()
        advanceTimeBy(100)
        runCurrent()

        assertTrue(vm.state.value.showCompletionDialog)
        assertNotNull(vm.state.value.completedRecord)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `stopRunning 성공 시 RunCompleted 이벤트 발생`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        coEvery { stopRunningSessionUseCase("session_123") } returns Result.success(Unit)
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        val events = mutableListOf<AudioEvent>()
        val job = backgroundScope.launch { vm.audioEvents.collect { events.add(it) } }

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        vm.stopRunning()
        advanceTimeBy(100)
        runCurrent()

        assertTrue(events.any { it is AudioEvent.RunCompleted })
        job.cancel()
        vm.viewModelScope.cancel()
    }

    @Test
    fun `stopRunning 성공 시 RunHistory가 업데이트됨`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        coEvery { stopRunningSessionUseCase("session_123") } returns Result.success(Unit)
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        vm.stopRunning()
        advanceTimeBy(100)
        runCurrent()

        coVerify { finalizeRunSessionUseCase(match { it.id == "session_123" }, any()) }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `stopRunning 실패 시 error 상태 설정`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        coEvery { stopRunningSessionUseCase("session_123") } returns Result.failure(RuntimeException("Stop failed"))
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        vm.stopRunning()
        advanceTimeBy(100)
        runCurrent()

        assertNotNull(vm.state.value.error)
        vm.viewModelScope.cancel()
    }

    // --- 완료 다이얼로그 ---

    @Test
    fun `dismissCompletionDialog 호출 시 다이얼로그 닫힘`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        coEvery { stopRunningSessionUseCase("session_123") } returns Result.success(Unit)
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        vm.stopRunning()
        advanceTimeBy(100)
        runCurrent()

        vm.dismissCompletionDialog()
        assertFalse(vm.state.value.showCompletionDialog)
        assertNull(vm.state.value.completedRecord)
        vm.viewModelScope.cancel()
    }

    // --- 메트릭 수신 ---

    @Test
    fun `메트릭 수신 시 거리 업데이트`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        metricsFlow.emit(RunningMetrics(timestamp = 1L, distance = 1500.0, speed = 3.0, pace = 5.56))
        advanceTimeBy(100)
        runCurrent()

        assertEquals(1500.0, vm.state.value.distance, 0.1)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `메트릭에서 심박수 업데이트`() = runTest(testDispatcher) {
        coEvery { startRunningSessionUseCase(any()) } returns Result.success("session_123")
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        vm.startRunning()
        advanceTimeBy(100)
        runCurrent()

        metricsFlow.emit(RunningMetrics(timestamp = 1L, heartRate = 155))
        advanceTimeBy(100)
        runCurrent()

        assertEquals(155, vm.state.value.currentHeartRate)
        vm.viewModelScope.cancel()
    }

    // --- 활성 세션 복원 ---

    @Test
    fun `초기화 시 활성 세션이 있으면 자동 복원`() = runTest(testDispatcher) {
        val activeSession = RunningSession(
            sessionId = "active_session",
            startTime = System.currentTimeMillis() - 60000,
            isActive = true
        )
        coEvery { getActiveSessionUseCase() } returns Result.success(activeSession)
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()

        assertTrue(vm.state.value.isRunning)
        assertEquals("active_session", vm.state.value.sessionId)
        vm.viewModelScope.cancel()
    }

    // --- 포맷팅 ---

    @Test
    fun `formatElapsedTime 정상 동작`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()
        assertEquals("00:00", vm.formatElapsedTime(0))
        assertEquals("05:30", vm.formatElapsedTime(330_000))
        assertEquals("1:00:00", vm.formatElapsedTime(3600_000))
        vm.viewModelScope.cancel()
    }

    @Test
    fun `formatDistance 정상 동작`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()
        assertEquals("0.00", vm.formatDistance(0.0))
        assertEquals("5.00", vm.formatDistance(5000.0))
        assertEquals("42.20", vm.formatDistance(42195.0))
        vm.viewModelScope.cancel()
    }

    // --- onPermissionGranted ---

    @Test
    fun `onPermissionGranted 호출 시 hasPermissions가 true`() = runTest(testDispatcher) {
        coEvery { checkHealthConnectPermissionsUseCase() } returns false
        val vm = createViewModel()
        advanceTimeBy(100)
        runCurrent()
        assertFalse(vm.state.value.hasPermissions)

        vm.onPermissionGranted()
        assertTrue(vm.state.value.hasPermissions)
        vm.viewModelScope.cancel()
    }
}
