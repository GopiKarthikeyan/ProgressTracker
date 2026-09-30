package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "gear")
data class GearEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mileageLimitM: Int?,
    val archivedAt: Instant?,
)
