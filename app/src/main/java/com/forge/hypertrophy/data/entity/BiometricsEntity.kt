package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "biometrics",
    indices = [Index(value = ["date"], unique = true)],
)
data class BiometricsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val bodyWeightKg: Double?,
)
