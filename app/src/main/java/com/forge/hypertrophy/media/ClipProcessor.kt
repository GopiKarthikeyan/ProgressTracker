package com.forge.hypertrophy.media

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.forge.hypertrophy.domain.media.TrimWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Trims a clip and burns a label into it. Progress runs from 0 to 1. */
interface ClipProcessor {
    suspend fun process(
        input: File,
        output: File,
        window: TrimWindow,
        label: String,
        onProgress: (Float) -> Unit,
    )
}

fun interface ClipDurations {
    suspend fun durationMs(file: File): Long
}

class RetrieverClipDurations @Inject constructor() : ClipDurations {
    override suspend fun durationMs(file: File): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }
}

/**
 * Media3 Transformer. The transformer object lives on the main looper, which
 * is what it requires for its callbacks; the encode itself runs on its own
 * threads. Cancelling the coroutine cancels the export.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class TransformerClipProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClipProcessor {
    override suspend fun process(
        input: File,
        output: File,
        window: TrimWindow,
        label: String,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            lateinit var transformer: Transformer
            val poll = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    handler.postDelayed(this, POLL_MS)
                }
            }
            transformer = Transformer.Builder(context)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(poll)
                            onProgress(1f)
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            handler.removeCallbacks(poll)
                            output.delete()
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    },
                )
                .build()
            val item = MediaItem.Builder()
                .setUri(Uri.fromFile(input))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(window.startMs)
                        .setEndPositionMs(window.endMs)
                        .build(),
                )
                .build()
            val videoEffects: List<Effect> = listOf(labelOverlay(label))
            val edited = EditedMediaItem.Builder(item)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
            continuation.invokeOnCancellation {
                handler.removeCallbacks(poll)
                transformer.cancel()
                output.delete()
            }
            transformer.start(edited, output.path)
            handler.post(poll)
        }
    }

    private fun labelOverlay(label: String): OverlayEffect {
        val text = SpannableString(" $label ")
        val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        text.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, flags)
        text.setSpan(BackgroundColorSpan(Color.argb(160, 0, 0, 0)), 0, text.length, flags)
        text.setSpan(AbsoluteSizeSpan(LABEL_PX), 0, text.length, flags)
        text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, flags)
        val settings = StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, -1f)
            .setBackgroundFrameAnchor(0f, -0.9f)
            .build()
        val overlays: List<TextureOverlay> = listOf(TextOverlay.createStaticTextOverlay(text, settings))
        return OverlayEffect(overlays)
    }

    private companion object {
        const val POLL_MS = 250L
        const val LABEL_PX = 56
    }
}
