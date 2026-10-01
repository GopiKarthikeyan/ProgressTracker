package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Starting load for a routine slot. A null [weightKg] means the lifter
 * skipped setup and the first session calibrates it.
 */
@Entity(
    tableName = "slot_baseline",
    foreignKeys = [
        ForeignKey(
            entity = RoutineSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["slotId"], unique = true)],
)
data class SlotBaselineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotId: Long,
    val weightKg: Double?,
    val repsHint: Int?,
    val setAt: Instant,
)
