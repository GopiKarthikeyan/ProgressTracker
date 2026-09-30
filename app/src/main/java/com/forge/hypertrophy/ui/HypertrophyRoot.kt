package com.forge.hypertrophy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.ui.navigation.MainScaffold
import com.forge.hypertrophy.ui.screens.onboarding.OnboardingScreen
import com.forge.hypertrophy.ui.screens.onboarding.OnboardingViewModel
import com.forge.hypertrophy.ui.theme.Black

@Composable
fun HypertrophyRoot(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (state.onboardingComplete) {
        null -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black),
        )
        false -> OnboardingScreen(
            state = state,
            onEvent = viewModel::onEvent,
        )
        true -> MainScaffold()
    }
}
