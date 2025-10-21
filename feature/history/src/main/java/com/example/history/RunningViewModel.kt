package com.example.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.domain.model.ExerciseType
import com.example.healthcare.domain.usecase.CheckHealthConnectPermissionsUseCase
import com.example.healthcare.domain.usecase.GetActiveSessionUseCase
import com.example.healthcare.domain.usecase.ObserveRunningMetricsUseCase
import com.example.healthcare.domain.usecase.StartRunningSessionUseCase
import com.example.healthcare.domain.usecase.StopRunningSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 러닝 화면 ViewModel
 */
@HiltViewModel
class RunningViewModel @Inject constructor(
    private val startRunningSessionUseCase: StartRunningSessionUseCase,
    private val stopRunningSessionUseCase: StopRunningSessionUseCase,
    private val observeRunningMetricsUseCase: ObserveRunningMetricsUseCase,
    private val checkHealthConnectPermissionsUseCase: CheckHealthConnectPermissionsUseCase,
    private val getActiveSessionUseCase: GetActiveSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RunningState())
    val state: StateFlow<RunningState> = _state.asStateFlow()

    private var metricsJob: Job? = null
    private var timerJob: Job? = null
    private var sessionStartTime: Long = 0L
    private var pausedTime: Long = 0L // 일시정지된 총 시간

    // 실시간 메트릭 누적용
    private val heartRateList = mutableListOf<Int>()
    private val paceList = mutableListOf<Double>()
    private val cadenceList = mutableListOf<Double>()

    // 기록 리스트
    private val recordsList = mutableListOf<RunningRecord>()

    companion object {
        private const val TAG = "Logd"
    }

    init {
        checkPermissions()
        checkActiveSession()
    }

    /**
     * 권한이 부여되었을 때 호출
     */
    fun onPermissionGranted() {
        _state.update { it.copy(hasPermissions = true) }
    }

    /**
     * 권한 확인
     */
    private fun checkPermissions() {
        viewModelScope.launch {
            val hasPermissions = checkHealthConnectPermissionsUseCase()
            _state.update { it.copy(hasPermissions = hasPermissions) }
        }
    }

    /**
     * 활성 세션 확인
     */
    private fun checkActiveSession() {
        viewModelScope.launch {
            getActiveSessionUseCase().onSuccess { session ->
                if (session != null && session.isActive) {
                    _state.update {
                        it.copy(
                            isRunning = true,
                            sessionId = session.sessionId
                        )
                    }
                    sessionStartTime = session.startTime
                    startMetricsObservation()
                    startTimer()
                }
            }
        }
    }

    /**
     * 러닝 시작
     */
    fun startRunning() {
        Log.d(TAG, "startRunning called")
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val result = startRunningSessionUseCase(ExerciseType.RUNNING)
            Log.d(TAG, "startRunningSessionUseCase result: isSuccess=${result.isSuccess}, isFailure=${result.isFailure}")

            result.onSuccess { sessionId ->
                Log.d(TAG, "Session started successfully: $sessionId")
                sessionStartTime = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        isRunning = true,
                        sessionId = sessionId,
                        isLoading = false,
                        elapsedTime = 0L
                    )
                }

                // 메트릭 관찰 시작
                startMetricsObservation()

                // 타이머 시작
                startTimer()
            }.onFailure { error ->
                Log.d(TAG, "Failed to start session: ${error.message}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to start: ${error.message}"
                    )
                }
            }
        }
    }

    /**
     * 러닝 일시정지
     */
    fun pauseRunning() {
        Log.d(TAG, "pauseRunning called")
        metricsJob?.cancel()
        timerJob?.cancel()

        _state.update { it.copy(isPaused = true) }
        Log.d(TAG, "Running paused")
    }

    /**
     * 러닝 재개
     */
    fun resumeRunning() {
        Log.d(TAG, "resumeRunning called")
        _state.update { it.copy(isPaused = false) }

        // 메트릭 관찰 재시작
        startMetricsObservation()

        // 타이머 재시작
        startTimer()

        Log.d(TAG, "Running resumed")
    }

    /**
     * 러닝 완전 중지 (기록 저장)
     */
    fun stopRunning() {
        viewModelScope.launch {
            val sessionId = _state.value.sessionId ?: return@launch
            val currentState = _state.value

            _state.update { it.copy(isLoading = true) }

            stopRunningSessionUseCase(sessionId)
                .onSuccess {
                    metricsJob?.cancel()
                    timerJob?.cancel()

                    // 기록 생성
                    val record = RunningRecord(
                        timestamp = System.currentTimeMillis(),
                        elapsedTime = currentState.elapsedTime,
                        distance = currentState.distance,
                        averagePace = currentState.averagePace,
                        averageCadence = currentState.averageCadence
                    )

                    // 리스트에 추가
                    recordsList.add(record)
                    Log.d(TAG, "Record saved: $record")
                    Log.d(TAG, "Total records: ${recordsList.size}")

                    // 리스트 초기화
                    heartRateList.clear()
                    paceList.clear()
                    cadenceList.clear()

                    _state.update {
                        RunningState(
                            hasPermissions = it.hasPermissions,
                            isLoading = false,
                            showCompletionDialog = true,
                            completedRecord = record,
                            records = recordsList.toList()
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to stop running session"
                        )
                    }
                }
        }
    }

    /**
     * 완료 다이얼로그 닫기
     */
    fun dismissCompletionDialog() {
        _state.update { it.copy(showCompletionDialog = false, completedRecord = null) }
    }

    /**
     * 메트릭 관찰 시작
     */
    private fun startMetricsObservation() {
        Log.d(TAG, "startMetricsObservation called")
        metricsJob?.cancel()
        metricsJob = viewModelScope.launch {
            observeRunningMetricsUseCase()
                .catch { e ->
                    Log.d(TAG, "Error observing metrics: ${e.message}")
                    _state.update {
                        it.copy(error = e.message ?: "Failed to observe metrics")
                    }
                }
                .collect { metrics ->
                    Log.d(TAG, "Received metrics in ViewModel: distance=${metrics.distance}, heartRate=${metrics.heartRate}")

                    // 심박수
                    metrics.heartRate?.let { hr ->
                        heartRateList.add(hr)
                    }

                    // 페이스
                    metrics.pace?.let { p ->
                        paceList.add(p)
                    }

                    // 케이던스
                    metrics.cadence?.let { c ->
                        cadenceList.add(c)
                    }

                    // 페이스 리스트 관리 (평균 계산용)
                    metrics.pace?.let { p ->
                        paceList.add(p)
                    }

                    // Current Pace: Repository의 pace (속도 기반 즉시 계산)
                    val currentPace = metrics.pace?.let { formatPace(it) } ?: "--:--"

                    // Average Pace: 누적된 페이스들의 평균
                    val avgPace = if (paceList.isNotEmpty()) {
                        formatPace(paceList.average())
                    } else "--:--"

                    // Cadence: Repository가 이미 평균을 계산함
                    val avgCadence = metrics.cadence?.toInt()

                    Log.d(TAG, "Current pace: ${metrics.pace}, cadence: ${metrics.cadence}")
                    Log.d(TAG, "paceList size: ${paceList.size}, avgPace: $avgPace")
                    Log.d(TAG, "Formatted - currentPace: $currentPace, avgPace: $avgPace")

                    _state.update { state ->
                        state.copy(
                            distance = metrics.distance ?: 0.0,
                            currentHeartRate = metrics.heartRate,
                            averageHeartRate = metrics.heartRate,
                            currentPace = currentPace,
                            averagePace = avgPace,
                            currentCadence = metrics.cadence?.toInt(),
                            averageCadence = avgCadence,
                            currentAltitude = metrics.altitude
                        )
                    }
                    Log.d(TAG, "State updated: currentPace=${_state.value.currentPace}, avgPace=${_state.value.averagePace}, cadence=${_state.value.currentCadence}")
                }
        }
    }

    /**
     * 타이머 시작
     */
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - sessionStartTime
                _state.update { it.copy(elapsedTime = elapsed) }
                delay(1000) // 1초마다 업데이트
            }
        }
    }

    /**
     * 페이스를 MM:SS 형식으로 포맷
     */
    private fun formatPace(paceInMinutesPerKm: Double): String {
        val minutes = paceInMinutesPerKm.toInt()
        val seconds = ((paceInMinutesPerKm - minutes) * 60).toInt()
        return String.format("%d:%02d", minutes, seconds)
    }

    /**
     * 시간을 HH:MM:SS 형식으로 포맷
     */
    fun formatElapsedTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60))

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 거리를 포맷 (km)
     */
    fun formatDistance(meters: Double): String {
        val km = meters / 1000.0
        return String.format("%.2f", km)
    }

    override fun onCleared() {
        super.onCleared()
        metricsJob?.cancel()
        timerJob?.cancel()
    }
}
