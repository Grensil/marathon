package com.example.history

import com.example.healthcare.domain.model.RoutePoint
import com.example.healthcare.domain.model.RunHistory
import com.example.healthcare.domain.repository.RunHistoryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: RunHistoryRepository
    private lateinit var viewModel: RunDetailViewModel

    private val sampleSession = RunHistory(
        id = "session_123", startTime = 1000L, endTime = 2000L,
        durationMs = 1000L, distanceMeters = 5000.0, averagePace = "5:30",
        averageHeartRate = 150, calories = 350
    )
    private val sampleRoutePoints = listOf(
        RoutePoint(latitude = 37.5665, longitude = 126.9780, timestamp = 1000L, orderIndex = 0),
        RoutePoint(latitude = 37.5670, longitude = 126.9785, timestamp = 1500L, orderIndex = 1),
        RoutePoint(latitude = 37.5675, longitude = 126.9790, timestamp = 2000L, orderIndex = 2)
    )

    @Before
    fun setup() {
        repository = mockk()
        viewModel = RunDetailViewModel(repository)
    }

    @Test
    fun `초기 상태는 로딩 중`() {
        val state = viewModel.state.value
        assertTrue(state.isLoading)
        assertNull(state.session)
        assertTrue(state.locationPoints.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `loadSession 성공 시 세션과 경로가 로드됨`() = runTest {
        coEvery { repository.getSessionById("session_123") } returns sampleSession
        coEvery { repository.getLocationPoints("session_123") } returns sampleRoutePoints

        viewModel.loadSession("session_123")

        val state = viewModel.state.value
        assertNotNull(state.session)
        assertEquals("session_123", state.session?.id)
        assertEquals(3, state.locationPoints.size)
        assertEquals(false, state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `존재하지 않는 세션 로딩 시 session이 null`() = runTest {
        coEvery { repository.getSessionById("unknown") } returns null
        coEvery { repository.getLocationPoints("unknown") } returns emptyList()

        viewModel.loadSession("unknown")

        val state = viewModel.state.value
        assertNull(state.session)
        assertTrue(state.locationPoints.isEmpty())
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `loadSession 실패 시 error 상태가 설정됨`() = runTest {
        coEvery { repository.getSessionById(any()) } throws RuntimeException("DB error")
        coEvery { repository.getLocationPoints(any()) } throws RuntimeException("DB error")

        viewModel.loadSession("session_123")

        val state = viewModel.state.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun `경로 데이터가 없는 세션도 정상 로딩`() = runTest {
        coEvery { repository.getSessionById("session_123") } returns sampleSession
        coEvery { repository.getLocationPoints("session_123") } returns emptyList()

        viewModel.loadSession("session_123")

        val state = viewModel.state.value
        assertNotNull(state.session)
        assertTrue(state.locationPoints.isEmpty())
        assertEquals(false, state.isLoading)
    }
}
