package com.forge.hypertrophy.ui.screens.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.SkillProgressEntity
import com.forge.hypertrophy.data.entity.SkillStageEventEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.engine.DoubleProgressionEngine
import com.forge.hypertrophy.domain.engine.PlateCalculator
import com.forge.hypertrophy.domain.engine.LiftSample
import com.forge.hypertrophy.domain.engine.PrDetector
import com.forge.hypertrophy.domain.engine.ReadinessAdvice
import com.forge.hypertrophy.domain.engine.ReadinessAdvisor
import com.forge.hypertrophy.domain.engine.ReadinessCheck
import com.forge.hypertrophy.domain.engine.SessionEstimator
import com.forge.hypertrophy.domain.engine.ShortOnTimePlanner
import com.forge.hypertrophy.domain.engine.StaticSkillEngine
import com.forge.hypertrophy.domain.engine.WarmupRampGenerator
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.LoggedSet
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionAction
import com.forge.hypertrophy.domain.model.ProgressionInput
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SkillPosition
import com.forge.hypertrophy.domain.model.SkillStageTargets
import com.forge.hypertrophy.domain.model.SlotPrescription
import com.forge.hypertrophy.domain.model.SlotSession
import com.forge.hypertrophy.domain.model.TrainingSlot
import com.forge.hypertrophy.domain.model.WarmupStep
import com.forge.hypertrophy.domain.workout.BaselineHint
import com.forge.hypertrophy.domain.workout.ChecklistStep
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import com.forge.hypertrophy.domain.workout.ExerciseChoice
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
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val SKIPPED_REASON = "skipped"
const val SHORT_ON_TIME_REASON = "short on time"

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
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val programs: ProgramRepository,
    private val skills: SkillRepository,
    private val preferences: TrainingPreferencesRepository,
    private val timer: WorkoutTimer,
    private val clock: Clock,
    private val elapsed: ElapsedRealtimeClock,
    private val widget: TodayWidgetRefresher,
    private val baselines: BaselineRepository,
    private val breadcrumbs: Breadcrumbs,
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
    private val planner = ShortOnTimePlanner(estimator)
    private val plates = PlateCalculator()
    private val warmups = WarmupRampGenerator(plates)
    private val prs = PrDetector()
    private val progression = DoubleProgressionEngine()
    private val skillEngine = StaticSkillEngine()

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
        val day = routines.getDay(dayId) ?: return
        val program = programs.getById(day.programId)
        val id = sessions.insert(
            WorkoutSessionEntity(
                date = clock.instant().atZone(clock.zone).toLocalDate(),
                dayId = dayId,
                kind = SessionKind.GYM,
                status = SessionStatus.PLANNED,
                isDeload = program?.deloadActive == true,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = null,
                completedAt = null,
            ),
        )
        routines.observeSlots(dayId).first().forEach { slot ->
            sessions.insertSlot(
                SessionSlotEntity(
                    sessionId = id,
                    slotId = slot.id,
                    prescriptionSnapshot = slot.toSnapshot(),
                    chosenAlternativeExerciseId = null,
                    skipped = false,
                    skipReason = null,
                    formConfirmed = null,
                ),
            )
        }
        restore(id)
    }

    private suspend fun restore(id: Long) {
        sessionId = id
        machine = load(id)
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
            is WorkoutPosition.WorkingSet -> logSet(position, method, now)
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

    private suspend fun logSet(position: WorkoutPosition.WorkingSet, method: EntryMethod, now: Long) {
        val suggestion = position.suggestion
        val id = sessions.insertSet(
            SetEntryEntity(
                sessionSlotId = position.slot.sessionSlotId,
                setNumber = position.setNumber,
                side = position.side,
                setType = SetType.WORKING,
                weightKg = suggestion.weightKg,
                reps = suggestion.reps,
                holdSec = suggestion.holdSec,
                rpe = null,
                jointFlags = machine.sessionJoints.toList(),
                entryMethod = method,
                loggedAt = clock.instant(),
            ),
        )
        machine = logCurrentSet(
            machine,
            RecordedSet(
                id = id,
                setNumber = position.setNumber,
                side = position.side,
                weightKg = suggestion.weightKg,
                reps = suggestion.reps,
                holdSec = suggestion.holdSec,
                rpe = null,
                jointFlags = machine.sessionJoints.toList(),
                entryMethod = method,
            ),
        )
        if (method == EntryMethod.HARDWARE_KEY) handsFree.armUndo(id, now)
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
        val id = sessions.insertSet(
            SetEntryEntity(
                sessionSlotId = position.slot.sessionSlotId,
                setNumber = 1,
                side = SetSide.BOTH,
                setType = SetType.WORKING,
                weightKg = null,
                reps = null,
                holdSec = elapsedSeconds,
                rpe = null,
                jointFlags = emptyList(),
                entryMethod = EntryMethod.SCREEN,
                loggedAt = clock.instant(),
            ),
        )
        machine = logBlock(
            machine,
            RecordedSet(id, 1, SetSide.BOTH, null, null, elapsedSeconds, null, emptyList(), EntryMethod.SCREEN),
        )
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
        val session = sessions.get(sessionId) ?: return
        val enabling = !session.isShortOnTime
        if (enabling) {
            val pending = machine.slots.filter { it.sets.isEmpty() && !it.skipped }
            val asTraining = pending.map { TrainingSlot(it.sessionSlotId, it.prescription) }
            val estimate = estimator.estimate(asTraining, machine.transitionRestSeconds)
            val planned = planner.plan(asTraining, (estimate / 2).coerceAtLeast(1), machine.transitionRestSeconds)
            val plannedById = planned.associateBy { it.id }
            machine = machine.copy(
                slots = machine.slots.map { slot ->
                    if (slot.sets.isNotEmpty() || slot.skipped) return@map slot
                    val kept = plannedById[slot.sessionSlotId]
                    if (kept == null) {
                        slot.copy(skipped = true, skipReason = SHORT_ON_TIME_REASON)
                    } else {
                        slot.copy(prescription = kept.prescription)
                    }
                },
            )
        } else {
            machine = machine.copy(
                slots = machine.slots.map { slot ->
                    val bounds = originalBounds[slot.sessionSlotId]
                    val restored = if (bounds == null) {
                        slot.prescription
                    } else {
                        slot.prescription.copy(setsMin = bounds.first, setsMax = bounds.second)
                    }
                    if (slot.skipReason == SHORT_ON_TIME_REASON) {
                        slot.copy(skipped = false, skipReason = null, prescription = restored)
                    } else {
                        slot.copy(prescription = restored)
                    }
                },
            )
        }
        machine.slots.forEach { persist(it) }
        sessions.update(session.copy(isShortOnTime = enabling))
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
        val session = sessions.get(sessionId) ?: return
        val now = clock.instant()
        val summary = summarize()
        sessions.update(session.copy(status = SessionStatus.COMPLETED, completedAt = now))
        sealCalibration(now)
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

    /** The skipped slot's first logged sets become its baseline and are not progressed. */
    private suspend fun sealCalibration(at: Instant) {
        for (slot in machine.slots) {
            val routineId = slot.routineSlotId ?: continue
            val hint = machine.baselineBySessionSlot[slot.sessionSlotId] ?: continue
            if (!hint.awaitingCalibration) continue
            val best = slot.sets.filter { it.weightKg != null }.maxByOrNull { it.weightKg ?: 0.0 } ?: continue
            val existing = baselines.forSlot(routineId) ?: continue
            baselines.save(existing.copy(weightKg = best.weightKg, repsHint = best.reps, setAt = at))
        }
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

    private suspend fun load(id: Long): WorkoutMachineState {
        val session = sessions.get(id) ?: return WorkoutMachineState()
        val stored = sessions.observeSlots(id).first()
        val workoutSlots = stored.map { toWorkoutSlot(it) }.sortedBy { it.sortOrder }
        workoutSlots.forEach { slot ->
            originalBounds.putIfAbsent(slot.sessionSlotId, slot.prescription.setsMin to slot.prescription.setsMax)
        }
        val progressed = workoutSlots.any { it.skipped || it.sets.isNotEmpty() } || session.status == SessionStatus.COMPLETED
        val checklist = session.dayId?.let { routines.observeChecklist(it).first() }.orEmpty()
        return WorkoutMachineState(
            slots = workoutSlots,
            prep = checklist.filter { it.phase == ChecklistPhase.PREP }.map { ChecklistStep(it.id, it.text, done = progressed) },
            cooldown = checklist.filter { it.phase == ChecklistPhase.COOLDOWN }.map {
                ChecklistStep(it.id, it.text, done = session.status == SessionStatus.COMPLETED)
            },
            started = session.startedAt != null,
            transitionRestSeconds = preferences.transitionRestSeconds.first(),
            previousByExercise = previousSets(id, workoutSlots),
            ownHistoryBySessionSlot = ownHistory(id, workoutSlots),
            baselineBySessionSlot = baselineHints(workoutSlots),
            sessionJoints = workoutSlots.flatMap { slot -> slot.sets.flatMap { it.jointFlags } }.toSet(),
        )
    }

    private fun suggested(slot: WorkoutSlot) = suggestionFor(
        slot,
        machine.previousByExercise,
        machine.baselineBySessionSlot[slot.sessionSlotId],
        machine.ownHistoryBySessionSlot[slot.sessionSlotId],
    )

    private suspend fun toWorkoutSlot(entity: SessionSlotEntity): WorkoutSlot {
        val prescription = entity.prescriptionSnapshot
        val exerciseId = entity.chosenAlternativeExerciseId ?: prescription.exerciseId
        val exercise = exercises.get(exerciseId)
        val routineId = entity.slotId
        val alternatives = if (routineId == null) {
            emptyList()
        } else {
            routines.observeAlternatives(routineId).first().mapNotNull { alternative ->
                exercises.get(alternative.exerciseId)?.let { ExerciseChoice(it.id, it.name) }
            }
        }
        return WorkoutSlot(
            sessionSlotId = entity.id,
            routineSlotId = entity.slotId,
            sortOrder = prescription.sortOrder,
            prescription = prescription,
            exerciseName = exercise?.name.orEmpty(),
            setupNotes = exercise?.setupNotes.orEmpty(),
            unilateral = exercise?.isUnilateral == true,
            equipment = exercise?.equipment ?: com.forge.hypertrophy.domain.model.Equipment.BARBELL,
            barWeightKg = exercise?.barWeightKg,
            alternatives = alternatives,
            skipped = entity.skipped,
            skipReason = entity.skipReason,
            chosenAlternativeExerciseId = entity.chosenAlternativeExerciseId,
            formConfirmed = entity.formConfirmed == true,
            sets = sessions.sets(entity.id).map { it.toRecorded() },
        )
    }

    private suspend fun previousSets(sessionId: Long, slots: List<WorkoutSlot>): Map<Long, RecordedSet> {
        val wanted = slots.map { it.activeExerciseId }.toSet()
        val best = mutableMapOf<Long, Pair<Long, RecordedSet>>()
        sessions.allSlots().filter { it.sessionId != sessionId }.forEach { slot ->
            val exerciseId = slot.chosenAlternativeExerciseId ?: slot.prescriptionSnapshot.exerciseId
            if (exerciseId !in wanted) return@forEach
            val last = sessions.sets(slot.id).lastOrNull { it.setType == SetType.WORKING || it.setType == SetType.AMRAP } ?: return@forEach
            val current = best[exerciseId]
            if (current == null || slot.sessionId > current.first) best[exerciseId] = slot.sessionId to last.toRecorded()
        }
        return best.mapValues { it.value.second }
    }

    private suspend fun ownHistory(sessionId: Long, slots: List<WorkoutSlot>): Map<Long, RecordedSet> {
        val byRoutine = slots.filter { it.routineSlotId != null }.groupBy { it.routineSlotId }
        val best = mutableMapOf<Long, Pair<Long, RecordedSet>>()
        sessions.allSlots().filter { it.sessionId != sessionId && it.slotId != null }.forEach { stored ->
            val routineId = stored.slotId ?: return@forEach
            if (routineId !in byRoutine) return@forEach
            val last = sessions.sets(stored.id).lastOrNull { it.setType == SetType.WORKING || it.setType == SetType.AMRAP }
                ?: return@forEach
            val current = best[routineId]
            if (current == null || stored.sessionId > current.first) best[routineId] = stored.sessionId to last.toRecorded()
        }
        val result = mutableMapOf<Long, RecordedSet>()
        for ((routineId, pair) in best) {
            byRoutine[routineId].orEmpty().forEach { slot -> result[slot.sessionSlotId] = pair.second }
        }
        return result
    }

    private suspend fun baselineHints(slots: List<WorkoutSlot>): Map<Long, BaselineHint> {
        val rows = baselines.forSlots(slots.mapNotNull { it.routineSlotId }).associateBy { it.slotId }
        return slots.mapNotNull { slot ->
            val routineId = slot.routineSlotId ?: return@mapNotNull null
            val row = rows[routineId] ?: return@mapNotNull null
            slot.sessionSlotId to BaselineHint(row.weightKg, row.repsHint, row.weightKg == null)
        }.toMap()
    }

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

    private suspend fun summarize(): WorkoutSummary {
        val history = historySamples()
        val prNotes = machine.slots.flatMap { slot ->
            slot.sets.mapNotNull { set ->
                val candidate = LiftSample(slot.activeExerciseId, slot.sessionSlotId, set.weightKg, set.reps)
                if (!prs.isNewPr(history, candidate)) return@mapNotNull null
                val estimate = com.forge.hypertrophy.domain.engine.E1rmCalculator.epley(set.weightKg ?: return@mapNotNull null, set.reps ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                PrNote(slot.exerciseName, com.forge.hypertrophy.domain.engine.LoadRounding.roundToDecimals(estimate))
            }
        }
        return WorkoutSummary(
            prs = prNotes,
            stagePrompts = stagePrompts(),
            nextSession = nextSessionNotes(),
        )
    }

    private suspend fun historySamples(): List<LiftSample> {
        val samples = mutableListOf<LiftSample>()
        sessions.allSlots().filter { it.sessionId != sessionId }.forEach { slot ->
            val exerciseId = slot.chosenAlternativeExerciseId ?: slot.prescriptionSnapshot.exerciseId
            sessions.sets(slot.id).forEach { set ->
                samples += LiftSample(exerciseId, slot.id, set.weightKg, set.reps)
            }
        }
        return samples
    }

    private suspend fun stagePrompts(): List<StagePrompt> {
        val prompts = mutableListOf<StagePrompt>()
        machine.slots.filter { it.prescription.category == com.forge.hypertrophy.domain.model.SlotCategory.SKILL && it.formConfirmed }.forEach { slot ->
            val exercise = exercises.get(slot.activeExerciseId) ?: return@forEach
            val skillId = exercise.skillId ?: return@forEach
            val steps = skills.getSteps(skillId)
            if (steps.isEmpty()) return@forEach
            val progress = skills.getProgress(skillId)
            val currentStep = steps.firstOrNull { it.id == progress?.currentStepId } ?: steps.first()
            val tier = steps.indexOf(currentStep).coerceAtLeast(0)
            val stage = progress?.stage ?: 1
            val total = slot.sets.sumOf { it.holdSec ?: 0 }
            val unbroken = slot.sets.maxOfOrNull { it.holdSec ?: 0 } ?: 0
            val next = skillEngine.afterAttempt(
                SkillPosition(tier, stage),
                SkillStageTargets(
                    currentStep.stage1TotalSec,
                    currentStep.stage2TotalLowSec,
                    currentStep.stage2TotalHighSec,
                    currentStep.stage3UnbrokenSec,
                ),
                total,
                unbroken,
                formConfirmed = true,
                tierCount = steps.size,
            )
            if (next.tierIndex == tier && next.stage == stage) return@forEach
            val step = steps.getOrElse(next.tierIndex) { steps.last() }
            val now = clock.instant()
            skills.upsertProgress(
                SkillProgressEntity(
                    id = progress?.id ?: 0,
                    skillId = skillId,
                    currentStepId = step.id,
                    stage = next.stage,
                    updatedAt = now,
                ),
            )
            skills.recordStageEvent(
                SkillStageEventEntity(
                    skillId = skillId,
                    date = now.atZone(clock.zone).toLocalDate(),
                    fromTier = tier,
                    fromStage = stage,
                    toTier = next.tierIndex,
                    toStage = next.stage,
                    recordedAt = now,
                ),
            )
            prompts += StagePrompt(skills.get(skillId)?.name.orEmpty(), next.tierIndex, next.stage)
        }
        return prompts
    }

    private fun nextSessionNotes(): List<NextSessionNote> = machine.slots.mapNotNull { slot ->
        val low = slot.prescription.repsLow ?: return@mapNotNull null
        val high = slot.prescription.repsHigh ?: return@mapNotNull null
        if (slot.sets.isEmpty()) return@mapNotNull null
        val hint = machine.baselineBySessionSlot[slot.sessionSlotId]
        val awaiting = hint?.awaitingCalibration == true
        val loggedWeight = slot.sets.mapNotNull { it.weightKg }.maxOrNull()
        val suggestion = progression.suggest(
            ProgressionInput(
                rule = slot.prescription.progressionRule,
                equipment = slot.equipment,
                metricType = slot.prescription.metricType,
                repsLow = low,
                repsHigh = high,
                exerciseIncrementKg = 2.5,
                incrementOverrideKg = slot.prescription.incrementOverrideKg,
                slotSessions = listOf(
                    SlotSession(
                        slot.sets.map { LoggedSet(it.weightKg, it.reps, SetType.WORKING) },
                        calibration = awaiting,
                    ),
                ),
                latestWeightFromAnySlotKg = loggedWeight,
                baselineWeightKg = if (awaiting) loggedWeight else hint?.weightKg,
                awaitingCalibration = awaiting && loggedWeight == null,
            ),
        )
        NextSessionNote(slot.exerciseName, suggestion.action, suggestion.weightKg)
    }

    private suspend fun SetEntryEntity.toRecorded() = RecordedSet(
        id = id,
        setNumber = setNumber,
        side = side,
        weightKg = weightKg,
        reps = reps,
        holdSec = holdSec,
        rpe = rpe,
        jointFlags = jointFlags,
        entryMethod = entryMethod,
    )

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

private fun RoutineSlotEntity.toSnapshot() = SlotPrescription(
    exerciseId = exerciseId,
    category = category,
    sortOrder = sortOrder,
    supersetGroup = supersetGroup,
    metricType = metricType,
    setsMin = setsMin,
    setsMax = setsMax,
    repsLow = repsLow,
    repsHigh = repsHigh,
    isAmrap = isAmrap,
    holdTargetSec = holdTargetSec,
    blockDurationSec = blockDurationSec,
    restMinSec = restMinSec,
    restMaxSec = restMaxSec,
    restAsNeeded = restAsNeeded,
    isOptional = isOptional,
    skipReasonLabel = skipReasonLabel,
    targetSkillStepId = targetSkillStepId,
    progressionRule = progressionRule,
    incrementOverrideKg = incrementOverrideKg,
    notes = notes,
    holdTargetMaxSec = holdTargetMaxSec,
)
