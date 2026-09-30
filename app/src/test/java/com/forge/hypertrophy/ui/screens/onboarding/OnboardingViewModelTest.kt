package com.forge.hypertrophy.ui.screens.onboarding

import com.forge.hypertrophy.data.repository.OnboardingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun completedSession_skipsOnboarding() = onboardingTest {
        val viewModel = OnboardingViewModel(FakeOnboardingRepository(completed = true))
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Done, viewModel.uiState.value.phase)
        assertEquals(true, viewModel.uiState.value.onboardingComplete)
    }

    @Test
    fun notificationsRequired_startsOnNotificationsStep() = onboardingTest {
        val viewModel = OnboardingViewModel(FakeOnboardingRepository(completed = false))
        advanceUntilIdle()

        viewModel.onEvent(
            OnboardingEvent.DeviceStatus(
                notificationsRuntimeRequired = true,
                notificationsGranted = false,
                batteryExempt = false,
            ),
        )

        assertEquals(OnboardingPhase.Notifications, viewModel.uiState.value.phase)
    }

    @Test
    fun notificationsNotRequired_startsOnBatteryStep() = onboardingTest {
        val viewModel = OnboardingViewModel(FakeOnboardingRepository(completed = false))
        advanceUntilIdle()

        viewModel.onEvent(
            OnboardingEvent.DeviceStatus(
                notificationsRuntimeRequired = false,
                notificationsGranted = true,
                batteryExempt = false,
            ),
        )

        assertEquals(OnboardingPhase.Battery, viewModel.uiState.value.phase)
    }

    @Test
    fun notificationsAlreadyGranted_startsOnBatteryStep() = onboardingTest {
        val viewModel = OnboardingViewModel(FakeOnboardingRepository(completed = false))
        advanceUntilIdle()

        viewModel.onEvent(
            OnboardingEvent.DeviceStatus(
                notificationsRuntimeRequired = true,
                notificationsGranted = true,
                batteryExempt = false,
            ),
        )

        assertEquals(OnboardingPhase.Battery, viewModel.uiState.value.phase)
    }

    @Test
    fun deniedNotification_stillAdvancesToBattery() = onboardingTest {
        val viewModel = OnboardingViewModel(FakeOnboardingRepository(completed = false))
        advanceUntilIdle()
        viewModel.onEvent(
            OnboardingEvent.DeviceStatus(
                notificationsRuntimeRequired = true,
                notificationsGranted = false,
                batteryExempt = false,
            ),
        )

        viewModel.onEvent(OnboardingEvent.NotificationsResult(granted = false))

        assertFalse(viewModel.uiState.value.notificationsGranted)
        assertEquals(OnboardingPhase.Battery, viewModel.uiState.value.phase)
    }

    @Test
    fun deviceStatusBeforeSession_advancesOnceRepositoryEmitsIncomplete() = onboardingTest {
        val repository = ControllableOnboardingRepository()
        val viewModel = OnboardingViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(
            OnboardingEvent.DeviceStatus(
                notificationsRuntimeRequired = true,
                notificationsGranted = false,
                batteryExempt = false,
            ),
        )
        repository.emitCompleted(false)
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Notifications, viewModel.uiState.value.phase)
        assertEquals(false, viewModel.uiState.value.onboardingComplete)
    }

    @Test
    fun batteryExemptionChanged_updatesState() = onboardingTest {
        val viewModel = OnboardingViewModel(FakeOnboardingRepository(completed = false))
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.BatteryExemptionChanged(exempt = true))

        assertTrue(viewModel.uiState.value.batteryExempt)
    }

    @Test
    fun laterDeviceStatus_doesNotResetPhase() = onboardingTest {
        val viewModel = OnboardingViewModel(FakeOnboardingRepository(completed = false))
        advanceUntilIdle()
        viewModel.onEvent(
            OnboardingEvent.DeviceStatus(
                notificationsRuntimeRequired = true,
                notificationsGranted = false,
                batteryExempt = false,
            ),
        )

        viewModel.onEvent(
            OnboardingEvent.DeviceStatus(
                notificationsRuntimeRequired = true,
                notificationsGranted = false,
                batteryExempt = true,
            ),
        )

        assertEquals(OnboardingPhase.Notifications, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.batteryExempt)
    }

    @Test
    fun finish_persistsCompletion() = onboardingTest {
        val repository = FakeOnboardingRepository(completed = false)
        val viewModel = OnboardingViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Finish)
        advanceUntilIdle()

        assertTrue(repository.marked)
        assertEquals(true, viewModel.uiState.value.onboardingComplete)
        assertEquals(OnboardingPhase.Done, viewModel.uiState.value.phase)
    }

    @Test
    fun freshViewModelAfterMarkCompleted_startsDone() = onboardingTest {
        val repository = FakeOnboardingRepository(completed = false)
        val first = OnboardingViewModel(repository)
        advanceUntilIdle()
        first.onEvent(OnboardingEvent.Finish)
        advanceUntilIdle()

        val second = OnboardingViewModel(repository)
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Done, second.uiState.value.phase)
        assertEquals(true, second.uiState.value.onboardingComplete)
    }

    private fun onboardingTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeOnboardingRepository(
    completed: Boolean,
) : OnboardingRepository {
    private val state = MutableStateFlow(completed)
    var marked: Boolean = false
        private set

    override val completed: Flow<Boolean> = state

    override suspend fun markCompleted() {
        marked = true
        state.value = true
    }
}

private class ControllableOnboardingRepository : OnboardingRepository {
    private val values = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    override val completed: Flow<Boolean> = values

    override suspend fun markCompleted() {
        values.emit(true)
    }

    fun emitCompleted(completed: Boolean) {
        check(values.tryEmit(completed))
    }
}
