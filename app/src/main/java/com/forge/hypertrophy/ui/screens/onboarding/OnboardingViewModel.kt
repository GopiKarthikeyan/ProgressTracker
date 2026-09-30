package com.forge.hypertrophy.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var sessionLoaded = false
    private var pendingDeviceStatus: OnboardingEvent.DeviceStatus? = null

    init {
        viewModelScope.launch {
            repository.completed.collect { completed ->
                sessionLoaded = true
                _uiState.update { current ->
                    if (completed) {
                        current.copy(
                            onboardingComplete = true,
                            phase = OnboardingPhase.Done,
                        )
                    } else {
                        val loaded = current.copy(onboardingComplete = false)
                        val pending = pendingDeviceStatus
                        if (pending != null && loaded.phase == OnboardingPhase.Loading) {
                            loaded.withFirstPhase(pending)
                        } else {
                            loaded
                        }
                    }
                }
            }
        }
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.DeviceStatus -> onDeviceStatus(event)
            is OnboardingEvent.NotificationsResult -> {
                _uiState.update { current ->
                    current.copy(
                        notificationsGranted = event.granted,
                        phase = OnboardingPhase.Battery,
                    )
                }
            }
            is OnboardingEvent.BatteryExemptionChanged -> {
                _uiState.update { it.copy(batteryExempt = event.exempt) }
            }
            OnboardingEvent.Finish -> {
                viewModelScope.launch {
                    repository.markCompleted()
                }
            }
        }
    }

    private fun onDeviceStatus(event: OnboardingEvent.DeviceStatus) {
        pendingDeviceStatus = event
        _uiState.update { current ->
            val status = current.copy(
                notificationsGranted = event.notificationsGranted,
                batteryExempt = event.batteryExempt,
            )
            when {
                !sessionLoaded -> status
                current.onboardingComplete != false || current.phase == OnboardingPhase.Done -> status
                current.phase != OnboardingPhase.Loading -> status
                else -> status.withFirstPhase(event)
            }
        }
    }
}

private fun OnboardingUiState.withFirstPhase(
    event: OnboardingEvent.DeviceStatus,
): OnboardingUiState {
    val phase = if (event.notificationsRuntimeRequired && !event.notificationsGranted) {
        OnboardingPhase.Notifications
    } else {
        OnboardingPhase.Battery
    }
    return copy(phase = phase)
}
