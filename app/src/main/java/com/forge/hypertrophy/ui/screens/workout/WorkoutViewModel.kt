package com.forge.hypertrophy.ui.screens.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
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
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.TrainingSlot
import com.forge.hypertrophy.domain.model.WarmupStep
import com.forge.hypertrophy.domain.usecase.CompleteWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.LoadWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.LogSetUseCase
import com.forge.hypertrophy.domain.usecase.StartWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.ToggleShortOnTimeUseCase
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import com.forge.hypertrophy.domain.workout.HandsFreeGate
import com.forge.hypertrophy.domain.workout.RecordedSet
import com.forge.hypertrophy.domain.workout.RestKind
import com.forge.hypertrophy.domain.workout.TimerCommand
import com.forge.hypertrophy.domain.workout.TimerPhase
import com.forge.hypertrophy.domain.workout.TimerSnapshot
import com.forge.hypertrophy.domain.workout.WorkoutMachineState
import com.forge.hypertrophy.domain.workout.WorkoutPosition
import com.forge.hypertrophy.domain.workout.WorkoutSlot
import com.forge.hypertrophy.domain.workout.WorkoutTimer
import com.forge.hypertrophy.widget.TodayWidgetRefresher
import com.forge.hypertrophy.domain.workout.adjustDraft
import com.forge.hypertrophy.domain.workout.blockTimer
import com.forge.hypertrophy.domain.workout.checkOff
import com.forge.hypertrophy.domain.workout.chooseAlternative
import com.forge.hypertrophy.domain.workout.confirmForm
import com.forge.hypertrophy.domain.workout.dismissRest
import com.forge.hypertrophy.domain.workout.logBlock
import com.forge.hypertrophy.domain.workout.logCurrentSet
import com.forge.hypertrophy.domain.workout.moveSlot
import com.forge.hypertrophy.domain.workout.projectTimer
import com.forge.hypertrophy.domain.workout.removeSet
import com.forge.hypertrophy.domain.workout.restKey
import com.forge.hypertrophy.domain.workout.restTimer
import com.forge.hypertrophy.domain.workout.restoredCountdown
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
import kotlinx.coroutines.launch

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
    val cuesEnabled: Boolean = false,
    val summary: WorkoutSummary? = null,
    val nextUp: NextUp? = null,
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
    data object ToggleShortOnTime : WorkoutEvent
    data object CompleteWorkout : WorkoutEvent
    data object Undo : WorkoutEvent
    data class Tick(val nowElapsedRealtime: Long) : WorkoutEvent
    data class Cues(val enabled: Boolean) : WorkoutEvent
    data class EditSetupNotes(val notes: String) : WorkoutEvent
}

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val preferences: TrainingPreferencesRepository,
    private val timer: WorkoutTimer,
    private val clock: Clock,
    private val elapsed: ElapsedRealtimeClock,
    private val widget: TodayWidgetRefresher,
    private val breadcrumbs: Breadcrumbs,
    private val loadWorkout: LoadWorkoutUseCase,
    private val startWorkout: StartWorkoutUseCase,
    private val completeWorkout: CompleteWorkoutUseCase,
    private val logSet: LogSetUseCase,
    private val toggleShortOnTimeUseCase: ToggleShortOnTimeUseCase,
) : ViewModel() {
    private val requestedSessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L
    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var sessionId: Long = 0L
    private var machine = WorkoutMachineState()
    private var armed: String? = null
    private var cuesEnabled = false
    private val handsFree = HandsFreeGate()
    private val originalBounds = mutableMapOf<Long, Pair<Int, Int>>()
    private val readiness = ReadinessAdvisor()
    private val estimator = SessionEstimator()
    private val plates = PlateCalculator()
    private val warmups = WarmupRampGenerator(plates)

    init {
        if (requestedSessionId != 0L) {
            viewModelScope.launch { restore(requestedSessionId) }
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
        breadcrumbs.record(event.javaClass.simpleName)
        viewModelScope.launch {
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
                WorkoutEvent.ToggleShortOnTime -> toggleShortOnTime()
                WorkoutEvent.CompleteWorkout -> complete()
                WorkoutEvent.Undo -> undo()
                is WorkoutEvent.Tick -> {
                    timer.refresh(event.nowElapsedRealtime)
                    publish()
                }
                is WorkoutEvent.Cues -> {
                    cuesEnabled = event.enabled
                    armed = null
                    publish()
                }
                is WorkoutEvent.EditSetupNotes -> editNotes(event.notes)
            }
        }
    }

    private suspend fun start(dayId: Long) {
        val id = startWorkout.execute(dayId)
        restore(id)
    }

    private suspend fun restore(id: Long) {
        sessionId = id
        machine = loadWorkout.execute(id)
        machine.slots.forEach { slot ->
            originalBounds.putIfAbsent(slot.sessionSlotId, slot.prescription.setsMin to slot.prescription.setsMax)
        }
        resumeClock()
        publish()
    }

    private suspend fun begin(sleep: Int?, soreness: Int?, energy: Int?) {
        val session = sessions.get(sessionId) ?: return
        val advice = readiness.advise(ReadinessCheck(sleep, soreness, energy))
        sessions.update(
            session.copy(
                status = SessionStatus.IN_PROGRESS,
                readinessSleep = sleep,
                readinessSoreness = soreness,
                readinessEnergy = energy,
                startedAt = clock.instant(),
            ),
        )
        machine = machine.copy(started = true)
        _uiState.value = _uiState.value.copy(advice = advice)
        publish()
    }

    private suspend fun primary(method: EntryMethod) {
        val now = elapsed.elapsedRealtime()
        if (method == EntryMethod.HARDWARE_KEY && !handsFree.accept(now)) return
        when (val position = workoutPosition(machine)) {
            is WorkoutPosition.WorkingSet -> logWorkingSet(position, method, now)
            is WorkoutPosition.PracticeBlock -> endBlock(position)
            is WorkoutPosition.Resting -> {
                machine = dismissRest(machine)
                armed = null
                timer.stop()
                publish()
            }
            else -> Unit
        }
    }

    private suspend fun logWorkingSet(position: WorkoutPosition.WorkingSet, method: EntryMethod, now: Long) {
        val recorded = logSet.logWorkingSet(
            sessionSlotId = position.slot.sessionSlotId,
            setNumber = position.setNumber,
            side = position.side,
            suggestion = position.suggestion,
            jointFlags = machine.sessionJoints.toList(),
            method = method
        )
        machine = logCurrentSet(machine, recorded)
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
        val recorded = logSet.logPracticeBlock(position.slot.sessionSlotId, elapsedSeconds)
        machine = logBlock(machine, recorded)
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
        val exercise = exerciseId?.let { exercises.get(it) }
        machine = chooseAlternative(machine, slot.sessionSlotId, exerciseId)
        if (exercise != null) {
            machine = machine.copy(
                slots = machine.slots.map { candidate ->
                    if (candidate.sessionSlotId == slot.sessionSlotId) {
                        candidate.copy(exerciseName = exercise.name, setupNotes = exercise.setupNotes, unilateral = exercise.isUnilateral)
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
        sessions.updateSet(set.toEntity(slot.sessionSlotId))
        publish()
    }

    private suspend fun toggleShortOnTime() {
        machine = toggleShortOnTimeUseCase.execute(sessionId, machine, originalBounds)
        machine.slots.forEach { persist(it) }
        publish()
    }

    private suspend fun undo() {
        val pending = handsFree.undoSetId
        val id = handsFree.takeUndo(elapsed.elapsedRealtime())
        if (id == null) {
            if (pending != null) publish()
            return
        }
        sessions.deleteSet(id)
        machine = removeSet(machine, id)
        armed = null
        publish()
    }

    private suspend fun editNotes(notes: String) {
        val slot = currentSlot() ?: return
        val exercise = exercises.get(slot.activeExerciseId) ?: return
        exercises.update(exercise.copy(setupNotes = notes))
        machine = machine.copy(
            slots = machine.slots.map { candidate ->
                if (candidate.sessionSlotId == slot.sessionSlotId) candidate.copy(setupNotes = notes) else candidate
            },
        )
        publish()
    }

    private suspend fun complete() {
        val summary = completeWorkout.execute(sessionId, machine)
        armed = null
        timer.stop()
        machine = machine.copy(started = true, prep = machine.prep.map { it.copy(done = true) }, cooldown = machine.cooldown.map { it.copy(done = true) })
        _uiState.value = _uiState.value.copy(
            sessionId = sessionId,
            position = WorkoutPosition.Summary,
            summary = summary,
            timer = TimerSnapshot.Idle,
            nextUp = null,
        )
        runCatching { widget.refresh() }
    }

    private suspend fun publish() {
        val session = sessions.get(sessionId)
        val finished = session?.status == SessionStatus.COMPLETED
        val position = if (finished) WorkoutPosition.Summary else workoutPosition(machine)
        if (finished) {
            if (armed != null) {
                armed = null
                timer.stop()
            }
        } else {
            syncTimer(position)
        }
        _uiState.value = WorkoutUiState(
            sessionId = sessionId,
            position = position,
            advice = _uiState.value.advice,
            timer = timer.snapshot.value,
            etaSeconds = eta(),
            shortOnTime = session?.isShortOnTime == true,
            undoUntilElapsedRealtime = handsFree.undoUntilElapsedRealtime,
            cuesEnabled = cuesEnabled,
            summary = _uiState.value.summary,
            nextUp = nextUp(position),
        )
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

    private fun restBounds(position: WorkoutPosition.Resting): Pair<Int, Int> = when (position.kind) {
        RestKind.TRANSITION -> machine.transitionRestSeconds to machine.transitionRestSeconds
        RestKind.AS_NEEDED -> 0 to 0
        RestKind.BETWEEN_SETS -> {
            val min = position.slot.prescription.restMinSec ?: 0
            min to (position.slot.prescription.restMaxSec ?: min)
        }
    }

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
        val load = if (weight == null) emptyList() else plates.load(weight, bar, inventory).platesPerSideKg
        val warmup = if (weight == null) {
            emptyList()
        } else {
            warmups.ramp(slot.equipment, slot.prescription.category, weight, bar, inventory)
        }
        return NextUp(slot.exerciseName, load, warmup)
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
        val entity = sessions.observeSlots(sessionId).first().firstOrNull { it.id == slot.sessionSlotId } ?: return
        sessions.updateSlot(
            entity.copy(
                prescriptionSnapshot = entity.prescriptionSnapshot.copy(
                    sortOrder = slot.sortOrder,
                    setsMin = slot.prescription.setsMin,
                    setsMax = slot.prescription.setsMax,
                ),
                chosenAlternativeExerciseId = slot.chosenAlternativeExerciseId,
                skipped = slot.skipped,
                skipReason = slot.skipReason,
                formConfirmed = slot.formConfirmed,
            ),
        )
    }

    private fun currentSlot(): WorkoutSlot? = when (val position = workoutPosition(machine)) {
        is WorkoutPosition.WorkingSet -> position.slot
        is WorkoutPosition.PracticeBlock -> position.slot
        is WorkoutPosition.Resting -> position.slot
        else -> null
    }

    private fun RecordedSet.toEntity(sessionSlotId: Long) = SetEntryEntity(
        id = id,
        sessionSlotId = sessionSlotId,
        setNumber = setNumber,
        side = side,
        setType = SetType.WORKING,
        weightKg = weightKg,
        reps = reps,
        holdSec = holdSec,
        rpe = rpe,
        jointFlags = jointFlags,
        entryMethod = entryMethod,
        loggedAt = clock.instant(),
    )
}
