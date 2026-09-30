package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_day WHERE programId = :programId ORDER BY sequenceIndex")
    fun observeDays(programId: Long): Flow<List<RoutineDayEntity>>

    @Query("SELECT * FROM routine_day WHERE id = :id")
    suspend fun getDay(id: Long): RoutineDayEntity?

    @Insert
    suspend fun insertDay(day: RoutineDayEntity): Long

    @Update
    suspend fun updateDay(day: RoutineDayEntity)

    @Query("DELETE FROM routine_day WHERE id = :id")
    suspend fun deleteDay(id: Long)

    @Query("SELECT * FROM checklist_item WHERE dayId = :dayId ORDER BY id")
    fun observeChecklist(dayId: Long): Flow<List<ChecklistItemEntity>>

    @Insert
    suspend fun insertChecklist(item: ChecklistItemEntity): Long

    @Update
    suspend fun updateChecklist(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_item WHERE id = :id")
    suspend fun deleteChecklist(id: Long)

    @Query("SELECT * FROM routine_slot WHERE dayId = :dayId ORDER BY sortOrder")
    fun observeSlots(dayId: Long): Flow<List<RoutineSlotEntity>>

    @Query("SELECT * FROM routine_slot WHERE id = :id")
    suspend fun getSlot(id: Long): RoutineSlotEntity?

    @Insert
    suspend fun insertSlot(slot: RoutineSlotEntity): Long

    @Update
    suspend fun updateSlot(slot: RoutineSlotEntity)

    @Query("DELETE FROM routine_slot WHERE id = :id")
    suspend fun deleteSlot(id: Long)

    @Query("SELECT * FROM slot_alternative WHERE slotId = :slotId ORDER BY id")
    fun observeAlternatives(slotId: Long): Flow<List<SlotAlternativeEntity>>

    @Insert
    suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long

    @Query("DELETE FROM slot_alternative WHERE id = :id")
    suspend fun deleteAlternative(id: Long)

    @Query("SELECT * FROM cardio_plan WHERE dayId = :dayId")
    fun observeCardioPlan(dayId: Long): Flow<CardioPlanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long
}
