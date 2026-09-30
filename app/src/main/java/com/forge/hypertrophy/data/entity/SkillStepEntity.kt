package com.forge.hypertrophy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "skill_step",
    foreignKeys = [
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("skillId")],
)
data class SkillStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skillId: Long,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "12") val stage1TotalSec: Int = 12,
    @ColumnInfo(defaultValue = "15") val stage2TotalLowSec: Int = 15,
    @ColumnInfo(defaultValue = "18") val stage2TotalHighSec: Int = 18,
    @ColumnInfo(defaultValue = "10") val stage3UnbrokenSec: Int = 10,
)
