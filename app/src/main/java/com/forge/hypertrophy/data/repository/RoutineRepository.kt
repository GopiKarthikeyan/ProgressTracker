package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.RoutineDao
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    fun observeDays(programId: Long): Flow<List<RoutineDayEntity>>

    suspend fun getDay(id: Long): RoutineDayEntity?

    suspend fun insertDay(day: RoutineDayEntity): Long

    suspend fun updateDay(day: RoutineDayEntity)

    suspend fun deleteDay(id: Long)

    suspend fun reorderDays(programId: Long, orderedDayIds: List<Long>)

    fun observeChecklist(dayId: Long): Flow<List<ChecklistItemEntity>>

    suspend fun insertChecklist(item: ChecklistItemEntity): Long

    suspend fun updateChecklist(item: ChecklistItemEntity)

    suspend fun deleteChecklist(id: Long)

    fun observeSlots(dayId: Long): Flow<List<RoutineSlotEntity>>

    suspend fun getSlot(id: Long): RoutineSlotEntity?

    suspend fun insertSlot(slot: RoutineSlotEntity): Long

    suspend fun updateSlot(slot: RoutineSlotEntity)

    suspend fun deleteSlot(id: Long)

    suspend fun reorderSlots(dayId: Long, orderedSlotIds: List<Long>)

    fun observeAlternatives(slotId: Long): Flow<List<SlotAlternativeEntity>>

    suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long

    suspend fun deleteAlternative(id: Long)

    fun observeCardioPlan(dayId: Long): Flow<CardioPlanEntity?>

    suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long
}

@Singleton
class RoomRoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
) : RoutineRepository {
    override fun observeDays(programId: Long): Flow<List<RoutineDayEntity>> = routineDao.observeDays(programId)

    override suspend fun getDay(id: Long): RoutineDayEntity? = routineDao.getDay(id)

    override suspend fun insertDay(day: RoutineDayEntity): Long = routineDao.insertDay(day)

    override suspend fun updateDay(day: RoutineDayEntity) = routineDao.updateDay(day)

    override suspend fun deleteDay(id: Long) = routineDao.deleteDay(id)

    override suspend fun reorderDays(programId: Long, orderedDayIds: List<Long>) =
        routineDao.reorderDays(programId, orderedDayIds)

    override fun observeChecklist(dayId: Long): Flow<List<ChecklistItemEntity>> = routineDao.observeChecklist(dayId)

    override suspend fun insertChecklist(item: ChecklistItemEntity): Long = routineDao.insertChecklist(item)

    override suspend fun updateChecklist(item: ChecklistItemEntity) = routineDao.updateChecklist(item)

    override suspend fun deleteChecklist(id: Long) = routineDao.deleteChecklist(id)

    override fun observeSlots(dayId: Long): Flow<List<RoutineSlotEntity>> = routineDao.observeSlots(dayId)

    override suspend fun getSlot(id: Long): RoutineSlotEntity? = routineDao.getSlot(id)

    override suspend fun insertSlot(slot: RoutineSlotEntity): Long = routineDao.insertSlot(slot)

    override suspend fun updateSlot(slot: RoutineSlotEntity) = routineDao.updateSlot(slot)

    override suspend fun deleteSlot(id: Long) = routineDao.deleteSlot(id)

    override suspend fun reorderSlots(dayId: Long, orderedSlotIds: List<Long>) =
        routineDao.reorderSlots(dayId, orderedSlotIds)

    override fun observeAlternatives(slotId: Long): Flow<List<SlotAlternativeEntity>> =
        routineDao.observeAlternatives(slotId)

    override suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long =
        routineDao.insertAlternative(alternative)

    override suspend fun deleteAlternative(id: Long) = routineDao.deleteAlternative(id)

    override fun observeCardioPlan(dayId: Long): Flow<CardioPlanEntity?> = routineDao.observeCardioPlan(dayId)

    override suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long = routineDao.upsertCardioPlan(plan)
}
