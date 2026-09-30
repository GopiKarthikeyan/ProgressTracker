package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forge.hypertrophy.domain.model.MediaType
import com.forge.hypertrophy.domain.model.Pose

@Entity(
    tableName = "media_item",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SetEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["setEntryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("exerciseId"),
        Index("setEntryId"),
    ],
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MediaType,
    val pose: Pose?,
    val exerciseId: Long?,
    val setEntryId: Long?,
    val uri: String,
    val trimStartMs: Long?,
    val trimEndMs: Long?,
)
