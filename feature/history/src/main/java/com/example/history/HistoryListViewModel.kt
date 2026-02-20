package com.example.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.local.entity.RunningSessionEntity
import com.example.healthcare.domain.repository.RunHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunStats(
    val totalRuns: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalDurationMs: Long = 0L
)

@HiltViewModel
class HistoryListViewModel @Inject constructor(
    private val runHistoryRepository: RunHistoryRepository
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<RunningSessionEntity>>(emptyList())
    val sessions: StateFlow<List<RunningSessionEntity>> = _sessions.asStateFlow()

    private val _stats = MutableStateFlow(RunStats())
    val stats: StateFlow<RunStats> = _stats.asStateFlow()

    init {
        loadSessions()
        loadStats()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            runHistoryRepository.getAllSessions().collect { sessionList ->
                _sessions.value = sessionList
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val totalRuns = runHistoryRepository.getSessionCount()
            val totalDistance = runHistoryRepository.getTotalDistance()
            val totalDuration = runHistoryRepository.getTotalDuration()
            _stats.value = RunStats(
                totalRuns = totalRuns,
                totalDistanceKm = totalDistance / 1000.0,
                totalDurationMs = totalDuration
            )
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            runHistoryRepository.deleteSession(sessionId)
            loadStats()
        }
    }
}
