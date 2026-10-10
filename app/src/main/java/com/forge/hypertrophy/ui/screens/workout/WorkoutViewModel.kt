package com.forge.hypertrophy.ui.screens.workout

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import com.forge.hypertrophy.data.media.MediaImporter
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.repository.WorkoutRepository
import com.forge.hypertrophy.domain.engine.AutoRegulationAdvisor
import com.forge.hypertrophy.domain.engine.AutoRegulationSuggestion
import com.forge.hypertrophy.domain.engine.PlateCalculator
import com.forge.hypertrophy.domain.engine.ReadinessAdvice
import com.forge.hypertrophy.domain.engine.ReadinessAdvisor
import com.forge.hypertrophy.domain.engine.ReadinessCheck
import com.forge.hypertrophy.domain.engine.WarmupRampGenerator
import com.forge.hypertrophy.domain.engine.SessionEstimator
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionAction
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.TrainingSlot
import com.forge.hypertrophy.domain.model.WarmupStep
import com.forge.hypertrophy.domain.usecase.LoadWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.ToggleShortOnTimeUseCase
import com.forge.hypertrophy.domain.usecase.WorkoutInteractors
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import com.forge.hypertrophy.domain.workout.HandsFreeGate
import com.forge.hypertrophy.domain.workout.RegulationPrompt
import com.forge.hypertrophy.domain.workout.RestKind
import com.forge.hypertrophy.domain.workout.TimerCommand
import com.forge.hypertrophy.domain.workout.TimerPhase
import com.forge.hypertrophy.domain.workout.TimerSnapshot
import com.forge.hypertrophy.domain.workout.WorkoutMachineState
import com.forge.hypertrophy.domain.workout.WorkoutPosition
import com.forge.hypertrophy.domain.workout.WorkoutPositionTransitionValidator
import com.forge.hypertrophy.domain.workout.WorkoutSlot
import com.forge.hypertrophy.domain.workout.WorkoutTimer
import com.forge.hypertrophy.widget.TodayWidgetRefresher
import com.forge.hypertrophy.domain.workout.addExtraSet
import com.forge.hypertrophy.domain.workout.adjustDraft
import com.forge.hypertrophy.domain.workout.blockTimer
import com.forge.hypertrophy.domain.workout.checkOff
import com.forge.hypertrophy.domain.workout.chooseAlternative
import com.forge.hypertrophy.domain.workout.confirmForm
import com.forge.hypertrophy.domain.workout.dismissRest
import com.forge.hypertrophy.domain.workout.leavePrep
import com.forge.hypertrophy.domain.workout.logBlock
import com.forge.hypertrophy.domain.workout.logCurrentSet
import com.forge.hypertrophy.domain.workout.moveSlot
import com.forge.hypertrophy.domain.workout.projectTimer
import com.forge.hypertrophy.domain.workout.canRemoveExtraSet
import com.forge.hypertrophy.domain.workout.removeExtraSet
import com.forge.hypertrophy.domain.workout.removeSet
import com.forge.hypertrophy.domain.workout.restKey
import com.forge.hypertrophy.domain.workout.restTimer
import com.forge.hypertrophy.domain.workout.restoredCountdown
import com.forge.hypertrophy.domain.workout.restWindowSeconds
import com.forge.hypertrophy.domain.workout.skipSlot
import com.forge.hypertrophy.domain.workout.suggestionFor
import com.forge.hypertrophy.domain.workout.updateSet
import com.forge.hypertrophy.domain.workout.workoutCue
import com.forge.hypertrophy.domain.workout.workoutPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val SKIPPED_REASON = "skipped"

data class NextUp(
    val exerciseName: String,
    val platesPerSideKg: List<Double>,
    val warmup: List<WarmupStep>,
)

data class PrNote(
    val exerciseName: String,
    val e1rmKg: Double,
)

data class StagePrompt(
    val skillName: String,
    val tierIndex: Int,
    val stage: Int,
)

data class NextSessionNote(
    val exerciseName: String,
    val action: ProgressionAction,
    val weightKg: Double?,
)

