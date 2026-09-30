package com.forge.hypertrophy.ui.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.screens.PlaceholderScreen

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_dashboard),
        modifier = modifier,
    )
}
