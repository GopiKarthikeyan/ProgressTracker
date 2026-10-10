package com.forge.hypertrophy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forge.hypertrophy.domain.model.CardioActivity
import com.forge.hypertrophy.domain.model.CardioStyle

@Entity(
    tableName = "cardio_plan",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["dayId"], unique = true)],
)
data class CardioPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val type: CardioStyle,
    val targetDistanceM: Int?,
    val isOptional: Boolean,
    @ColumnInfo(defaultValue = "''") val label: String = "",
    @ColumnInfo(defaultValue = "'RUNNING'") val activity: CardioActivity = CardioActivity.RUNNING,
    @ColumnInfo(defaultValue = "''") val customName: String = "",
    val elevationM: Double? = null,
    val count: Int? = null,
)
