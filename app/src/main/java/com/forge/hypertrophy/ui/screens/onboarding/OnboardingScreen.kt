package com.forge.hypertrophy.ui.screens.onboarding

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.theme.Black

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onEvent(OnboardingEvent.NotificationsResult(granted))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onEvent(
                    OnboardingEvent.BatteryExemptionChanged(
                        exempt = context.isBatteryOptimizationExempt(),
                    ),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.onboardingComplete, state.phase) {
        if (state.onboardingComplete == false && state.phase == OnboardingPhase.Loading) {
            onEvent(context.deviceStatusEvent())
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Black,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.phase) {
                OnboardingPhase.Notifications -> NotificationsStep(
                    onAllow = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
                OnboardingPhase.Battery -> BatteryStep(
                    exempt = state.batteryExempt,
                    onExempt = { context.openBatteryExemptionSettings() },
                    onContinue = { onEvent(OnboardingEvent.Finish) },
                )
                OnboardingPhase.Loading,
                OnboardingPhase.Done,
                -> Unit
            }
        }
    }
}

@Composable
private fun NotificationsStep(onAllow: () -> Unit) {
    Text(
        text = stringResource(R.string.onboarding_notifications_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = stringResource(R.string.onboarding_notifications_body),
        style = MaterialTheme.typography.bodyLarge,
    )
    Button(
        onClick = onAllow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.onboarding_notifications_action))
    }
}

@Composable
private fun BatteryStep(
    exempt: Boolean,
    onExempt: () -> Unit,
    onContinue: () -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_battery_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = stringResource(R.string.onboarding_battery_body),
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = stringResource(R.string.onboarding_battery_manufacturer),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (exempt) {
        Text(
            text = stringResource(R.string.onboarding_battery_exempt),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Button(
        onClick = onExempt,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.onboarding_battery_action))
    }
    TextButton(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.onboarding_battery_continue))
    }
}

private fun Context.deviceStatusEvent(): OnboardingEvent.DeviceStatus {
    val runtimeRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val granted = if (!runtimeRequired) {
        true
    } else {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
    return OnboardingEvent.DeviceStatus(
        notificationsRuntimeRequired = runtimeRequired,
        notificationsGranted = granted,
        batteryExempt = isBatteryOptimizationExempt(),
    )
}

private fun Context.isBatteryOptimizationExempt(): Boolean {
    val powerManager = getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.openBatteryExemptionSettings() {
    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:$packageName".toUri()
    }
    try {
        startActivity(request)
    } catch (_: ActivityNotFoundException) {
        // The system dialog is unavailable. The on-screen note covers manufacturer restrictions.
    }
}
