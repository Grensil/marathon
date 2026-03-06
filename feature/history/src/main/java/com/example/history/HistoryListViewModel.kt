package com.example.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.domain.model.RunHistory
import com.example.healthcare.domain.repository.RunHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunStats(
    val totalRuns: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalDurationMs: Long = 0L
)

enum class SortOrder(val label: String) {
    RECENT("Latest"),
    LONGEST_DISTANCE("Distance"),
    FASTEST_PACE("Pace"),
    LONGEST_DURATION("Duration")
}

@HiltViewModel
class HistoryListViewModel @Inject constructor(
    private val runHistoryRepository: RunHistoryRepository
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<RunHistory>>(emptyList())
    val sessions: StateFlow<List<RunHistory>> = _sessions.asStateFlow()

    private val _stats = MutableStateFlow(RunStats())
    val stats: StateFlow<RunStats> = _stats.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.RECENT)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private var rawSessions: List<RunHistory> = emptyList()

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            runHistoryRepository.getAllSessions()
                .catch { e ->
                    Log.e(TAG, "세션 목록 로딩 실패", e)
                }
                .collect { sessionList ->
                    rawSessions = sessionList
                    _stats.value = RunStats(
                        totalRuns = sessionList.size,
                        totalDistanceKm = sessionList.sumOf { it.distanceMeters } / 1000.0,
                        totalDurationMs = sessionList.sumOf { it.durationMs }
                    )
                    applySortOrder()
                }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        applySortOrder()
    }

    private fun applySortOrder() {
        _sessions.value = when (_sortOrder.value) {
            SortOrder.RECENT -> rawSessions.sortedByDescending { it.startTime }
            SortOrder.LONGEST_DISTANCE -> rawSessions.sortedByDescending { it.distanceMeters }
            SortOrder.FASTEST_PACE -> rawSessions.sortedBy { parsePaceToSeconds(it.averagePace) }
            SortOrder.LONGEST_DURATION -> rawSessions.sortedByDescending { it.durationMs }
        }
    }

    private fun parsePaceToSeconds(pace: String): Int {
        val parts = pace.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val min = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val sec = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        if (min == 0 && sec == 0) return Int.MAX_VALUE
        return min * 60 + sec
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                runHistoryRepository.deleteSession(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "세션 삭제 실패: $sessionId", e)
            }
        }
    }

    companion object {
        private const val TAG = "HistoryListViewModel"
    }
}
