package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forge.hypertrophy.data.entity.SlotBaselineEntity

@Dao
interface BaselineDao {
    @Query("SELECT * FROM slot_baseline WHERE slotId = :slotId")
    suspend fun forSlot(slotId: Long): SlotBaselineEntity?

    @Query("SELECT * FROM slot_baseline WHERE slotId IN (:slotIds)")
    suspend fun forSlots(slotIds: List<Long>): List<SlotBaselineEntity>

    @Query("SELECT * FROM slot_baseline")
    suspend fun all(): List<SlotBaselineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SlotBaselineEntity): Long
}
