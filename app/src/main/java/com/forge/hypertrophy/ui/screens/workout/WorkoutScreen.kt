package com.forge.hypertrophy.ui.screens.workout

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.forge.hypertrophy.domain.workout.REST_ADJUST_STEP_MILLIS
import com.forge.hypertrophy.domain.workout.TimerPhase
import com.forge.hypertrophy.domain.workout.WorkoutPosition
import com.forge.hypertrophy.ui.components.CircleActionButton
import com.forge.hypertrophy.ui.components.ExerciseSilhouette
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.PrimaryButton
import com.forge.hypertrophy.ui.components.ProgressRing
import com.forge.hypertrophy.ui.components.SecondaryButton
import com.forge.hypertrophy.ui.components.StepperButton
import com.forge.hypertrophy.ui.theme.Muted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WorkoutScreen(
    onBack: () -> Unit,
    onRecordClip: (exerciseId: Long, setEntryId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var pendingImport by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
    val pickClip = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = pendingImport
        pendingImport = null
        if (uri != null && target != null) {
            viewModel.onEvent(WorkoutEvent.ImportClip(uri, target.first, target.second))
        }
    }
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
        onRecordClip = onRecordClip,
        onImportClip = { exerciseId, setEntryId ->
            pendingImport = exerciseId to setEntryId
            pickClip.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        },
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
    onRecordClip: (exerciseId: Long, setEntryId: Long?) -> Unit = { _, _ -> },
    onImportClip: (exerciseId: Long, setEntryId: Long?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val skipReason = stringResource(R.string.workout_skip_reason_default)
    var moreOpen by remember { mutableStateOf(false) }
    val working = state.position as? WorkoutPosition.WorkingSet
    LaunchedEffect(working?.slot?.sessionSlotId) {
        if (working == null) moreOpen = false
    }

    val showHeroSilhouette = headerExerciseName(state.position) != null ||
        headerCategory(state.position) != null
    val showSlotStrip = state.position !is WorkoutPosition.Readiness &&
        state.position !is WorkoutPosition.Summary

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top chrome + hero silhouette
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                    exerciseName = headerExerciseName(state.position),
                    category = headerCategory(state.position),
                    showSilhouette = false,
                )
                if (showHeroSilhouette) {
                    ExerciseSilhouette(
                        exerciseName = headerExerciseName(state.position),
                        category = headerCategory(state.position),
                        size = 148.dp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
            }

            // Middle: everything else scrolls between hero and strip
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (
                    state.advice == ReadinessAdvice.SHORT_ON_TIME_HOLD_WEIGHTS &&
                    state.position !is WorkoutPosition.Readiness &&
                    state.position !is WorkoutPosition.Summary
                ) {
                    Text(
                        stringResource(R.string.workout_readiness_advice),
                        color = MaterialTheme.colorScheme.primary,
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
                    is WorkoutPosition.Warmup -> WarmupCard(pos, state.timer)
                    is WorkoutPosition.Resting -> RestCard(pos, state, onEvent)
                    is WorkoutPosition.Cooldown -> CooldownCard(pos, onEvent)
                    is WorkoutPosition.Summary -> SummaryCard(state.summary)
                }
                if (state.importingClip) {
                    Text(stringResource(R.string.media_importing), color = MaterialTheme.colorScheme.onSurface)
                }
                if (state.importFailed) {
                    Text(stringResource(R.string.media_import_failed), color = MaterialTheme.colorScheme.primary)
                    SecondaryButton(
                        label = stringResource(R.string.dashboard_dismiss),
                        onClick = { onEvent(WorkoutEvent.DismissImportFailure) },
                    )
                }
                PrimaryAction(
                    state = state,
                    onEvent = onEvent,
                    onBack = onBack,
                    onRecordClip = onRecordClip,
                    onImportClip = onImportClip,
                    onUndo = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onEvent(WorkoutEvent.Undo)
                    },
                )
            }

            // Bottom: horizontal exercise strip with silhouettes
            if (showSlotStrip) {
                SlotStrip(state.slotStrip)
            }
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
    onRecordClip: (exerciseId: Long, setEntryId: Long?) -> Unit,
    onImportClip: (exerciseId: Long, setEntryId: Long?) -> Unit,
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
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when {
            undo -> PrimaryButton(label = stringResource(R.string.workout_undo), onClick = onUndo)
            pos is WorkoutPosition.Resting -> {
                RestTimerActions(pos, state, onEvent, onRecordClip, onImportClip)
            }
            pos is WorkoutPosition.Warmup -> {
                PrimaryButton(
                    label = stringResource(R.string.workout_warmup_start_work),
                    onClick = { onEvent(WorkoutEvent.Primary(EntryMethod.SCREEN)) },
                )
                SecondaryButton(
                    label = stringResource(R.string.workout_skip_warmup),
                    onClick = { onEvent(WorkoutEvent.Skip(SKIPPED_REASON)) },
                )
            }
            pos is WorkoutPosition.WorkingSet -> {
                SecondaryButton(
                    label = stringResource(R.string.workout_record_clip),
                    onClick = { onRecordClip(pos.slot.activeExerciseId, null) },
                )
                SecondaryButton(
                    label = stringResource(R.string.workout_import_clip),
                    onClick = { onImportClip(pos.slot.activeExerciseId, null) },
                    enabled = !state.importingClip,
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
private fun RestTimerActions(
    pos: WorkoutPosition.Resting,
    state: WorkoutUiState,
    onEvent: (WorkoutEvent) -> Unit,
    onRecordClip: (exerciseId: Long, setEntryId: Long?) -> Unit,
    onImportClip: (exerciseId: Long, setEntryId: Long?) -> Unit,
) {
    val timer = state.timer
    val fraction = restProgress(pos.slot.sessionSlotId to pos.round, timer)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            symbol = "−",
            contentDescription = stringResource(R.string.workout_rest_minus),
            onClick = { onEvent(WorkoutEvent.AdjustRest(-REST_ADJUST_STEP_MILLIS)) },
        )
        ProgressRing(progress = fraction, size = 160.dp, stroke = 12.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NumericText(
                    text = clockText(timer.phase, timer.remainingMillis, timer.elapsedMillis),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(R.string.workout_rest_label),
                    color = Muted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        StepperButton(
            symbol = "+",
            contentDescription = stringResource(R.string.workout_rest_plus),
            onClick = { onEvent(WorkoutEvent.AdjustRest(REST_ADJUST_STEP_MILLIS)) },
        )
    }
    PrimaryButton(
        label = stringResource(R.string.workout_skip_rest),
        onClick = { onEvent(WorkoutEvent.Primary(EntryMethod.SCREEN)) },
    )
    val setId = state.lastLoggedSetId
    val exerciseId = state.lastLoggedExerciseId
    if (setId != null && exerciseId != null) {
        SecondaryButton(
            label = stringResource(R.string.workout_record_clip),
            onClick = { onRecordClip(exerciseId, setId) },
        )
        SecondaryButton(
            label = stringResource(R.string.workout_import_clip),
            onClick = { onImportClip(exerciseId, setId) },
            enabled = !state.importingClip,
        )
    }
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
    is WorkoutPosition.Warmup -> stringResource(R.string.workout_warmup_phase_title)
    is WorkoutPosition.Resting -> stringResource(R.string.workout_resting_title)
    is WorkoutPosition.PracticeBlock -> position.slot.exerciseName
    is WorkoutPosition.Cooldown -> stringResource(R.string.workout_cooldown_title)
    is WorkoutPosition.Summary -> stringResource(R.string.workout_complete_title)
}

@Composable
private fun headerSubtitle(position: WorkoutPosition): String? = when (position) {
    is WorkoutPosition.WorkingSet -> stringResource(R.string.workout_set_label, position.setNumber, position.setCount)
    is WorkoutPosition.Warmup -> position.slot.exerciseName
    is WorkoutPosition.Resting -> position.slot.exerciseName
    is WorkoutPosition.PracticeBlock -> stringResource(R.string.workout_timed_block)
    else -> null
}

private fun headerExerciseName(position: WorkoutPosition): String? = when (position) {
    is WorkoutPosition.WorkingSet -> position.slot.exerciseName
    is WorkoutPosition.Warmup -> position.slot.exerciseName
    is WorkoutPosition.Resting -> position.slot.exerciseName
    is WorkoutPosition.PracticeBlock -> position.slot.exerciseName
    else -> null
}

private fun headerCategory(position: WorkoutPosition): com.forge.hypertrophy.domain.model.SlotCategory? = when (position) {
    is WorkoutPosition.WorkingSet -> position.slot.prescription.category
    is WorkoutPosition.Warmup -> position.slot.prescription.category
    is WorkoutPosition.Resting -> position.slot.prescription.category
    is WorkoutPosition.PracticeBlock -> position.slot.prescription.category
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
