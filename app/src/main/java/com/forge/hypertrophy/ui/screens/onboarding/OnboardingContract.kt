package com.forge.hypertrophy.ui.screens.onboarding

data class OnboardingUiState(
    val phase: OnboardingPhase = OnboardingPhase.Loading,
    val onboardingComplete: Boolean? = null,
    val notificationsGranted: Boolean = false,
    val batteryExempt: Boolean = false,
)

enum class OnboardingPhase {
    Loading,
    Notifications,
    Battery,
    Done,
}

sealed interface OnboardingEvent {
    data class DeviceStatus(
        val notificationsRuntimeRequired: Boolean,
        val notificationsGranted: Boolean,
        val batteryExempt: Boolean,
    ) : OnboardingEvent

    data class NotificationsResult(val granted: Boolean) : OnboardingEvent

    data class BatteryExemptionChanged(val exempt: Boolean) : OnboardingEvent

    data object Finish : OnboardingEvent
}