data class WorkoutSummary(
    val prs: List<PrNote>,
    val stagePrompts: List<StagePrompt>,
    val nextSession: List<NextSessionNote>,
)

data class WorkoutUiState(
    val sessionId: Long? = null,
    val position: WorkoutPosition = WorkoutPosition.Readiness,
    val advice: ReadinessAdvice = ReadinessAdvice.NONE,
    val timer: TimerSnapshot = TimerSnapshot.Idle,
    val etaSeconds: Int = 0,
    val shortOnTime: Boolean = false,
    val undoUntilElapsedRealtime: Long? = null,
    val handsFreePulse: Int = 0,
    val cuesEnabled: Boolean = false,
    val jointFlags: Set<String> = emptySet(),
    val summary: WorkoutSummary? = null,
    val nextUp: NextUp? = null,
    val canRemoveSet: Boolean = false,
    /** Last logged set, for attaching a clip during rest. */
    val lastLoggedSetId: Long? = null,
    val lastLoggedExerciseId: Long? = null,
    val importingClip: Boolean = false,
    val importFailed: Boolean = false,
)

sealed interface WorkoutEvent {
    data class Start(val dayId: Long) : WorkoutEvent
    data class SubmitReadiness(val sleep: Int?, val soreness: Int?, val energy: Int?) : WorkoutEvent
    data object SkipReadiness : WorkoutEvent
    data class CheckOff(val itemId: Long) : WorkoutEvent
    data class Primary(val method: EntryMethod) : WorkoutEvent
    data class Adjust(val weightDeltaKg: Double = 0.0, val repDelta: Int = 0, val holdDelta: Int = 0) : WorkoutEvent
    data class Skip(val reason: String) : WorkoutEvent
    data class ChooseAlternative(val exerciseId: Long?) : WorkoutEvent
    data class MoveSlot(val from: Int, val to: Int) : WorkoutEvent
    data class ConfirmForm(val slotId: Long) : WorkoutEvent
    data class ToggleJoint(val flag: String) : WorkoutEvent
    data class SetRpe(val setId: Long, val rpe: Double?) : WorkoutEvent
    data object AcceptRegulation : WorkoutEvent
    data object DismissRegulation : WorkoutEvent
    data object ToggleShortOnTime : WorkoutEvent
    data object CompleteWorkout : WorkoutEvent
    data object AddSet : WorkoutEvent
    data object RemoveSet : WorkoutEvent
    data object Undo : WorkoutEvent
    data object Tick : WorkoutEvent
    data class Cues(val enabled: Boolean) : WorkoutEvent
    data class EditSetupNotes(val notes: String) : WorkoutEvent
    data class ImportClip(val uri: Uri, val exerciseId: Long, val setEntryId: Long?) : WorkoutEvent
    data object DismissImportFailure : WorkoutEvent
}

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workouts: WorkoutRepository,
    private val preferences: TrainingPreferencesRepository,
    private val timer: WorkoutTimer,
    private val clock: Clock,
    private val elapsed: ElapsedRealtimeClock,
    private val widget: TodayWidgetRefresher,
    private val breadcrumbs: Breadcrumbs,
    private val loadWorkout: LoadWorkoutUseCase,
    private val interactors: WorkoutInteractors,
    private val toggleShortOnTimeUseCase: ToggleShortOnTimeUseCase,
    private val mediaImporter: MediaImporter,
) : ViewModel() {
    private val requestedSessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L
    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var sessionId: Long = 0L
    private var sessionReady = false
    private var pendingReadiness: ReadinessCheck? = null
    private var machine = WorkoutMachineState()
    private var armed: String? = null
    private var cuesEnabled = false
    private val handsFree = HandsFreeGate()
    private var handsFreePulse = 0
    private var lastLoggedSetId: Long? = null
    private var lastLoggedExerciseId: Long? = null

    /**
     * Events run one at a time. [machine] is mutated across suspension points
     * (Room writes), so two quick taps or a key press during a write would
     * otherwise both see the same position and log the set twice.
     */
    private val events = Mutex()
    private val originalBounds = mutableMapOf<Long, Pair<Int, Int>>()
    private val readiness = ReadinessAdvisor()
    private val estimator = SessionEstimator()
    private val plates = PlateCalculator()
    private val warmups = WarmupRampGenerator(plates)
    private val transitions = WorkoutPositionTransitionValidator()
    private val regulation = AutoRegulationAdvisor()

    init {
        if (requestedSessionId != 0L) {
            viewModelScope.launch {
                events.withLock { restore(requestedSessionId) }
            }
        }
        viewModelScope.launch {
            timer.snapshot.collect { snap ->
                _uiState.update { it.copy(timer = snap) }
            }
        }
        viewModelScope.launch {
            timer.commands.collect { command ->
                when (command) {
                    TimerCommand.SKIP -> onEvent(WorkoutEvent.Skip(SKIPPED_REASON))
                    TimerCommand.COMPLETE_SET -> onEvent(WorkoutEvent.Primary(EntryMethod.SCREEN))
                }
            }
        }
    }

    fun onEvent(event: WorkoutEvent) {
        if (event == WorkoutEvent.Tick) {
            refreshClock()
            return
        }
        breadcrumbs.record(event.javaClass.simpleName)
        viewModelScope.launch {
            events.withLock { handle(event) }
        }
    }

    private suspend fun handle(event: WorkoutEvent) {
        when (event) {
            is WorkoutEvent.Start -> start(event.dayId)
            is WorkoutEvent.SubmitReadiness -> begin(event.sleep, event.soreness, event.energy)
            WorkoutEvent.SkipReadiness -> begin(null, null, null)
            is WorkoutEvent.CheckOff -> {
                machine = checkOff(machine, event.itemId)
                publish()
            }
            is WorkoutEvent.Primary -> primary(event.method)
            is WorkoutEvent.Adjust -> {
                machine = adjustDraft(machine, event.weightDeltaKg, event.repDelta, event.holdDelta)
                publish()
            }
            is WorkoutEvent.Skip -> skip(event.reason)
            is WorkoutEvent.ChooseAlternative -> choose(event.exerciseId)
            is WorkoutEvent.MoveSlot -> move(event.from, event.to)
            is WorkoutEvent.ConfirmForm -> {
                val slotId = (workoutPosition(machine) as? WorkoutPosition.WorkingSet)?.slot?.sessionSlotId
                    ?: event.slotId
                machine = confirmForm(machine, slotId)
                persist(machine.slots.first { it.sessionSlotId == slotId })
                publish()
            }
            is WorkoutEvent.ToggleJoint -> toggleJoint(event.flag)
            is WorkoutEvent.SetRpe -> setRpe(event.setId, event.rpe)
            WorkoutEvent.AcceptRegulation -> acceptRegulation()
            WorkoutEvent.DismissRegulation -> dismissRegulation()
            WorkoutEvent.ToggleShortOnTime -> toggleShortOnTime()
            WorkoutEvent.CompleteWorkout -> complete()
            WorkoutEvent.AddSet -> addSet()
            WorkoutEvent.RemoveSet -> removeExtra()
            WorkoutEvent.Undo -> undo()
            WorkoutEvent.Tick -> refreshClock()
            is WorkoutEvent.Cues -> {
                cuesEnabled = event.enabled
                armed = null
                publish()
            }
            is WorkoutEvent.EditSetupNotes -> editNotes(event.notes)
            is WorkoutEvent.ImportClip -> importClip(event.uri, event.exerciseId, event.setEntryId)
            WorkoutEvent.DismissImportFailure -> _uiState.update { it.copy(importFailed = false) }
        }
    }

    private suspend fun importClip(uri: Uri, exerciseId: Long, setEntryId: Long?) {
        _uiState.update { it.copy(importingClip = true, importFailed = false) }
        val ok = mediaImporter.importVideo(uri, exerciseId, setEntryId)
        _uiState.update { it.copy(importingClip = false, importFailed = !ok) }
    }

    private suspend fun start(dayId: Long) {
        val id = interactors.start.execute(dayId)
        restore(id)
    }

    private suspend fun restore(id: Long) {
        sessionId = id
        machine = loadWorkout.execute(id)
        machine.slots.forEach { slot ->
            originalBounds.putIfAbsent(slot.sessionSlotId, slot.prescription.setsMin to slot.prescription.setsMax)
        }
        resumeClock()
        sessionReady = true
        val pending = pendingReadiness
        pendingReadiness = null
        val applied = pending != null && !machine.started &&
            begin(pending.sleep, pending.soreness, pending.energy)
        if (!applied) publish()
    }

    private suspend fun begin(sleep: Int?, soreness: Int?, energy: Int?): Boolean {
        if (!sessionReady) {
            pendingReadiness = ReadinessCheck(sleep, soreness, energy)
            return false
        }
        if (workouts.findSession(sessionId) == null) return false
        val advice = readiness.advise(ReadinessCheck(sleep, soreness, energy))
        if (!workouts.beginSession(sessionId, sleep, soreness, energy, clock.instant())) return false
        machine = machine.copy(started = true)
        _uiState.value = _uiState.value.copy(advice = advice)
        publish()
        return true
    }

    private suspend fun primary(method: EntryMethod) {
        val now = elapsed.elapsedRealtime()
        if (method == EntryMethod.HARDWARE_KEY && !handsFree.accept(now)) return
        val acted = when (val position = workoutPosition(machine)) {
            is WorkoutPosition.Prep -> {
                if (position.items.all { it.done }) {
                    machine = leavePrep(machine)
                    publish()
                    true
                } else {
                    false
                }
            }
            is WorkoutPosition.WorkingSet -> {
                logWorkingSet(position, method, now)
                true
            }
            is WorkoutPosition.PracticeBlock -> {
                endBlock(position)
                true
            }
            is WorkoutPosition.Resting -> {
                machine = dismissRest(machine)
                armed = null
                timer.stop()
                publish()
                true
            }
            else -> false
        }
        if (acted && method == EntryMethod.HARDWARE_KEY) {
            handsFreePulse += 1
            publish()
        }
    }

    private suspend fun logWorkingSet(position: WorkoutPosition.WorkingSet, method: EntryMethod, now: Long) {
        val recorded = interactors.logSet.logWorkingSet(
            sessionSlotId = position.slot.sessionSlotId,
            setNumber = position.setNumber,
            side = position.side,
            suggestion = position.suggestion,
            jointFlags = machine.sessionJoints.toList(),
            method = method
        )
        machine = logCurrentSet(machine, recorded)
        lastLoggedSetId = recorded.id
        lastLoggedExerciseId = position.slot.activeExerciseId
        if (method == EntryMethod.HARDWARE_KEY) handsFree.armUndo(recorded.id, now)
        armed = null
        publish()
    }

    private suspend fun endBlock(position: WorkoutPosition.PracticeBlock) {
        val planned = position.slot.prescription.blockDurationSec ?: 0
        val snapshot = timer.snapshot.value
        val elapsedSeconds = when (snapshot.phase) {
            TimerPhase.COUNTDOWN, TimerPhase.WARNING -> (planned - (snapshot.remainingMillis / 1000L).toInt()).coerceAtLeast(0)
            else -> planned
        }
        val recorded = interactors.logSet.logPracticeBlock(position.slot.sessionSlotId, elapsedSeconds)
        machine = logBlock(machine, recorded)
        lastLoggedSetId = recorded.id
        lastLoggedExerciseId = position.slot.activeExerciseId
        armed = null
        timer.stop()
        publish()
    }

    private suspend fun skip(reason: String) {
        if (workoutPosition(machine) is WorkoutPosition.Resting) {
            machine = dismissRest(machine)
            armed = null
            timer.stop()
            publish()
            return
        }
        val slot = currentSlot() ?: return
        machine = skipSlot(machine, slot.sessionSlotId, reason)
        persist(machine.slots.first { it.sessionSlotId == slot.sessionSlotId })
        armed = null
        timer.stop()
        publish()
    }

    private suspend fun choose(exerciseId: Long?) {
        val slot = currentSlot() ?: return
        val resolvedId = exerciseId ?: slot.prescription.exerciseId
        val exercise = workouts.findExercise(resolvedId)
        machine = chooseAlternative(machine, slot.sessionSlotId, exerciseId)
        if (exercise != null) {
            val hold = loadWorkout.skillHold(resolvedId, slot.prescription.metricType)
            machine = machine.copy(
                slots = machine.slots.map { candidate ->
                    if (candidate.sessionSlotId == slot.sessionSlotId) {
                        candidate.copy(
                            exerciseName = exercise.name,
                            setupNotes = exercise.setupNotes,
                            unilateral = exercise.isUnilateral,
                            equipment = exercise.equipment,
                            barWeightKg = exercise.barWeightKg,
                            skillHold = hold,
                        )
                    } else {
                        candidate
                    }
                },
            )
        }
        persist(machine.slots.first { it.sessionSlotId == slot.sessionSlotId })
        publish()
    }

    private suspend fun move(from: Int, to: Int) {
        machine = moveSlot(machine, from, to)
        machine.slots.forEach { persist(it) }
        publish()
    }

    private suspend fun toggleJoint(flag: String) {
        val joints = machine.sessionJoints.toMutableSet()
        if (!joints.add(flag)) joints.remove(flag)
        machine = machine.copy(sessionJoints = joints)
        publish()
    }

    private suspend fun setRpe(setId: Long, rpe: Double?) {
        machine = updateSet(machine, setId) { it.copy(rpe = rpe) }
        val slot = machine.slots.firstOrNull { slot -> slot.sets.any { it.id == setId } } ?: return
        val set = slot.sets.first { it.id == setId }
        workouts.updateRecordedSet(slot.sessionSlotId, set, clock.instant())
        publish()
    }

    private suspend fun acceptRegulation() {
        val working = workoutPosition(machine) as? WorkoutPosition.WorkingSet ?: return
        val prompt = regulationFor(working) ?: return
        val current = working.suggestion.weightKg ?: return
        val weight = regulation.weightAfterDecision(current, prompt.toSuggestion(), accepted = true)
        machine = machine.copy(
            draft = working.suggestion.copy(weightKg = weight),
            acceptedRegulationSetIds = machine.acceptedRegulationSetIds + prompt.sourceSetId,
        )
        publish()
    }

    private suspend fun dismissRegulation() {
        val working = workoutPosition(machine) as? WorkoutPosition.WorkingSet ?: return
        val prompt = regulationFor(working) ?: return
        machine = machine.copy(
            dismissedRegulationSetIds = machine.dismissedRegulationSetIds + prompt.sourceSetId,
        )
        publish()
    }

    private suspend fun toggleShortOnTime() {
        machine = toggleShortOnTimeUseCase.execute(sessionId, machine, originalBounds)
        machine.slots.forEach { persist(it) }
        publish()
    }

    private suspend fun addSet() {
        val slotId = when (val position = workoutPosition(machine)) {
            is WorkoutPosition.WorkingSet -> position.slot.sessionSlotId
            is WorkoutPosition.Resting -> position.slot.sessionSlotId
            else -> return
        }
        machine = addExtraSet(machine)
        val slot = machine.slots.firstOrNull { it.sessionSlotId == slotId } ?: return
        persist(slot)
        publish()
    }

    private suspend fun removeExtra() {
        val slotId = when (val position = workoutPosition(machine)) {
            is WorkoutPosition.WorkingSet -> position.slot.sessionSlotId
            is WorkoutPosition.Resting -> position.slot.sessionSlotId
            else -> return
        }
        val floor = originalBounds[slotId]?.second
            ?: machine.slots.firstOrNull { it.sessionSlotId == slotId }?.prescription?.setsMin
            ?: return
        machine = removeExtraSet(machine, floor)
        val slot = machine.slots.firstOrNull { it.sessionSlotId == slotId } ?: return
        persist(slot)
        publish()
    }

    private suspend fun undo() {
        val pending = handsFree.undoSetId
        val id = handsFree.takeUndo(elapsed.elapsedRealtime())
        if (id == null) {
            if (pending != null) publish()
            return
        }
        workouts.deleteSet(id)
        machine = removeSet(machine, id)
        if (id == lastLoggedSetId) {
            lastLoggedSetId = null
            lastLoggedExerciseId = null
        }
        armed = null
        publish()
    }

    private suspend fun editNotes(notes: String) {
        val slot = currentSlot() ?: return
        if (workouts.findExercise(slot.activeExerciseId) == null) return
        workouts.updateSetupNotes(slot.activeExerciseId, notes)
        machine = machine.copy(
            slots = machine.slots.map { candidate ->
                if (candidate.sessionSlotId == slot.sessionSlotId) candidate.copy(setupNotes = notes) else candidate
            },
        )
        publish()
    }

    private suspend fun complete() {
        if (workouts.findSession(sessionId)?.status == SessionStatus.COMPLETED) {
            if (_uiState.value.position !is WorkoutPosition.Summary && accept(WorkoutPosition.Summary)) {
                _uiState.value = _uiState.value.copy(
                    position = WorkoutPosition.Summary,
                    timer = TimerSnapshot.Idle,
                    undoUntilElapsedRealtime = null,
                    nextUp = null,
                )
            }
            return
        }
        if (!accept(WorkoutPosition.Summary)) return
        val summary = interactors.complete.execute(sessionId, machine)
        armed = null
        timer.stop()
        handsFree.clearUndo()
        machine = machine.copy(
            started = true,
            leftPrep = true,
            leftCooldown = true,
            prep = machine.prep.map { it.copy(done = true) },
            cooldown = machine.cooldown.map { it.copy(done = true) },
        )
        _uiState.value = _uiState.value.copy(
            sessionId = sessionId,
            position = WorkoutPosition.Summary,
            summary = summary,
            timer = TimerSnapshot.Idle,
            undoUntilElapsedRealtime = null,
            nextUp = null,
        )
        runCatching { widget.refresh() }
    }

    private suspend fun publish() {
        val open = workouts.findSession(sessionId)
        if (
            machine.started &&
            open != null &&
            open.status != SessionStatus.COMPLETED &&
            workoutPosition(machine) == WorkoutPosition.Summary
        ) {
            complete()
            if (workouts.findSession(sessionId)?.status == SessionStatus.COMPLETED) return
        }
        val session = workouts.findSession(sessionId)
        val finished = session?.status == SessionStatus.COMPLETED
        val position = if (finished) WorkoutPosition.Summary else workoutPosition(machine)
        if (!accept(position)) return
        val shown = decorate(position)
        if (finished) {
            if (armed != null) {
                armed = null
                timer.stop()
            }
        } else {
            syncTimer(shown)
        }
        val removeFloor = when (val pos = shown) {
            is WorkoutPosition.WorkingSet -> originalBounds[pos.slot.sessionSlotId]?.second
                ?: pos.slot.prescription.setsMin
            is WorkoutPosition.Resting -> originalBounds[pos.slot.sessionSlotId]?.second
                ?: pos.slot.prescription.setsMin
            else -> null
        }
        if (
            _uiState.value.position is WorkoutPosition.Resting &&
            shown !is WorkoutPosition.Resting
        ) {
            lastLoggedSetId = null
            lastLoggedExerciseId = null
        }
        _uiState.value = WorkoutUiState(
            sessionId = sessionId,
            position = shown,
            advice = _uiState.value.advice,
            timer = timer.snapshot.value,
            etaSeconds = eta(),
            shortOnTime = session?.isShortOnTime == true,
            undoUntilElapsedRealtime = handsFree.undoUntilElapsedRealtime,
            handsFreePulse = handsFreePulse,
            cuesEnabled = cuesEnabled,
            jointFlags = machine.sessionJoints,
            summary = _uiState.value.summary,
            nextUp = nextUp(shown),
            canRemoveSet = removeFloor != null && canRemoveExtraSet(machine, removeFloor),
            lastLoggedSetId = lastLoggedSetId,
            lastLoggedExerciseId = lastLoggedExerciseId,
        )
    }

    private fun accept(next: WorkoutPosition, resetSession: Boolean = false): Boolean {
        val from = _uiState.value.position
        if (transitions.allow(from, next, resetSession)) return true
        breadcrumbs.record("rejected ${from::class.simpleName} -> ${next::class.simpleName}")
        return false
    }

    private suspend fun syncTimer(position: WorkoutPosition) {
        val now = elapsed.elapsedRealtime()
        when (position) {
            is WorkoutPosition.Resting -> {
                val key = restKey(position.slot.sessionSlotId, position.round, position.kind)
                if (armed != key) {
                    armed = key
                    timer.start(restTimer(position, now, machine.transitionRestSeconds, cueFor(position.next), cuesEnabled))
                } else {
                    timer.refresh(now)
                }
            }
            is WorkoutPosition.PracticeBlock -> {
                val key = "block:${position.slot.sessionSlotId}"
                if (armed != key) {
                    armed = key
                    timer.start(blockTimer(position.slot, now).copy(cue = position.slot.exerciseName, speak = cuesEnabled))
                } else {
                    timer.refresh(now)
                }
            }
            else -> if (armed != null) {
                armed = null
                timer.stop()
            }
        }
    }

    private suspend fun resumeClock() {
        val stored = preferences.activeTimerEndElapsedRealtime.first() ?: return
        when (val position = workoutPosition(machine)) {
            is WorkoutPosition.Resting -> {
                if (position.kind == RestKind.AS_NEEDED) return
                val (min, max) = restBounds(position)
                val spec = restoredCountdown(stored, min, max)
                if (projectTimer(spec, elapsed.elapsedRealtime()).phase == TimerPhase.FINISHED) {
                    machine = dismissRest(machine)
                    preferences.setActiveTimerEndElapsedRealtime(null)
                } else {
                    armed = restKey(position.slot.sessionSlotId, position.round, position.kind)
                    timer.start(spec.copy(cue = cueFor(position.next), speak = cuesEnabled))
                }
            }
            is WorkoutPosition.PracticeBlock -> {
                val duration = position.slot.prescription.blockDurationSec ?: 0
                val spec = restoredCountdown(stored, duration, duration)
                if (projectTimer(spec, elapsed.elapsedRealtime()).phase == TimerPhase.FINISHED) {
                    preferences.setActiveTimerEndElapsedRealtime(null)
                    endBlock(position)
                } else {
                    armed = "block:${position.slot.sessionSlotId}"
                    timer.start(spec.copy(cue = position.slot.exerciseName, speak = cuesEnabled))
                }
            }
            else -> Unit
        }
    }

    private fun refreshClock() {
        val now = elapsed.elapsedRealtime()
        timer.refresh(now)
        val until = handsFree.undoUntilElapsedRealtime ?: return
        if (now <= until) return
        handsFree.clearUndo()
        _uiState.update { it.copy(undoUntilElapsedRealtime = null) }
    }

    private fun restBounds(position: WorkoutPosition.Resting): Pair<Int, Int> =
        restWindowSeconds(position, machine.transitionRestSeconds)

    private fun cueFor(slot: WorkoutSlot?): String? {
        if (slot == null) return null
        val suggestion = suggested(slot)
        return workoutCue(
            setNumber = slot.sets.size + 1,
            setCount = slot.prescription.setsMax.coerceAtLeast(1),
            exerciseName = slot.exerciseName,
            weightKg = suggestion.weightKg,
            repsLow = slot.prescription.repsLow,
            repsHigh = slot.prescription.repsHigh,
            holdSec = suggestion.holdSec,
        )
    }

    private suspend fun nextUp(position: WorkoutPosition): NextUp? {
        val slot = (position as? WorkoutPosition.Resting)?.next ?: return null
        val suggestion = suggested(slot)
        val weight = suggestion.weightKg
        val inventory = preferences.plateInventoryKg.first()
        val bar = slot.barWeightKg ?: 20.0
        val load = platesPerSide(weight, bar, inventory)
        val warmup = if (weight == null) {
            emptyList()
        } else {
            warmups.ramp(slot.equipment, slot.prescription.category, weight, bar, inventory)
        }
        return NextUp(slot.exerciseName, load, warmup)
    }

    private suspend fun decorate(position: WorkoutPosition): WorkoutPosition {
        val plated = withPlates(position)
        val working = plated as? WorkoutPosition.WorkingSet ?: return plated
        return working.copy(regulation = regulationFor(working))
    }

    private fun regulationFor(working: WorkoutPosition.WorkingSet): RegulationPrompt? {
        val last = machine.slots
            .filter { it.activeExerciseId == working.slot.activeExerciseId }
            .flatMap { it.sets }
            .maxByOrNull { it.id }
            ?: return null
        if (last.id in machine.dismissedRegulationSetIds || last.id in machine.acceptedRegulationSetIds) return null
        val increment = working.slot.prescription.incrementOverrideKg ?: DEFAULT_LOAD_INCREMENT_KG
        val advice = regulation.advise(last.rpe, working.suggestion.weightKg, increment) ?: return null
        return RegulationPrompt(last.id, advice.lastRpe, advice.suggestedWeightKg)
    }

    private fun RegulationPrompt.toSuggestion() = AutoRegulationSuggestion(
        lastRpe = lastRpe,
        suggestedWeightKg = suggestedWeightKg,
    )

    private suspend fun withPlates(position: WorkoutPosition): WorkoutPosition {
        val working = position as? WorkoutPosition.WorkingSet ?: return position
        val inventory = preferences.plateInventoryKg.first()
        val bar = working.slot.barWeightKg ?: 20.0
        return working.copy(requiredPlates = platesPerSide(working.suggestion.weightKg, bar, inventory))
    }

    private fun platesPerSide(weightKg: Double?, barKg: Double, inventoryKg: List<Double>): List<Double> {
        val weight = weightKg ?: return emptyList()
        return plates.load(weight, barKg, inventoryKg).platesPerSideKg
    }

    private fun eta(): Int {
        val pending = machine.slots.mapNotNull { slot ->
            if (slot.skipped) return@mapNotNull null
            val doneRounds = slot.sets.map { it.setNumber }.toSet().size
            val left = (slot.prescription.setsMax - doneRounds).coerceAtLeast(0)
            if (left == 0 && slot.prescription.metricType != MetricType.TIMED_BLOCK) return@mapNotNull null
            if (slot.prescription.metricType == MetricType.TIMED_BLOCK && slot.sets.isNotEmpty()) return@mapNotNull null
            TrainingSlot(slot.sessionSlotId, slot.prescription.copy(setsMax = left, setsMin = minOf(slot.prescription.setsMin, left)))
        }
        return estimator.estimate(pending, machine.transitionRestSeconds)
    }

    private fun suggested(slot: WorkoutSlot) = suggestionFor(
        slot,
        machine.previousByExercise,
        machine.baselineBySessionSlot[slot.sessionSlotId],
        machine.ownHistoryBySessionSlot[slot.sessionSlotId],
    )

    private suspend fun persist(slot: WorkoutSlot) {
        workouts.persistSlot(sessionId, slot)
    }

    private fun currentSlot(): WorkoutSlot? = when (val position = workoutPosition(machine)) {
        is WorkoutPosition.WorkingSet -> position.slot
        is WorkoutPosition.PracticeBlock -> position.slot
        is WorkoutPosition.Resting -> position.slot
        else -> null
    }

    private companion object {
        const val DEFAULT_LOAD_INCREMENT_KG = 2.5
    }
}
