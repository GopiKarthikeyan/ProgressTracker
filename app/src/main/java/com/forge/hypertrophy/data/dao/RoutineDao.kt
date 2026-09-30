package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM routine_day WHERE programId = :programId ORDER BY sequenceIndex, id")
    suspend fun days(programId: Long): List<RoutineDayEntity>

    /**
     * Writes [orderedDayIds] as sequence indexes 0..n-1. The list must contain
     * every day of the program once.
     */
    @Transaction
    suspend fun reorderDays(programId: Long, orderedDayIds: List<Long>) {
        val existing = days(programId).map { it.id }.toSet()
        require(existing == orderedDayIds.toSet()) {
            "reorderDays requires every day of program $programId exactly once"
        }
        orderedDayIds.forEachIndexed { index, id ->
            setDaySequence(id, programId, index)
        }
    }

    @Query("UPDATE routine_day SET sequenceIndex = :index WHERE id = :id AND programId = :programId")
    suspend fun setDaySequence(id: Long, programId: Long, index: Int)

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

    @Query("SELECT * FROM checklist_item WHERE dayId IN (:dayIds) ORDER BY id")
    suspend fun checklistForDays(dayIds: List<Long>): List<ChecklistItemEntity>

    @Insert
    suspend fun insertChecklist(item: ChecklistItemEntity): Long

    @Update
    suspend fun updateChecklist(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_item WHERE id = :id")
    suspend fun deleteChecklist(id: Long)

    @Query("SELECT * FROM routine_slot WHERE dayId = :dayId ORDER BY sortOrder")
    fun observeSlots(dayId: Long): Flow<List<RoutineSlotEntity>>

    @Query("SELECT * FROM routine_slot WHERE dayId = :dayId ORDER BY sortOrder, id")
    suspend fun slots(dayId: Long): List<RoutineSlotEntity>

    @Query("SELECT * FROM routine_slot WHERE dayId IN (:dayIds) ORDER BY dayId, sortOrder, id")
    suspend fun slotsForDays(dayIds: List<Long>): List<RoutineSlotEntity>

    @Query("SELECT * FROM routine_slot")
    suspend fun allSlots(): List<RoutineSlotEntity>

    /**
     * Writes [orderedSlotIds] as sort orders 0..n-1. The list must contain
     * every slot of the day once.
     */
    @Transaction
    suspend fun reorderSlots(dayId: Long, orderedSlotIds: List<Long>) {
        val existing = slots(dayId).map { it.id }.toSet()
        require(existing == orderedSlotIds.toSet()) {
            "reorderSlots requires every slot of day $dayId exactly once"
        }
        orderedSlotIds.forEachIndexed { index, id ->
            setSlotOrder(id, dayId, index)
        }
    }

    @Query("UPDATE routine_slot SET sortOrder = :index WHERE id = :id AND dayId = :dayId")
    suspend fun setSlotOrder(id: Long, dayId: Long, index: Int)

    @Query("SELECT targetSkillStepId FROM routine_slot WHERE targetSkillStepId IS NOT NULL")
    suspend fun targetedStepIds(): List<Long>

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

    @Query("SELECT * FROM slot_alternative WHERE slotId IN (:slotIds) ORDER BY id")
    suspend fun alternativesForSlots(slotIds: List<Long>): List<SlotAlternativeEntity>

    @Query("SELECT * FROM slot_alternative")
    suspend fun allAlternatives(): List<SlotAlternativeEntity>

    @Insert
    suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long

    @Query("DELETE FROM slot_alternative WHERE id = :id")
    suspend fun deleteAlternative(id: Long)

    @Query("SELECT * FROM cardio_plan WHERE dayId = :dayId")
    fun observeCardioPlan(dayId: Long): Flow<CardioPlanEntity?>

    @Query("SELECT * FROM cardio_plan WHERE dayId IN (:dayIds)")
    suspend fun cardioForDays(dayIds: List<Long>): List<CardioPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long
}
