package com.forge.hypertrophy.ui.screens.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.forge.hypertrophy.ui.components.TouchTargets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 72dp button; capture and playback controls are workout-adjacent. */
@Composable
fun MediaButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTargets.Workout),
    ) {
        Text(label)
    }
}

/** One short beep per whole second left in a countdown. */
@Composable
fun CountdownBeeps(secondsLeft: Int, active: Boolean) {
    val tones = remember { ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME) }
    DisposableEffect(Unit) {
        onDispose { tones.release() }
    }
    LaunchedEffect(secondsLeft, active) {
        if (!active) return@LaunchedEffect
        val tone = if (secondsLeft == 0) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP
        tones.startTone(tone, if (secondsLeft == 0) LONG_BEEP_MS else SHORT_BEEP_MS)
    }
}

/** Decodes a photo off the main thread at a reduced size. */
@Composable
fun rememberPhoto(path: String?): State<ImageBitmap?> = produceState<ImageBitmap?>(initialValue = null, path) {
    value = if (path == null) {
        null
    } else {
        withContext(Dispatchers.IO) { decodeScaled(path) }
    }
}

private fun decodeScaled(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > MAX_EDGE_PX || bounds.outHeight / sample > MAX_EDGE_PX) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

private const val TONE_VOLUME = 80
private const val SHORT_BEEP_MS = 120
private const val LONG_BEEP_MS = 400
private const val MAX_EDGE_PX = 1_280
