package com.example.history

import com.example.healthcare.domain.model.RunHistory
import com.example.healthcare.domain.repository.RunHistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: RunHistoryRepository
    private lateinit var sessionsFlow: MutableSharedFlow<List<RunHistory>>

    private val sampleSessions = listOf(
        RunHistory(id = "session_1", startTime = 1000L, durationMs = 1800_000L, distanceMeters = 5000.0, averagePace = "6:00"),
        RunHistory(id = "session_2", startTime = 2000L, durationMs = 3600_000L, distanceMeters = 10000.0, averagePace = "5:30"),
        RunHistory(id = "session_3", startTime = 3000L, durationMs = 900_000L, distanceMeters = 2000.0, averagePace = "7:00")
    )

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        sessionsFlow = MutableSharedFlow()
        coEvery { repository.getAllSessions() } returns sessionsFlow
    }

    private fun createViewModel() = HistoryListViewModel(repository)

    @Test
    fun `초기 상태에서 sessions는 빈 리스트`() = runTest {
        val viewModel = createViewModel()
        assertEquals(emptyList<RunHistory>(), viewModel.sessions.value)
    }

    @Test
    fun `초기 sortOrder는 RECENT`() = runTest {
        val viewModel = createViewModel()
        assertEquals(SortOrder.RECENT, viewModel.sortOrder.value)
    }

    @Test
    fun `세션 목록 로딩 시 stats가 올바르게 계산됨`() = runTest {
        val viewModel = createViewModel()
        sessionsFlow.emit(sampleSessions)

        val stats = viewModel.stats.value
        assertEquals(3, stats.totalRuns)
        assertEquals(17.0, stats.totalDistanceKm, 0.001)
        assertEquals(6300_000L, stats.totalDurationMs)
    }

    @Test
    fun `빈 세션 목록에서 stats는 0`() = runTest {
        val viewModel = createViewModel()
        sessionsFlow.emit(emptyList())

        val stats = viewModel.stats.value
        assertEquals(0, stats.totalRuns)
        assertEquals(0.0, stats.totalDistanceKm, 0.001)
        assertEquals(0L, stats.totalDurationMs)
    }

    @Test
    fun `RECENT 정렬은 startTime 내림차순`() = runTest {
        val viewModel = createViewModel()
        sessionsFlow.emit(sampleSessions)

        val sessions = viewModel.sessions.value
        assertEquals("session_3", sessions[0].id)
        assertEquals("session_2", sessions[1].id)
        assertEquals("session_1", sessions[2].id)
    }

    @Test
    fun `LONGEST_DISTANCE 정렬은 distanceMeters 내림차순`() = runTest {
        val viewModel = createViewModel()
        sessionsFlow.emit(sampleSessions)
        viewModel.setSortOrder(SortOrder.LONGEST_DISTANCE)

        val sessions = viewModel.sessions.value
        assertEquals("session_2", sessions[0].id)
        assertEquals("session_1", sessions[1].id)
        assertEquals("session_3", sessions[2].id)
    }

    @Test
    fun `FASTEST_PACE 정렬은 pace 오름차순`() = runTest {
        val viewModel = createViewModel()
        sessionsFlow.emit(sampleSessions)
        viewModel.setSortOrder(SortOrder.FASTEST_PACE)

        val sessions = viewModel.sessions.value
        assertEquals("session_2", sessions[0].id)
        assertEquals("session_1", sessions[1].id)
        assertEquals("session_3", sessions[2].id)
    }

    @Test
    fun `LONGEST_DURATION 정렬은 durationMs 내림차순`() = runTest {
        val viewModel = createViewModel()
        sessionsFlow.emit(sampleSessions)
        viewModel.setSortOrder(SortOrder.LONGEST_DURATION)

        val sessions = viewModel.sessions.value
        assertEquals("session_2", sessions[0].id)
        assertEquals("session_1", sessions[1].id)
        assertEquals("session_3", sessions[2].id)
    }

    @Test
    fun `pace가 없는 세션은 FASTEST_PACE 정렬 시 마지막으로`() = runTest {
        val viewModel = createViewModel()
        sessionsFlow.emit(listOf(
            RunHistory(id = "fast", startTime = 1000L, averagePace = "5:00"),
            RunHistory(id = "no_pace", startTime = 2000L, averagePace = "--:--"),
            RunHistory(id = "slow", startTime = 3000L, averagePace = "8:00")
        ))
        viewModel.setSortOrder(SortOrder.FASTEST_PACE)

        val sessions = viewModel.sessions.value
        assertEquals("fast", sessions[0].id)
        assertEquals("slow", sessions[1].id)
        assertEquals("no_pace", sessions[2].id)
    }

    @Test
    fun `deleteSession은 repository의 deleteSession을 호출`() = runTest {
        val viewModel = createViewModel()
        viewModel.deleteSession("session_1")
        coVerify { repository.deleteSession("session_1") }
    }

    @Test
    fun `deleteSession 호출 후에도 sessions 상태 유지`() = runTest {
        val viewModel = createViewModel()
        sessionsFlow.emit(sampleSessions)

        viewModel.deleteSession("session_1")

        assertEquals(3, viewModel.stats.value.totalRuns)
        coVerify { repository.deleteSession("session_1") }
    }
}
