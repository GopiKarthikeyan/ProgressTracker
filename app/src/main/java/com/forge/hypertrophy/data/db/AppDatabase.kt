package com.forge.hypertrophy.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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

@Database(
    entities = [
        ProgramEntity::class,
        ExerciseEntity::class,
        SkillEntity::class,
        SkillStepEntity::class,
        SkillProgressEntity::class,
        RoutineDayEntity::class,
        ChecklistItemEntity::class,
        RoutineSlotEntity::class,
        SlotAlternativeEntity::class,
        CardioPlanEntity::class,
        WorkoutSessionEntity::class,
        SessionSlotEntity::class,
        SetEntryEntity::class,
        CardioLogEntity::class,
        TrackPointEntity::class,
        MediaItemEntity::class,
        BiometricsEntity::class,
        GearEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(TrainingConverters::class)
abstract class AppDatabase : RoomDatabase()
