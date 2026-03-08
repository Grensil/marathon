package com.example.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.domain.model.ExerciseType
import com.example.healthcare.domain.model.RoutePoint
import com.example.healthcare.domain.model.RunHistory
import com.example.healthcare.domain.usecase.CheckHealthConnectPermissionsUseCase
import com.example.healthcare.domain.usecase.FinalizeRunSessionUseCase
import com.example.healthcare.domain.usecase.GetActiveSessionUseCase
import com.example.healthcare.domain.usecase.ObserveRunningMetricsUseCase
import com.example.healthcare.domain.usecase.SaveRunSessionUseCase
import com.example.healthcare.domain.usecase.StartRunningSessionUseCase
import com.example.healthcare.domain.usecase.StopRunningSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AudioEvent {
    data object RunStarted : AudioEvent()
    data object RunPaused : AudioEvent()
    data object RunResumed : AudioEvent()
    data class RunCompleted(val distanceKm: String, val elapsedTime: String) : AudioEvent()
    data class KilometerReached(val km: Int, val pace: String, val elapsedTime: String) : AudioEvent()
}

@HiltViewModel
class RunningViewModel @Inject constructor(
    private val startRunningSessionUseCase: StartRunningSessionUseCase,
    private val stopRunningSessionUseCase: StopRunningSessionUseCase,
    private val observeRunningMetricsUseCase: ObserveRunningMetricsUseCase,
    private val checkHealthConnectPermissionsUseCase: CheckHealthConnectPermissionsUseCase,
    private val getActiveSessionUseCase: GetActiveSessionUseCase,
    private val saveRunSessionUseCase: SaveRunSessionUseCase,
    private val finalizeRunSessionUseCase: FinalizeRunSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RunningState())
    val state: StateFlow<RunningState> = _state.asStateFlow()

    private val _audioEvents = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 8)
    val audioEvents: SharedFlow<AudioEvent> = _audioEvents.asSharedFlow()

    private var metricsJob: Job? = null
    private var timerJob: Job? = null
    private var sessionStartTime: Long = 0L
    private var pausedTime: Long = 0L
    private var pauseStartTime: Long = 0L

    private val heartRateList = mutableListOf<Int>()
    private val paceList = mutableListOf<Double>()
    private val cadenceList = mutableListOf<Double>()
    private val routePoints = mutableListOf<LocationPoint>()
    private var locationPointIndex = 0
    private var lastAnnouncedKm = 0
    private var maxHeartRate: Int? = null
    private var bestPace: Double? = null
    private var totalStepCount: Int = 0

    companion object {
        private const val TAG = "Logd"
    }

    init {
        checkPermissions()
        checkActiveSession()
    }

    fun onPermissionGranted() {
        _state.update { it.copy(hasPermissions = true) }
    }

    private fun checkPermissions() {
        viewModelScope.launch {
            val hasPermissions = checkHealthConnectPermissionsUseCase()
            _state.update { it.copy(hasPermissions = hasPermissions) }
        }
    }

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

    fun startRunning() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val result = startRunningSessionUseCase(ExerciseType.RUNNING)

            result.onSuccess { sessionId ->
                sessionStartTime = System.currentTimeMillis()
                pausedTime = 0L
                lastAnnouncedKm = 0

                heartRateList.clear()
                paceList.clear()
                cadenceList.clear()
                routePoints.clear()
                locationPointIndex = 0
                maxHeartRate = null
                bestPace = null
                totalStepCount = 0

                val runHistory = RunHistory(
                    id = sessionId,
                    startTime = sessionStartTime
                )
                saveRunSessionUseCase(runHistory)

                _state.update {
                    it.copy(
                        isRunning = true,
                        sessionId = sessionId,
                        isLoading = false,
                        elapsedTime = 0L,
                        distance = 0.0,
                        currentPace = "--:--",
                        averagePace = "--:--",
                        currentHeartRate = null,
                        averageHeartRate = null,
                        currentCadence = null,
                        averageCadence = null,
                        currentAltitude = null,
                        calories = 0,
                        routePoints = persistentListOf()
                    )
                }

                _audioEvents.tryEmit(AudioEvent.RunStarted)
                startMetricsObservation()
                startTimer()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to start: ${error.message}"
                    )
                }
            }
        }
    }

    fun pauseRunning() {
        metricsJob?.cancel()
        timerJob?.cancel()
        pauseStartTime = System.currentTimeMillis()
        _state.update { it.copy(isPaused = true) }
        _audioEvents.tryEmit(AudioEvent.RunPaused)
    }

    fun resumeRunning() {
        if (pauseStartTime > 0) {
            pausedTime += System.currentTimeMillis() - pauseStartTime
            pauseStartTime = 0L
        }
        _state.update { it.copy(isPaused = false) }
        _audioEvents.tryEmit(AudioEvent.RunResumed)
        startMetricsObservation()
        startTimer()
    }

    fun stopRunning() {
        viewModelScope.launch {
            val sessionId = _state.value.sessionId ?: return@launch

            // Cancel jobs FIRST to prevent race condition on state
            metricsJob?.cancel()
            timerJob?.cancel()

            val currentState = _state.value

            _state.update { it.copy(isLoading = true) }

            stopRunningSessionUseCase(sessionId)
                .onSuccess {
                    val record = RunningRecord(
                        sessionId = sessionId,
                        timestamp = System.currentTimeMillis(),
                        elapsedTime = currentState.elapsedTime,
                        distance = currentState.distance,
                        averagePace = currentState.averagePace,
                        averageCadence = currentState.averageCadence,
                        averageHeartRate = currentState.averageHeartRate,
                        calories = currentState.calories
                    )

                    val runHistory = RunHistory(
                        id = sessionId,
                        startTime = sessionStartTime,
                        endTime = System.currentTimeMillis(),
                        durationMs = currentState.elapsedTime,
                        distanceMeters = currentState.distance,
                        averagePace = currentState.averagePace,
                        averageHeartRate = currentState.averageHeartRate,
                        averageCadence = currentState.averageCadence,
                        calories = currentState.calories,
                        maxHeartRate = maxHeartRate,
                        maxPace = bestPace?.let { formatPace(it) },
                        totalSteps = totalStepCount
                    )
                    val domainPoints = routePoints.mapIndexed { index, point ->
                        RoutePoint(
                            latitude = point.latitude,
                            longitude = point.longitude,
                            altitude = point.altitude,
                            timestamp = point.timestamp,
                            orderIndex = index
                        )
                    }
                    finalizeRunSessionUseCase(runHistory, domainPoints)

                    heartRateList.clear()
                    paceList.clear()
                    cadenceList.clear()
                    routePoints.clear()
                    maxHeartRate = null
                    bestPace = null
                    totalStepCount = 0

                    val distanceKm = formatDistance(currentState.distance)
                    val elapsedTimeStr = formatElapsedTime(currentState.elapsedTime)
                    _audioEvents.tryEmit(AudioEvent.RunCompleted(distanceKm, elapsedTimeStr))

                    _state.update {
                        RunningState(
                            hasPermissions = it.hasPermissions,
                            isLoading = false,
                            showCompletionDialog = true,
                            completedRecord = record
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

    fun dismissCompletionDialog() {
        _state.update { it.copy(showCompletionDialog = false, completedRecord = null) }
    }

    private fun startMetricsObservation() {
        metricsJob?.cancel()
        metricsJob = viewModelScope.launch {
            observeRunningMetricsUseCase()
                .catch { e ->
                    _state.update {
                        it.copy(error = e.message ?: "Failed to observe metrics")
                    }
                }
                .collect { metrics ->
                    metrics.heartRate?.let { hr ->
                        heartRateList.add(hr)
                        if (maxHeartRate == null || hr > maxHeartRate!!) {
                            maxHeartRate = hr
                        }
                    }

                    metrics.pace?.let { p ->
                        if (p in 1.0..30.0) {
                            paceList.add(p)
                            if (bestPace == null || p < bestPace!!) {
                                bestPace = p
                            }
                        }
                    }

                    metrics.cadence?.let { c ->
                        cadenceList.add(c)
                        totalStepCount = (metrics.distance?.let {
                            // Steps from metrics if available
                            (it / 0.75).toInt()
                        }) ?: totalStepCount
                    }

                    val lat = metrics.latitude
                    val lon = metrics.longitude
                    if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                        val point = LocationPoint(
                            latitude = lat,
                            longitude = lon,
                            altitude = metrics.altitude,
                            timestamp = metrics.timestamp
                        )
                        routePoints.add(point)
                        locationPointIndex++
                    }

                    val currentPace = metrics.pace?.let { formatPace(it) } ?: "--:--"
                    val currentDistance = metrics.distance ?: 0.0
                    val avgPace = if (paceList.isNotEmpty() && currentDistance > 50.0) {
                        formatPace(paceList.average())
                    } else "--:--"
                    val avgCadence = if (cadenceList.isNotEmpty()) {
                        cadenceList.average().toInt()
                    } else null
                    val avgHeartRate = if (heartRateList.isNotEmpty()) {
                        heartRateList.average().toInt()
                    } else null

                    val distanceKm = (metrics.distance ?: 0.0) / 1000.0
                    val estimatedCalories = (distanceKm * 70).toInt()

                    // Km milestone detection
                    val currentKm = distanceKm.toInt()
                    if (currentKm > lastAnnouncedKm && currentKm > 0) {
                        lastAnnouncedKm = currentKm
                        val elapsed = _state.value.elapsedTime
                        _audioEvents.tryEmit(
                            AudioEvent.KilometerReached(
                                km = currentKm,
                                pace = avgPace,
                                elapsedTime = formatElapsedTime(elapsed)
                            )
                        )
                    }

                    _state.update { state ->
                        state.copy(
                            distance = metrics.distance ?: 0.0,
                            currentHeartRate = metrics.heartRate,
                            averageHeartRate = avgHeartRate,
                            currentPace = currentPace,
                            averagePace = avgPace,
                            currentCadence = metrics.cadence?.toInt(),
                            averageCadence = avgCadence,
                            currentAltitude = metrics.altitude,
                            calories = estimatedCalories,
                            currentLatitude = metrics.latitude,
                            currentLongitude = metrics.longitude,
                            routePoints = routePoints.toImmutableList()
                        )
                    }
                }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - sessionStartTime - pausedTime
                _state.update { it.copy(elapsedTime = elapsed) }
                delay(1000)
            }
        }
    }

    private fun formatPace(paceInMinutesPerKm: Double): String {
        if (paceInMinutesPerKm > 30 || paceInMinutesPerKm < 0) return "--:--"
        val minutes = paceInMinutesPerKm.toInt()
        val seconds = ((paceInMinutesPerKm - minutes) * 60).toInt()
        return String.format("%d:%02d", minutes, seconds)
    }

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
