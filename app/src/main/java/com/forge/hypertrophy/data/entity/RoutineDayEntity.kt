package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_day",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("programId")],
)
data class RoutineDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val label: String,
    /** ISO weekday number: Monday is 1 and Sunday is 7. Null on a rolling program. */
    val dayOfWeek: Int?,
    val sequenceIndex: Int,
    val isRest: Boolean,
    val prepDurationMin: Int? = null,
    val cooldownDurationMin: Int? = null,
)
