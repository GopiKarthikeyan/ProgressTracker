package com.forge.hypertrophy.ui.screens.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.forge.hypertrophy.ui.theme.NeonAccent

/**
 * Before/after reveal. The after photo is clipped at the handle, and a
 * horizontal drag moves the handle.
 */
@Composable
fun PhysiqueSlider(
    before: ImageBitmap,
    after: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    var fraction by remember { mutableFloatStateOf(0.5f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    fraction = (fraction + dragAmount / widthPx).coerceIn(0f, 1f)
                }
            },
    ) {
        Image(
            bitmap = before,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            bitmap = after,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    widthPx = size.width
                    clipRect(right = size.width * fraction) {
                        this@drawWithContent.drawContent()
                    }
                    drawLine(
                        color = NeonAccent,
                        start = Offset(size.width * fraction, 0f),
                        end = Offset(size.width * fraction, size.height),
                        strokeWidth = HANDLE_WIDTH_PX,
                    )
                },
        )
    }
}

private const val HANDLE_WIDTH_PX = 4f
