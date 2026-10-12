package com.forge.hypertrophy.ui.screens.dayplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.schedule.ScheduleLoader
import com.forge.hypertrophy.data.schedule.ScheduleReconciler
import com.forge.hypertrophy.domain.dayplan.DayPlanChecklistItem
import com.forge.hypertrophy.domain.dayplan.DayPlanCtaResolver
import com.forge.hypertrophy.domain.dayplan.DayPlanSectionBuilder
import com.forge.hypertrophy.domain.dayplan.DayPlanSectionKind
import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.exercise.ExerciseCategoryAffinity
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.usecase.DayPlanCommitment
import com.forge.hypertrophy.domain.usecase.ResolveDayPlanUseCase
import com.forge.hypertrophy.domain.usecase.ScheduleEdit
import com.forge.hypertrophy.domain.usecase.StartWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.SwapWithTodayUseCase
import com.forge.hypertrophy.domain.usecase.TakeRestNowUseCase

import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class DayPlanViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val preferences: TrainingPreferencesRepository,
    private val cursor: ScheduleCursorRepository,
    private val clock: Clock,
    private val startWorkout: StartWorkoutUseCase,
    private val reconciler: ScheduleReconciler,
) : ViewModel() {
    private val loader = ScheduleLoader(programs, routines, sessions, preferences, cursor)
    private val resolveDay = ResolveDayPlanUseCase(clock)
    private val swapWithToday = SwapWithTodayUseCase(clock, StreakCalculator())
    private val takeRest = TakeRestNowUseCase(clock, StreakCalculator())
    private val gate = Mutex()
    private var startInFlight = false

    private val _uiState = MutableStateFlow(DayPlanUiState())
    val uiState: StateFlow<DayPlanUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun onEvent(event: DayPlanEvent) {
        when (event) {
            is DayPlanEvent.SelectDate -> viewModelScope.launch {
                _uiState.update { it.copy(selectedDate = event.date) }
                refresh()
            }
            is DayPlanEvent.ToggleSection -> {
                _uiState.update { state ->
                    val next = state.collapsedSections.toMutableSet()
                    if (!next.add(event.kindKey)) next.remove(event.kindKey)
                    state.copy(collapsedSections = next)
                }
            }
            DayPlanEvent.PrimaryAction -> viewModelScope.launch { onPrimary() }
            DayPlanEvent.OpenedSession -> {
                startInFlight = false
                _uiState.update { it.copy(sessionToOpen = null) }
            }
            DayPlanEvent.OpenedSlotEditor -> {
                _uiState.update { it.copy(slotToOpen = null) }
            }
            DayPlanEvent.Refresh -> viewModelScope.launch { refresh() }
            is DayPlanEvent.OpenDayEditor, is DayPlanEvent.OpenSlotEditor -> Unit
            is DayPlanEvent.AddSlotInCategory -> viewModelScope.launch {
                addSlotInCategory(event.kindKey)
            }
            is DayPlanEvent.MoveSlot -> viewModelScope.launch {
                moveSlot(event.slotId, event.delta)
            }
            is DayPlanEvent.RemoveSlot -> viewModelScope.launch {
                removeSlot(event.slotId)
            }
        }
    }

    private suspend fun addSlotInCategory(kindKey: String) {
        if (!_uiState.value.editable) return
        val dayId = _uiState.value.dayId ?: return
        val category = kindKeyToCategory(kindKey) ?: return
        val library = exercises.observeActive().first()
        val match = library.firstOrNull { exercise ->
            ExerciseCategoryAffinity.matches(
                category = category,
                name = exercise.name,
                skillId = exercise.skillId,
                primaryMuscles = exercise.primaryMuscleGroups,
            )
        } ?: library.firstOrNull() ?: return
        val slots = routines.observeSlots(dayId).first()
        val defaults = defaultsFor(category)
        val slotId = routines.insertSlot(
            RoutineSlotEntity(
                dayId = dayId,
                exerciseId = match.id,
                category = category,
                sortOrder = slots.size,
                supersetGroup = null,
                metricType = defaults.metricType,
                setsMin = defaults.setsMin,
                setsMax = defaults.setsMax,
                repsLow = defaults.repsLow,
                repsHigh = defaults.repsHigh,
                isAmrap = false,
                holdTargetSec = defaults.holdTargetSec,
                blockDurationSec = defaults.blockDurationSec,
                restMinSec = defaults.restMinSec,
                restMaxSec = defaults.restMaxSec,
                restAsNeeded = false,
                isOptional = false,
                skipReasonLabel = null,
                targetSkillStepId = null,
                progressionRule = ProgressionRule.DOUBLE,
                incrementOverrideKg = null,
            ),
        )
        _uiState.update { it.copy(slotToOpen = slotId) }
        refresh()
    }

    private fun kindKeyToCategory(kindKey: String): SlotCategory? = when (
        runCatching { DayPlanSectionKind.valueOf(kindKey) }.getOrNull()
    ) {
        DayPlanSectionKind.PREP -> SlotCategory.PREP
        DayPlanSectionKind.MOBILITY -> SlotCategory.MOBILITY
        DayPlanSectionKind.SKILL -> SlotCategory.SKILL
        DayPlanSectionKind.COMPOUND -> SlotCategory.COMPOUND
        DayPlanSectionKind.ISOLATION -> SlotCategory.ISOLATION
        DayPlanSectionKind.CORE -> SlotCategory.CORE
        DayPlanSectionKind.CARDIO -> SlotCategory.CARDIO
        DayPlanSectionKind.COOLDOWN -> SlotCategory.COOLDOWN
        DayPlanSectionKind.OTHER, null -> null
    }

    private data class SlotDefaults(
        val metricType: MetricType,
        val setsMin: Int,
        val setsMax: Int,
        val repsLow: Int?,
        val repsHigh: Int?,
        val holdTargetSec: Int?,
        val blockDurationSec: Int?,
        val restMinSec: Int?,
        val restMaxSec: Int?,
    )

    private fun defaultsFor(category: SlotCategory): SlotDefaults = when (category) {
        SlotCategory.SKILL -> SlotDefaults(
            metricType = MetricType.TIMED_BLOCK,
            setsMin = 1,
            setsMax = 1,
            repsLow = null,
            repsHigh = null,
            holdTargetSec = null,
            blockDurationSec = 600,
            restMinSec = 60,
            restMaxSec = 90,
        )
        SlotCategory.PREP, SlotCategory.MOBILITY, SlotCategory.COOLDOWN -> SlotDefaults(
            metricType = MetricType.REPS,
            setsMin = 1,
            setsMax = 1,
            repsLow = 10,
            repsHigh = 10,
            holdTargetSec = null,
            blockDurationSec = null,
            restMinSec = 0,
            restMaxSec = 30,
        )
        SlotCategory.CORE -> SlotDefaults(
            metricType = MetricType.REPS,
            setsMin = 3,
            setsMax = 3,
            repsLow = 8,
            repsHigh = 12,
            holdTargetSec = null,
            blockDurationSec = null,
            restMinSec = 60,
            restMaxSec = 90,
        )
        else -> SlotDefaults(
            metricType = MetricType.WEIGHT_REPS,
            setsMin = 3,
            setsMax = 3,
            repsLow = 8,
            repsHigh = 12,
            holdTargetSec = null,
            blockDurationSec = null,
            restMinSec = 90,
            restMaxSec = 120,
        )
    }

    private suspend fun moveSlot(slotId: Long, delta: Int) {
        if (!_uiState.value.editable) return
        val dayId = _uiState.value.dayId ?: return
        val ordered = routines.observeSlots(dayId).first().sortedBy { it.sortOrder }
        val index = ordered.indexOfFirst { it.id == slotId }
        if (index < 0) return
        val category = ordered[index].category
        val sameCategory = ordered.mapIndexedNotNull { i, slot ->
            i.takeIf { slot.category == category }
        }
        val position = sameCategory.indexOf(index)
        val targetPosition = position + delta
        if (targetPosition !in sameCategory.indices) return
        val targetIndex = sameCategory[targetPosition]
        val ids = ordered.map { it.id }.toMutableList()
        val temp = ids[index]
        ids[index] = ids[targetIndex]
        ids[targetIndex] = temp
        routines.reorderSlots(dayId, ids)
        refresh()
    }

    private suspend fun removeSlot(slotId: Long) {
        if (!_uiState.value.editable) return
        routines.deleteSlot(slotId)
        refresh()
    }

    private suspend fun onPrimary() {
        val state = _uiState.value
        when (state.cta.kind) {
            com.forge.hypertrophy.domain.dayplan.DayPlanCtaKind.START_WORKOUT -> startOrResume()
            com.forge.hypertrophy.domain.dayplan.DayPlanCtaKind.TAKE_REST -> applySchedule { takeRest.apply(it) }
            com.forge.hypertrophy.domain.dayplan.DayPlanCtaKind.SWAP_WITH_TODAY -> {
                val date = state.selectedDate
                applySchedule { swapWithToday.apply(it, date) }
            }
            else -> Unit
        }
    }

    private suspend fun startOrResume() {
        val state = _uiState.value
        state.activeSessionId?.let { id ->
            _uiState.update { it.copy(sessionToOpen = id) }
            return
        }
        val dayId = state.dayId ?: return
        if (startInFlight || state.isRestDay) return
        startInFlight = true
        val id = try {
            startWorkout.execute(dayId)
        } catch (_: IllegalArgumentException) {
            startInFlight = false
            return
        }
        _uiState.update { it.copy(sessionToOpen = id) }
    }

    private suspend fun applySchedule(edit: (ScheduleSnapshot) -> ScheduleEdit) {
        gate.withLock {
            val loaded = loader.load() ?: return
            when (val result = edit(loaded.snapshot)) {
                ScheduleEdit.Rejected -> Unit
                is ScheduleEdit.Applied -> {
                    persist(loaded.program, loaded.days, result.snapshot)
                    if (clock.instant().atZone(clock.zone).toLocalDate() in result.snapshot.autoCompletedRests) {
                        recordRest(loaded.program, result.snapshot)
                    }
                    refresh()
                }
            }
        }
    }

    private suspend fun persist(program: ProgramEntity, days: List<RoutineDayEntity>, snapshot: ScheduleSnapshot) {
        if (program.rollingSequence != snapshot.rollingIndex) {
            programs.update(program.copy(rollingSequence = snapshot.rollingIndex))
        }
        val ordered = snapshot.orderedDays().map { it.id }
        val current = days.sortedBy { it.sequenceIndex }.map { it.id }
        if (ordered.toSet() == current.toSet() && ordered != current) {
            routines.reorderDays(program.id, ordered)
        }
        cursor.save(
            fixedSwaps = snapshot.fixedSwaps,
            rollingDayByDate = snapshot.rollingDayByDate,
            autoCompletedRests = snapshot.autoCompletedRests,
        )
        preferences.setLastReconciledDate(snapshot.lastReconciled)
    }

    private suspend fun recordRest(program: ProgramEntity, snapshot: ScheduleSnapshot) {
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        if (sessions.completedDays().any { it.date == today }) return
        val rest = snapshot.dayOn(today, today)
        sessions.insert(
            WorkoutSessionEntity(
                date = today,
                dayId = rest?.id,
                kind = SessionKind.REST,
                status = SessionStatus.COMPLETED,
                isDeload = program.deloadActive,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = null,
                completedAt = clock.instant(),
            ),
        )
    }

    private suspend fun refresh() {
        runCatching { reconciler.reconcile() }
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val selectedDate = when {
            _uiState.value.dateStrip.isEmpty() -> today
            else -> _uiState.value.selectedDate
        }
        val program = programs.observeActive().first()
        if (program == null) {
            _uiState.update {
                DayPlanUiState(
                    today = today,
                    selectedDate = selectedDate,
                    dateStrip = dateStrip(today, selectedDate),
                    loading = false,
                )
            }
            return
        }
        val loaded = loader.load(program) ?: return
        val transition = preferences.transitionRestSeconds.first()
        val resolved = resolveDay.resolve(loaded.snapshot, selectedDate, transition)
        val day = resolved.day
        val allExercises = exercises.all()
        val exerciseNames = allExercises.associate { it.id to it.name }
        val exerciseNotes = allExercises.associate { it.id to it.setupNotes }
        val dayEntity = day?.let { d -> loaded.days.firstOrNull { it.id == d.id } }
        val checklist = if (day != null) {
            routines.observeChecklist(day.id).first().map {
                DayPlanChecklistItem(it.id, it.phase, it.text, it.reps, it.seconds)
            }
        } else {
            emptyList()
        }
        val sections = if (day != null && !day.isRest) {
            DayPlanSectionBuilder.build(
                slots = day.slots,
                checklist = checklist,
                exerciseNames = exerciseNames,
                exerciseNotes = exerciseNotes,
                transitionRestSeconds = transition,
                prepDurationMin = dayEntity?.prepDurationMin,
                cooldownDurationMin = dayEntity?.cooldownDurationMin,
            )
        } else {
            emptyList()
        }
        val openToday = sessions.observeInProgress().first()
            .firstOrNull { it.kind == SessionKind.GYM && it.date == today }
        val cta = DayPlanCtaResolver.resolve(
            selectedDate = selectedDate,
            today = today,
            commitment = resolved.commitment,
            isRest = day?.isRest == true,
            mode = loaded.snapshot.mode,
            hasActiveSession = openToday != null && selectedDate == today,
            swapPeerWeekday = null,
        )
        val trainingDays = loaded.snapshot.days.count { !it.isRest }
        val editable = resolved.commitment == DayPlanCommitment.COMMITTED && day != null && !day.isRest
        val declaredPrep = (dayEntity?.prepDurationMin ?: 0) * 60
        val declaredCooldown = (dayEntity?.cooldownDurationMin ?: 0) * 60
        val totalSeconds = maxOf(
            resolved.estimatedSeconds + declaredPrep + declaredCooldown,
            sections.sumOf { it.estimatedSeconds },
        )
        _uiState.update { current ->
            current.copy(
                programName = program.name,
                scheduleMode = loaded.snapshot.mode,
                trainingDayCount = trainingDays,
                selectedDate = selectedDate,
                today = today,
                dateStrip = dateStrip(today, selectedDate),
                dayLabel = day?.label,
                isRestDay = day?.isRest == true,
                commitment = resolved.commitment,
                estimatedSeconds = totalSeconds,
                sections = sections,
                cta = cta,
                editable = editable,
                dayId = day?.id,
                activeSessionId = openToday?.id,
                loading = false,
            )
        }
    }

    private fun dateStrip(today: LocalDate, selected: LocalDate): List<DayPlanDateChip> {
        val start = today.minusDays(3)
        return (0L..13L).map { offset ->
            val date = start.plusDays(offset)
            DayPlanDateChip(
                date = date,
                isToday = date == today,
                isSelected = date == selected,
            )
        }
    }
}
