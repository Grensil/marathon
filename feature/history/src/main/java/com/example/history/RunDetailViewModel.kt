package com.example.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.domain.model.RoutePoint
import com.example.healthcare.domain.model.RunHistory
import com.example.healthcare.domain.repository.RunHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class RunDetailState(
    val session: RunHistory? = null,
    val locationPoints: ImmutableList<RoutePoint> = persistentListOf(),
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
                locationPoints = locationPoints.toImmutableList(),
                isLoading = false
            )
        }
    }
}
