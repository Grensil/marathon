package com.example.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.local.entity.LocationPointEntity
import com.example.healthcare.data.local.entity.RunningSessionEntity
import com.example.healthcare.domain.repository.RunHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunDetailState(
    val session: RunningSessionEntity? = null,
    val locationPoints: List<LocationPointEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class RunDetailViewModel @Inject constructor(
    private val runHistoryRepository: RunHistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RunDetailState())
    val state: StateFlow<RunDetailState> = _state.asStateFlow()

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            _state.value = RunDetailState(isLoading = true)

            val session = runHistoryRepository.getSessionById(sessionId)
            val locationPoints = runHistoryRepository.getLocationPoints(sessionId)

            _state.value = RunDetailState(
                session = session,
                locationPoints = locationPoints,
                isLoading = false
            )
        }
    }
}
