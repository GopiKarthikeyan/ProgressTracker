package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import java.time.Instant

@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = SessionSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionSlotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionSlotId")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionSlotId: Long,
    val setNumber: Int,
    val side: SetSide,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val holdSec: Int?,
    val rpe: Double?,
    val jointFlags: List<String>,
    val entryMethod: EntryMethod,
    val loggedAt: Instant,
)
