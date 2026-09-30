package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.forge.hypertrophy.domain.model.ScheduleMode
import java.time.LocalDate

@Entity(tableName = "program")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val scheduleMode: ScheduleMode,
    val rollingSequence: Int,
    val deloadActive: Boolean,
    val deloadStartedOn: LocalDate?,
)
