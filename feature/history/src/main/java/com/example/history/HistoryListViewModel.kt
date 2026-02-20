package com.example.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.domain.model.RunHistory
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

    private val _sessions = MutableStateFlow<List<RunHistory>>(emptyList())
    val sessions: StateFlow<List<RunHistory>> = _sessions.asStateFlow()

    private val _stats = MutableStateFlow(RunStats())
    val stats: StateFlow<RunStats> = _stats.asStateFlow()

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            runHistoryRepository.getAllSessions().collect { sessionList ->
                _sessions.value = sessionList
                _stats.value = RunStats(
                    totalRuns = sessionList.size,
                    totalDistanceKm = sessionList.sumOf { it.distanceMeters } / 1000.0,
                    totalDurationMs = sessionList.sumOf { it.durationMs }
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            runHistoryRepository.deleteSession(sessionId)
        }
    }
}
