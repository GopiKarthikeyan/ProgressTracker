package com.forge.hypertrophy.ui.screens.workout

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.engine.ReadinessAdvice
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.workout.HandsFreeKeys
import com.forge.hypertrophy.domain.workout.TimerPhase
import com.forge.hypertrophy.domain.workout.WorkoutPosition
import com.forge.hypertrophy.ui.components.CircleActionButton
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.PrimaryButton
import com.forge.hypertrophy.ui.components.ProgressRing
import com.forge.hypertrophy.ui.components.SecondaryButton
import com.forge.hypertrophy.ui.theme.Cream
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Rose
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WorkoutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        while (isActive) {
            delay(1_000)
            viewModel.onEvent(WorkoutEvent.Tick)
        }
    }
    LaunchedEffect(state.handsFreePulse) {
        if (state.handsFreePulse == 0) return@LaunchedEffect
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    WorkoutContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val code = event.key.nativeKeyCode
                if (!HandsFreeKeys.isWorkoutKey(code)) return@onPreviewKeyEvent false
                if (HandsFreeKeys.yieldsToActiveMedia(code) && context.musicActive()) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    viewModel.onEvent(WorkoutEvent.Primary(EntryMethod.HARDWARE_KEY))
                }
                true
            },
    )
}

@Composable
fun WorkoutContent(
    state: WorkoutUiState,
    onEvent: (WorkoutEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val skipReason = stringResource(R.string.workout_skip_reason_default)
    var moreOpen by remember { mutableStateOf(false) }
    val working = state.position as? WorkoutPosition.WorkingSet
    LaunchedEffect(working?.slot?.sessionSlotId) {
        if (working == null) moreOpen = false
    }

    Box(modifier = modifier.fillMaxSize().background(Cream)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WorkoutHeader(
                title = headerTitle(state.position),
                subtitle = headerSubtitle(state.position),
                etaSeconds = if (
                    state.position !is WorkoutPosition.Readiness &&
                    state.position !is WorkoutPosition.Summary
                ) {
                    state.etaSeconds
                } else {
                    null
                },
                showMore = working != null,
                onBack = onBack,
                onMore = { moreOpen = true },
            )
            if (
                state.advice == ReadinessAdvice.SHORT_ON_TIME_HOLD_WEIGHTS &&
                state.position !is WorkoutPosition.Readiness &&
                state.position !is WorkoutPosition.Summary
            ) {
                Text(
                    stringResource(R.string.workout_readiness_advice),
                    color = Rose,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when (val pos = state.position) {
                is WorkoutPosition.Readiness -> ReadinessCard(
                    sessionReady = state.sessionId != null,
                    onEvent = onEvent,
                )
                is WorkoutPosition.Prep -> PrepCard(pos, onEvent)
                is WorkoutPosition.PracticeBlock -> PracticeBlockCard(state, pos.slot.sessionSlotId)
                is WorkoutPosition.WorkingSet -> WorkingSetCard(pos, onEvent)
                is WorkoutPosition.Resting -> RestCard(pos, state, onEvent)
                is WorkoutPosition.Cooldown -> CooldownCard(pos, onEvent)
                is WorkoutPosition.Summary -> SummaryCard(state.summary)
            }
            PrimaryAction(
                state = state,
                onEvent = onEvent,
                onBack = onBack,
                onUndo = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onEvent(WorkoutEvent.Undo)
                },
            )
        }
        if (moreOpen && working != null) {
            WorkoutMoreSheet(
                pos = working,
                jointFlags = state.jointFlags,
                shortOnTime = state.shortOnTime,
                cuesEnabled = state.cuesEnabled,
                canRemoveSet = state.canRemoveSet,
                skipReason = skipReason,
                onEvent = onEvent,
                onDismiss = { moreOpen = false },
            )
        }
    }
}

@Composable
private fun PrimaryAction(
    state: WorkoutUiState,
    onEvent: (WorkoutEvent) -> Unit,
    onBack: () -> Unit,
    onUndo: () -> Unit,
) {
    val undo = state.undoUntilElapsedRealtime != null
    val pos = state.position
    val working = pos as? WorkoutPosition.WorkingSet
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!undo && working?.needsFormCheck == true) {
            SecondaryButton(
                label = stringResource(R.string.workout_form_solid),
                onClick = { onEvent(WorkoutEvent.ConfirmForm(working.slot.sessionSlotId)) },
                accent = true,
            )
        }
        if (!undo && working?.suggestSkip == true) {
            Text(
                stringResource(R.string.workout_suggest_skip),
                color = Rose,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when {
            undo -> PrimaryButton(label = stringResource(R.string.workout_undo), onClick = onUndo)
            pos is WorkoutPosition.Resting -> {
                val timer = state.timer
                val fraction = restProgress(pos.slot.sessionSlotId to pos.round, timer)
                ProgressRing(progress = fraction, size = 160.dp, stroke = 12.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NumericText(
                            text = clockText(timer.phase, timer.remainingMillis, timer.elapsedMillis),
                            color = Ink,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            stringResource(R.string.workout_skip_rest),
                            color = Rose,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // Whole ring area is tappable via the button below for 72dp accessibility.
                PrimaryButton(
                    label = stringResource(R.string.workout_skip_rest),
                    onClick = { onEvent(WorkoutEvent.Primary(EntryMethod.SCREEN)) },
                )
                SecondaryButton(
                    label = stringResource(R.string.workout_add_set),
                    onClick = { onEvent(WorkoutEvent.AddSet) },
                )
                if (state.canRemoveSet) {
                    SecondaryButton(
                        label = stringResource(R.string.workout_remove_set),
                        onClick = { onEvent(WorkoutEvent.RemoveSet) },
                    )
                }
            }
            pos is WorkoutPosition.WorkingSet -> {
                SecondaryButton(
                    label = stringResource(R.string.workout_add_set),
                    onClick = { onEvent(WorkoutEvent.AddSet) },
                )
                if (state.canRemoveSet) {
                    SecondaryButton(
                        label = stringResource(R.string.workout_remove_set),
                        onClick = { onEvent(WorkoutEvent.RemoveSet) },
                    )
                }
                CircleActionButton(
                    label = stringResource(R.string.workout_log_set),
                    onClick = { onEvent(WorkoutEvent.Primary(EntryMethod.SCREEN)) },
                    size = 128.dp,
                )
            }
            pos is WorkoutPosition.Prep && pos.items.all { it.done } -> CircleActionButton(
                label = stringResource(R.string.workout_start_first),
                onClick = { onEvent(WorkoutEvent.Primary(EntryMethod.SCREEN)) },
            )
            pos is WorkoutPosition.PracticeBlock -> CircleActionButton(
                label = stringResource(R.string.workout_complete_block),
                onClick = { onEvent(WorkoutEvent.Primary(EntryMethod.SCREEN)) },
            )
            pos is WorkoutPosition.Cooldown && pos.items.all { it.done } -> CircleActionButton(
                label = stringResource(R.string.workout_finish),
                onClick = { onEvent(WorkoutEvent.CompleteWorkout) },
            )
            pos is WorkoutPosition.Summary -> PrimaryButton(
                label = stringResource(R.string.workout_done),
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun restProgress(key: Any, timer: com.forge.hypertrophy.domain.workout.TimerSnapshot): Float {
    var ceiling by remember(key) { mutableStateOf(0L) }
    if (
        (timer.phase == TimerPhase.COUNTDOWN || timer.phase == TimerPhase.WARNING) &&
        timer.remainingMillis > ceiling
    ) {
        ceiling = timer.remainingMillis
    }
    if (timer.phase == TimerPhase.OVERTIME || timer.phase == TimerPhase.FINISHED) return 1f
    if (ceiling <= 0L) return 0f
    val done = (ceiling - timer.remainingMillis).coerceAtLeast(0)
    return (done.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun headerTitle(position: WorkoutPosition): String = when (position) {
    is WorkoutPosition.Readiness -> stringResource(R.string.workout_readiness_title)
    is WorkoutPosition.Prep -> stringResource(R.string.workout_prep_title)
    is WorkoutPosition.WorkingSet -> position.slot.exerciseName
    is WorkoutPosition.Resting -> stringResource(R.string.workout_resting_title)
    is WorkoutPosition.PracticeBlock -> position.slot.exerciseName
    is WorkoutPosition.Cooldown -> stringResource(R.string.workout_cooldown_title)
    is WorkoutPosition.Summary -> stringResource(R.string.workout_complete_title)
}

@Composable
private fun headerSubtitle(position: WorkoutPosition): String? = when (position) {
    is WorkoutPosition.WorkingSet -> stringResource(R.string.workout_set_label, position.setNumber, position.setCount)
    is WorkoutPosition.Resting -> position.slot.exerciseName
    is WorkoutPosition.PracticeBlock -> stringResource(R.string.workout_timed_block)
    else -> null
}

internal fun clockText(phase: TimerPhase, remainingMillis: Long, elapsedMillis: Long): String {
    val seconds = when (phase) {
        TimerPhase.STOPWATCH, TimerPhase.OVERTIME -> (elapsedMillis / 1000L).toInt()
        else -> (remainingMillis / 1000L).toInt()
    }
    val text = formatDuration(seconds)
    return if (phase == TimerPhase.OVERTIME) "+$text" else text
}

private fun Context.musicActive(): Boolean =
    (getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
