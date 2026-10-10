package com.forge.hypertrophy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Sand

@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = TouchTargets.Editor,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .background(Sand, CircleShape),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.builder_back),
            tint = Ink,
        )
    }
}
