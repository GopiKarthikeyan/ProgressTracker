package com.forge.hypertrophy.data.dao

import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.entity.BiometricsEntity
import com.forge.hypertrophy.data.entity.CardioLogEntity
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.entity.GearEntity
import com.forge.hypertrophy.data.entity.MediaItemEntity
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.SkillEntity
import com.forge.hypertrophy.data.entity.SkillProgressEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.entity.TrackPointEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.domain.model.CardioSource
import com.forge.hypertrophy.domain.model.CardioType
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MediaType
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import java.time.Instant
import java.time.LocalDate

internal class DaoFixture(
    private val db: AppDatabase,
) {
    suspend fun program(name: String = "block"): Long = db.programDao().insert(
        ProgramEntity(
            name = name,
            scheduleMode = ScheduleMode.ROLLING,
            rollingSequence = 0,
            deloadActive = false,
            deloadStartedOn = null,
        ),
    )

    suspend fun exercise(
        name: String,
        skillId: Long? = null,
        archivedAt: Instant? = null,
    ): Long = db.exerciseDao().insert(
        ExerciseEntity(
            name = name,
            equipment = Equipment.BARBELL,
            barWeightKg = 20.0,
            loadIncrementKg = 2.5,
            isUnilateral = false,
            skillId = skillId,
            primaryMuscleGroups = listOf("back"),
            secondaryMuscleGroups = emptyList(),
            setupNotes = "",
            archivedAt = archivedAt,
        ),
    )

    suspend fun skill(
        name: String,
        archivedAt: Instant? = null,
    ): Long = db.skillDao().insert(
        SkillEntity(name = name, archivedAt = archivedAt),
    )

    suspend fun step(
        skillId: Long,
        sortOrder: Int = 0,
    ): Long = db.skillDao().insertStep(
        SkillStepEntity(skillId = skillId, sortOrder = sortOrder),
    )

    suspend fun progress(
        skillId: Long,
        stepId: Long,
        stage: Int = 1,
    ): Long = db.skillDao().upsertProgress(
        SkillProgressEntity(
            skillId = skillId,
            currentStepId = stepId,
            stage = stage,
            updatedAt = LOGGED_AT,
        ),
    )

    suspend fun day(
        programId: Long,
        sequenceIndex: Int = 0,
        label: String = "day",
    ): Long = db.routineDao().insertDay(
        RoutineDayEntity(
            programId = programId,
            label = label,
            dayOfWeek = null,
            sequenceIndex = sequenceIndex,
            isRest = false,
        ),
    )

    suspend fun checklist(dayId: Long): Long = db.routineDao().insertChecklist(
        ChecklistItemEntity(
            dayId = dayId,
            phase = ChecklistPhase.PREP,
            text = "warm up",
            reps = null,
            seconds = 60,
        ),
    )

    suspend fun slot(
        dayId: Long,
        exerciseId: Long,
        setsMax: Int = 8,
        sortOrder: Int = 0,
        targetSkillStepId: Long? = null,
    ): Long = db.routineDao().insertSlot(
        RoutineSlotEntity(
            dayId = dayId,
            exerciseId = exerciseId,
            category = SlotCategory.COMPOUND,
            sortOrder = sortOrder,
            supersetGroup = null,
            metricType = MetricType.WEIGHT_REPS,
            setsMin = 3,
            setsMax = setsMax,
            repsLow = 6,
            repsHigh = 10,
            isAmrap = false,
            holdTargetSec = null,
            blockDurationSec = null,
            restMinSec = 90,
            restMaxSec = 120,
            restAsNeeded = false,
            isOptional = false,
            skipReasonLabel = null,
            targetSkillStepId = targetSkillStepId,
            progressionRule = ProgressionRule.LINEAR,
            incrementOverrideKg = null,
        ),
    )

    suspend fun alternative(
        slotId: Long,
        exerciseId: Long,
    ): Long = db.routineDao().insertAlternative(
        SlotAlternativeEntity(slotId = slotId, exerciseId = exerciseId),
    )

    suspend fun cardioPlan(dayId: Long): Long = db.routineDao().upsertCardioPlan(
        CardioPlanEntity(
            dayId = dayId,
            type = CardioType.JOG,
            targetDistanceM = 3000,
            isOptional = false,
        ),
    )

    suspend fun session(
        dayId: Long? = null,
        status: SessionStatus = SessionStatus.COMPLETED,
    ): Long = db.sessionDao().insert(
        WorkoutSessionEntity(
            date = SESSION_DATE,
            dayId = dayId,
            kind = SessionKind.GYM,
            status = status,
            isDeload = false,
            isShortOnTime = false,
            readinessSleep = null,
            readinessSoreness = null,
            readinessEnergy = null,
            startedAt = null,
            completedAt = null,
        ),
    )

    suspend fun sessionSlot(
        sessionId: Long,
        slotId: Long?,
        prescription: SlotPrescription,
    ): Long = db.sessionDao().insertSlot(
        SessionSlotEntity(
            sessionId = sessionId,
            slotId = slotId,
            prescriptionSnapshot = prescription,
            chosenAlternativeExerciseId = null,
            skipped = false,
            skipReason = null,
            formConfirmed = null,
        ),
    )

    suspend fun setEntry(sessionSlotId: Long): Long = db.sessionDao().insertSet(
        SetEntryEntity(
            sessionSlotId = sessionSlotId,
            setNumber = 1,
            side = SetSide.BOTH,
            setType = SetType.WORKING,
            weightKg = 60.0,
            reps = 8,
            holdSec = null,
            rpe = null,
            jointFlags = emptyList(),
            entryMethod = EntryMethod.SCREEN,
            loggedAt = LOGGED_AT,
        ),
    )

    suspend fun gear(
        name: String,
        archivedAt: Instant? = null,
    ): Long = db.gearDao().insert(
        GearEntity(name = name, mileageLimitM = 500_000, archivedAt = archivedAt),
    )

    suspend fun cardioLog(
        sessionId: Long,
        gearId: Long? = null,
    ): Long = db.cardioDao().insert(
        CardioLogEntity(
            sessionId = sessionId,
            distanceM = 1000.0,
            durationSec = 600,
            source = CardioSource.MANUAL,
            gearId = gearId,
            tempC = null,
            uvIndex = null,
        ),
    )

    suspend fun trackPoint(
        cardioLogId: Long,
        sequenceIndex: Int,
    ): Long = db.cardioDao().insertTrackPoints(
        listOf(
            TrackPointEntity(
                cardioLogId = cardioLogId,
                latitude = 12.0,
                longitude = 77.0,
                altitudeM = null,
                accuracyM = null,
                recordedAt = LOGGED_AT,
                sequenceIndex = sequenceIndex,
            ),
        ),
    ).single()

    suspend fun media(
        exerciseId: Long?,
        setEntryId: Long?,
    ): Long = db.mediaDao().insert(
        MediaItemEntity(
            type = MediaType.PHOTO,
            pose = Pose.FRONT,
            exerciseId = exerciseId,
            setEntryId = setEntryId,
            uri = "content://media/1",
            trimStartMs = null,
            trimEndMs = null,
        ),
    )

    suspend fun biometrics(
        date: LocalDate,
        bodyWeightKg: Double,
    ): Long = db.biometricsDao().upsert(
        BiometricsEntity(date = date, bodyWeightKg = bodyWeightKg),
    )

    fun prescription(slot: RoutineSlotEntity) = SlotPrescription(
        exerciseId = slot.exerciseId,
        category = slot.category,
        sortOrder = slot.sortOrder,
        supersetGroup = slot.supersetGroup,
        metricType = slot.metricType,
        setsMin = slot.setsMin,
        setsMax = slot.setsMax,
        repsLow = slot.repsLow,
        repsHigh = slot.repsHigh,
        isAmrap = slot.isAmrap,
        holdTargetSec = slot.holdTargetSec,
        blockDurationSec = slot.blockDurationSec,
        restMinSec = slot.restMinSec,
        restMaxSec = slot.restMaxSec,
        restAsNeeded = slot.restAsNeeded,
        isOptional = slot.isOptional,
        skipReasonLabel = slot.skipReasonLabel,
        targetSkillStepId = slot.targetSkillStepId,
        progressionRule = slot.progressionRule,
        incrementOverrideKg = slot.incrementOverrideKg,
    )

    companion object {
        val LOGGED_AT: Instant = Instant.parse("2026-04-01T08:00:00Z")
        val SESSION_DATE: LocalDate = LocalDate.of(2026, 4, 1)
        val ARCHIVED_AT: Instant = Instant.parse("2026-04-02T08:00:00Z")
    }
}
