package com.forge.hypertrophy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Muted
import com.forge.hypertrophy.ui.theme.Rose
import com.forge.hypertrophy.ui.theme.RoseSoft
import com.forge.hypertrophy.ui.theme.Sand
import com.forge.hypertrophy.ui.theme.White

val CardShape = RoundedCornerShape(24.dp)
val PillShape = RoundedCornerShape(999.dp)

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    color: Color = White,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(color, CardShape)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    trackColor: Color = RoseSoft,
    progressColor: Color = Rose,
    content: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val diameter = this.size.minDimension - strokePx
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Rose,
            contentColor = White,
            disabledContainerColor = Rose.copy(alpha = 0.35f),
            disabledContentColor = White.copy(alpha = 0.7f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    val border = if (accent) Rose else Ink.copy(alpha = if (enabled) 0.2f else 0.1f)
    val content = if (accent) Rose else Ink
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTargets.Workout),
        shape = PillShape,
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = White,
            contentColor = content,
            disabledContentColor = Muted,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = TouchTargets.Workout),
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Rose,
                contentColor = White,
            ),
        ) {
            ChipLabel(label)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = TouchTargets.Workout),
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Sand,
                contentColor = Ink,
            ),
        ) {
            ChipLabel(label)
        }
    }
}

@Composable
private fun ChipLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}

@Composable
fun StepperButton(
    symbol: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(TouchTargets.Workout)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, Ink.copy(alpha = if (enabled) 0.2f else 0.1f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = White,
            contentColor = Ink,
            disabledContentColor = Muted,
        ),
    ) {
        Text(text = symbol, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun CircleActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 120.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = CircleShape,
        contentPadding = PaddingValues(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Rose,
            contentColor = White,
            disabledContainerColor = Rose.copy(alpha = 0.35f),
            disabledContentColor = White.copy(alpha = 0.7f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}
