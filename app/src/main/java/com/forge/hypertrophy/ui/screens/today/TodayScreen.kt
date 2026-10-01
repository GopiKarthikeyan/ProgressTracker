package com.forge.hypertrophy.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.White

@Composable
fun TodayScreen(
    onOpenCardio: () -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.nav_today),
            color = White,
            style = MaterialTheme.typography.headlineMedium,
        )
        TextButton(
            onClick = onOpenCardio,
            modifier = Modifier.heightIn(min = TouchTargets.Workout),
        ) {
            Text(stringResource(R.string.cardio_title))
        }
        TextButton(
            onClick = onOpenGallery,
            modifier = Modifier.heightIn(min = TouchTargets.Workout),
        ) {
            Text(stringResource(R.string.media_gallery))
        }
    }
}
