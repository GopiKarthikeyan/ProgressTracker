package com.forge.hypertrophy.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.dao.CompletedSetRow
import com.forge.hypertrophy.data.entity.BiometricsEntity
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.BiometricsRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.data.schedule.ScheduleLoader
import com.forge.hypertrophy.data.schedule.toPrescription
import com.forge.hypertrophy.domain.engine.DatedValue
import com.forge.hypertrophy.domain.engine.DeloadEngine
import com.forge.hypertrophy.domain.engine.DoubleProgressionEngine
import com.forge.hypertrophy.domain.engine.HoldSample
import com.forge.hypertrophy.domain.engine.LiftSample
import com.forge.hypertrophy.domain.engine.MovingAverage
import com.forge.hypertrophy.domain.engine.PrDetector
import com.forge.hypertrophy.domain.engine.StartingWeight
import com.forge.hypertrophy.domain.engine.slotSessionsForProgression
import com.forge.hypertrophy.domain.engine.ReadinessCheck
import com.forge.hypertrophy.domain.engine.SessionEstimator
import com.forge.hypertrophy.domain.engine.SetRecordCalculator
import com.forge.hypertrophy.domain.engine.ShortOnTimePlanner
import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.engine.WeeklyVolumeCalculator
import com.forge.hypertrophy.domain.engine.heatmapKind
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionAction
import com.forge.hypertrophy.domain.model.ProgressionInput
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import com.forge.hypertrophy.domain.model.LoggedSet
import com.forge.hypertrophy.domain.model.TrainingDay
import com.forge.hypertrophy.domain.usecase.GetTodaysWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.ReconcileScheduleUseCase
import com.forge.hypertrophy.domain.usecase.ScheduleEdit
import com.forge.hypertrophy.domain.usecase.SkipToNextUseCase
import com.forge.hypertrophy.domain.usecase.SwapWithTomorrowUseCase
import com.forge.hypertrophy.domain.usecase.TakeRestNowUseCase
import com.forge.hypertrophy.widget.TodayWidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val skills: SkillRepository,
    private val biometrics: BiometricsRepository,
    private val preferences: TrainingPreferencesRepository,
    private val cursor: ScheduleCursorRepository,
    private val clock: Clock,
    private val widget: TodayWidgetRefresher,
    private val baselines: BaselineRepository,
) : ViewModel() {
    private val loader = ScheduleLoader(programs, routines, sessions, preferences, cursor)
    private val streaks = StreakCalculator()
    private val records = SetRecordCalculator()
    private val prs = PrDetector()
    private val volume = WeeklyVolumeCalculator()
    private val progression = DoubleProgressionEngine()
    private val deloadEngine = DeloadEngine()
    private val estimator = SessionEstimator()
    private val planner = ShortOnTimePlanner(estimator)
    private val todayWorkout = GetTodaysWorkoutUseCase(clock)
    private val reconcile = ReconcileScheduleUseCase(clock)
    private val takeRest = TakeRestNowUseCase(clock, streaks)
    private val skipToNext = SkipToNextUseCase(clock, streaks)
    private val swapTomorrow = SwapWithTomorrowUseCase(clock, streaks)
    private val gate = Mutex()
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            programs.observeActive().collect { reload() }
        }
        viewModelScope.launch {
            biometrics.observeAll().collect { reload() }
        }
    }

    fun onEvent(event: DashboardEvent) {
        viewModelScope.launch {
            when (event) {
                DashboardEvent.TakeRestNow -> applySchedule { takeRest.apply(it) }
                DashboardEvent.SkipToNext -> applySchedule { skipToNext.apply(it) }
                DashboardEvent.SwapWithTomorrow -> applySchedule { swapTomorrow.apply(it) }
                is DashboardEvent.Start -> start(event.shortOnTime)
                is DashboardEvent.WeightDraft -> _uiState.value = _uiState.value.copy(weightDraft = event.text)
                is DashboardEvent.BodyFatDraft -> _uiState.value = _uiState.value.copy(bodyFatDraft = event.text)
                DashboardEvent.SaveWeighIn -> saveWeighIn()
                DashboardEvent.DismissNotice -> _uiState.value = _uiState.value.copy(notice = null)
            }
        }
    }

    private suspend fun applySchedule(edit: (ScheduleSnapshot) -> ScheduleEdit) {
        gate.withLock {
            val loaded = loader.load() ?: return
            when (val result = edit(loaded.snapshot)) {
                ScheduleEdit.Rejected -> _uiState.value = _uiState.value.copy(notice = DashboardNotice.SCHEDULE_REJECTED)
                is ScheduleEdit.Applied -> {
                    persist(loaded.program, loaded.days, result.snapshot)
                    if (clock.localToday() in result.snapshot.autoCompletedRests) {
                        recordRest(loaded.program, result.snapshot)
                    }
                    publish(loaded.program)
                }
            }
        }
    }

    private suspend fun start(shortOnTime: Boolean) {
        gate.withLock {
            val loaded = loader.load() ?: return
            if (sessions.observeInProgress().first().isNotEmpty()) {
                _uiState.value = _uiState.value.copy(notice = DashboardNotice.ALREADY_IN_PROGRESS)
                return
            }
            val today = clock.localToday()
            val day = loaded.snapshot.dayOn(today, today) ?: return
            val transition = preferences.transitionRestSeconds.first()
            val entities = loaded.slots.filter { it.dayId == day.id }.sortedBy { it.sortOrder }
            val planned = plannedSlots(entities, day, shortOnTime, transition)
            val id = sessions.insert(
                WorkoutSessionEntity(
                    date = today,
                    dayId = day.id,
                    kind = kindFor(day),
                    status = SessionStatus.IN_PROGRESS,
                    isDeload = loaded.program.deloadActive,
                    isShortOnTime = shortOnTime,
                    readinessSleep = null,
                    readinessSoreness = null,
                    readinessEnergy = null,
                    startedAt = clock.instant(),
                    completedAt = null,
                ),
            )
            entities.forEach { slot ->
                val kept = planned[slot.id]
                sessions.insertSlot(
                    SessionSlotEntity(
                        sessionId = id,
                        slotId = slot.id,
                        prescriptionSnapshot = kept ?: slot.toPrescription(),
                        chosenAlternativeExerciseId = null,
                        skipped = shortOnTime && kept == null,
                        skipReason = if (shortOnTime && kept == null) SHORT_ON_TIME_REASON else null,
                        formConfirmed = null,
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(notice = DashboardNotice.WORKOUT_STARTED)
            publish(loaded.program)
        }
    }

    private suspend fun saveWeighIn() {
        gate.withLock {
            saveWeighInLocked()
        }
    }

    private suspend fun saveWeighInLocked() {
        val state = _uiState.value
        val weight = parseMeasurement(state.weightDraft)
        val fat = parseMeasurement(state.bodyFatDraft)
        if (weight is Measure.Invalid || fat is Measure.Invalid || (weight is Measure.Empty && fat is Measure.Empty)) {
            _uiState.value = state.copy(notice = DashboardNotice.WEIGH_IN_INVALID)
            return
        }
        if (weight is Measure.Value && weight.number <= 0.0) {
            _uiState.value = state.copy(notice = DashboardNotice.WEIGH_IN_INVALID)
            return
        }
        if (fat is Measure.Value && (fat.number < 0.0 || fat.number > 100.0)) {
            _uiState.value = state.copy(notice = DashboardNotice.WEIGH_IN_INVALID)
            return
        }
        val today = clock.localToday()
        val existing = biometrics.get(today)
        biometrics.upsert(
            BiometricsEntity(
                id = existing?.id ?: 0,
                date = today,
                bodyWeightKg = if (weight is Measure.Value) weight.number else existing?.bodyWeightKg,
                bodyFatPercent = if (fat is Measure.Value) fat.number else existing?.bodyFatPercent,
            ),
        )
        _uiState.value = _uiState.value.copy(
            weightDraft = "",
            bodyFatDraft = "",
            notice = DashboardNotice.WEIGH_IN_SAVED,
        )
    }

    private suspend fun reload() {
        gate.withLock {
            val program = programs.observeActive().first()
            if (program == null) {
                _uiState.value = _uiState.value.copy(today = null, currentStreak = 0, bestStreak = 0)
                publishHistory(program = null, snapshot = null, slots = emptyList())
                return
            }
            val loaded = loader.load(program) ?: return
            val reconciled = reconcile.reconcile(loaded.snapshot)
            if (reconciled != loaded.snapshot) {
                persist(program, loaded.days, reconciled)
            }
            publish(program)
        }
    }

    private suspend fun publish(program: ProgramEntity) {
        val loaded = loader.load(program) ?: return
        val snapshot = loaded.snapshot
        val today = clock.localToday()
        val transition = preferences.transitionRestSeconds.first()
        val plan = todayWorkout.today(
            snapshot = snapshot,
            check = ReadinessCheck(null, null, null),
            transitionRestSeconds = transition,
            budgetSeconds = 1,
        )
        val shortSeconds = plan?.let { card ->
            val budget = (card.estimatedSeconds / 2).coerceAtLeast(1)
            estimator.estimate(
                planner.plan(card.day.slots, budget, transition),
                transition,
            )
        } ?: 0
        val inProgress = sessions.observeInProgress().first().isNotEmpty()
        val kept = _uiState.value
        _uiState.value = kept.copy(
            today = plan?.let {
                TodayCard(
                    label = it.day.label,
                    estimatedSeconds = it.estimatedSeconds,
                    shortEstimatedSeconds = shortSeconds,
                    mode = snapshot.mode,
                    isRest = it.day.isRest,
                    takeRestEnabled = snapshot.mode == ScheduleMode.ROLLING && snapshot.days.any { day -> day.isRest },
                    skipEnabled = snapshot.mode == ScheduleMode.ROLLING && snapshot.days.isNotEmpty(),
                    swapEnabled = snapshot.mode == ScheduleMode.FIXED && snapshot.dayOn(today.plusDays(1), today) != null,
                    startEnabled = !inProgress,
                )
            },
            currentStreak = streaks.streak(snapshot, today),
            bestStreak = streaks.bestStreak(snapshot, today),
        )
        publishHistory(program, snapshot, loaded.slots)
        runCatching { widget.refresh() }
    }

    private suspend fun publishHistory(
        program: ProgramEntity?,
        snapshot: ScheduleSnapshot?,
        slots: List<RoutineSlotEntity>,
    ) {
        val today = clock.localToday()
        val exerciseById = exercises.all().associateBy { it.id }
        val sets = sessions.completedSets()
        val completed = sessions.completedDays()
        val rests = snapshot?.autoCompletedRests ?: emptySet()
        val rows = biometrics.observeAll().first()
        val kept = _uiState.value
        _uiState.value = kept.copy(
            heatmap = heatmap(completed, rests, today),
            records = exerciseRecords(sets, exerciseById),
            skills = skillLadders(sets, exerciseById),
            weight = chart(rows, BiometricsEntity::bodyWeightKg),
            bodyFat = chart(rows, BiometricsEntity::bodyFatPercent),
            volume = weeklyVolume(sets, exerciseById, today),
            stalls = if (snapshot == null) {
                emptyList()
            } else {
                val starting = baselines.all().associate { it.slotId to StartingWeight(it.weightKg, it.repsHint, it.setAt) }
                stalls(slots, sets, exerciseById, starting)
            },
            deload = if (program == null || snapshot == null) null else deloadStatus(program, completed, snapshot),
        )
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
        val today = clock.localToday()
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

    private fun plannedSlots(
        entities: List<RoutineSlotEntity>,
        day: TrainingDay,
        shortOnTime: Boolean,
        transition: Int,
    ): Map<Long, SlotPrescription> {
        if (!shortOnTime) return entities.associate { it.id to it.toPrescription() }
        val estimate = estimator.estimate(day.slots, transition)
        val budget = (estimate / 2).coerceAtLeast(1)
        return planner.plan(day.slots, budget, transition).associate { it.id to it.prescription }
    }

    private fun kindFor(day: TrainingDay): SessionKind {
        if (day.isRest) return SessionKind.REST
        val training = day.slots.filter {
            val category = it.prescription.category
            category != SlotCategory.PREP && category != SlotCategory.COOLDOWN
        }
        if (training.isNotEmpty() && training.all { it.prescription.category == SlotCategory.CARDIO }) {
            return SessionKind.CARDIO
        }
        return SessionKind.GYM
    }

    private fun heatmap(
        completed: List<com.forge.hypertrophy.data.dao.CompletedSessionDay>,
        rests: Set<LocalDate>,
        today: LocalDate,
    ): List<HeatmapCell> {
        val kinds = completed.groupBy { it.date }.mapValues { (_, days) -> days.map { it.kind }.toSet() }
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L)
        val cells = mutableListOf<HeatmapCell>()
        var date = start
        val end = start.plusWeeks(WEEKS)
        while (date.isBefore(end)) {
            val logged = kinds[date].orEmpty().toMutableSet()
            if (date in rests) logged += SessionKind.REST
            cells += HeatmapCell(date, heatmapKind(logged))
            date = date.plusDays(1)
        }
        return cells
    }

    private fun exerciseRecords(
        sets: List<CompletedSetRow>,
        exercisesById: Map<Long, com.forge.hypertrophy.data.entity.ExerciseEntity>,
    ): List<ExerciseRecordUi> {
        val lifts = sets.map { row ->
            LiftSample(
                exerciseId = exerciseId(row),
                slotId = row.slotId ?: 0L,
                weightKg = row.weightKg,
                reps = row.reps,
            )
        }
        val e1rm = prs.bestByExercise(lifts)
        val volumeBest = records.bestWeightTimesReps(lifts)
        val ids = (e1rm.keys + volumeBest.keys).distinct()
        return ids.mapNotNull { id ->
            val exercise = exercisesById[id] ?: return@mapNotNull null
            val pair = volumeBest[id]
            ExerciseRecordUi(
                name = exercise.name,
                bestE1rmKg = e1rm[id],
                bestWeightKg = pair?.weightKg,
                bestReps = pair?.reps,
            )
        }.sortedBy { it.name }
    }

    private suspend fun skillLadders(
        sets: List<CompletedSetRow>,
        exercisesById: Map<Long, com.forge.hypertrophy.data.entity.ExerciseEntity>,
    ): List<SkillLadderUi> {
        val skillList = skills.observeActive().first()
        if (skillList.isEmpty()) return emptyList()
        val steps = skills.allSteps().groupBy { it.skillId }
        val progress = skills.allProgress().associateBy { it.skillId }
        val holds = sets.mapNotNull { row ->
            val seconds = row.holdSec ?: return@mapNotNull null
            val skillId = exercisesById[exerciseId(row)]?.skillId ?: return@mapNotNull null
            HoldSample(skillId, seconds)
        }
        val bestHold = records.maxUnbrokenHold(holds)
        return skillList.map { skill ->
            val ladder = steps[skill.id].orEmpty().sortedBy { it.sortOrder }
            val current = progress[skill.id]
            val tier = current?.let { step -> ladder.indexOfFirst { it.id == step.currentStepId } } ?: 0
            SkillLadderUi(
                name = skill.name,
                tierNames = ladder.map { it.name },
                currentTier = tier.coerceAtLeast(0),
                stage = current?.stage ?: 1,
                maxHoldSec = bestHold[skill.id],
            )
        }.sortedBy { it.name }
    }

    private fun weeklyVolume(
        sets: List<CompletedSetRow>,
        exercisesById: Map<Long, com.forge.hypertrophy.data.entity.ExerciseEntity>,
        today: LocalDate,
    ): List<MuscleVolumeUi> {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(6)
        val week = sets.filter { !it.sessionDate.isBefore(start) && !it.sessionDate.isAfter(end) }
        val counted = week.mapNotNull { row ->
            val exercise = exercisesById[exerciseId(row)] ?: return@mapNotNull null
            com.forge.hypertrophy.domain.engine.VolumeSet(
                setType = row.setType,
                primaryMuscles = exercise.primaryMuscleGroups,
                secondaryMuscles = exercise.secondaryMuscleGroups,
            )
        }
        return volume.volume(counted)
            .map { (muscle, setsForMuscle) -> MuscleVolumeUi(muscle, setsForMuscle) }
            .sortedBy { it.muscle }
    }

    private fun stalls(
        slots: List<RoutineSlotEntity>,
        sets: List<CompletedSetRow>,
        exercisesById: Map<Long, com.forge.hypertrophy.data.entity.ExerciseEntity>,
        starting: Map<Long, StartingWeight>,
    ): List<String> {
        val names = mutableListOf<String>()
        for (slot in slots) {
            val low = slot.repsLow ?: continue
            val high = slot.repsHigh ?: continue
            if (slot.metricType != MetricType.WEIGHT_REPS &&
                slot.metricType != MetricType.REPS &&
                slot.metricType != MetricType.HOLD_OR_REPS
            ) {
                continue
            }
            val exercise = exercisesById[slot.exerciseId] ?: continue
            val baseline = starting[slot.id]
            val history = slotSessionsForProgression(
                sets.filter { it.slotId == slot.id }
                    .groupBy { it.sessionId }
                    .values
                    .map { rows ->
                        rows.first().completedAt to rows.map { LoggedSet(it.weightKg, it.reps, it.setType) }
                    },
                baseline,
            )
            val suggestion = progression.suggest(
                ProgressionInput(
                    rule = slot.progressionRule,
                    equipment = exercise.equipment,
                    metricType = slot.metricType,
                    repsLow = low,
                    repsHigh = high,
                    exerciseIncrementKg = exercise.loadIncrementKg,
                    incrementOverrideKg = slot.incrementOverrideKg,
                    slotSessions = history,
                    latestWeightFromAnySlotKg = latestWeight(sets, slot.exerciseId),
                    baselineWeightKg = baseline?.weightKg,
                    awaitingCalibration = baseline?.awaitingCalibration == true,
                ),
            )
            if (suggestion.action == ProgressionAction.STALL) names += exercise.name
        }
        return names.distinct().sorted()
    }

    private fun latestWeight(sets: List<CompletedSetRow>, exerciseId: Long): Double? {
        val dated = sets.filter { exerciseId(it) == exerciseId && it.setType == SetType.WORKING && it.weightKg != null }
        val latest = dated.maxOfOrNull { it.sessionDate } ?: return null
        return dated.filter { it.sessionDate == latest }.maxOfOrNull { it.weightKg ?: 0.0 }
    }

    private fun deloadStatus(
        program: ProgramEntity,
        completed: List<com.forge.hypertrophy.data.dao.CompletedSessionDay>,
        snapshot: ScheduleSnapshot,
    ): DeloadStatus? {
        if (!program.deloadActive) return null
        val started = program.deloadStartedOn
        val rotation = snapshot.days.count { !it.isRest }
        val done = if (started == null) {
            0
        } else {
            completed.count { it.date >= started && it.kind != SessionKind.REST }
        }
        return DeloadStatus(
            startedOn = started,
            rotationComplete = deloadEngine.rotationFinished(done, rotation),
        )
    }

    private fun chart(
        rows: List<BiometricsEntity>,
        value: (BiometricsEntity) -> Double?,
    ): ChartSeries {
        val samples = rows.mapNotNull { row ->
            val measured = value(row) ?: return@mapNotNull null
            ChartPoint(row.date, measured)
        }.sortedBy { it.date }
        val average = MovingAverage.trailing(samples.map { DatedValue(it.date, it.value) })
            .map { ChartPoint(it.date, it.value) }
        return ChartSeries(samples, average)
    }

    private fun exerciseId(row: CompletedSetRow): Long {
        return row.chosenAlternativeExerciseId ?: row.prescriptionSnapshot.exerciseId
    }

    private fun Clock.localToday(): LocalDate = instant().atZone(zone).toLocalDate()

    private companion object {
        const val WEEKS = 16L
        const val SHORT_ON_TIME_REASON = "short on time"
    }
}

private sealed interface Measure {
    data object Empty : Measure
    data class Value(val number: Double) : Measure
    data object Invalid : Measure
}

private fun parseMeasurement(raw: String): Measure {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return Measure.Empty
    val number = trimmed.toDoubleOrNull() ?: return Measure.Invalid
    return Measure.Value(number)
}
