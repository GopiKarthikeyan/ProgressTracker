package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.BaselineDao
import com.forge.hypertrophy.data.entity.SlotBaselineEntity
import javax.inject.Inject
import javax.inject.Singleton

interface BaselineRepository {
    suspend fun forSlot(slotId: Long): SlotBaselineEntity?

    suspend fun forSlots(slotIds: List<Long>): List<SlotBaselineEntity>

    suspend fun all(): List<SlotBaselineEntity>

    suspend fun save(entity: SlotBaselineEntity)
}

@Singleton
class RoomBaselineRepository @Inject constructor(
    private val dao: BaselineDao,
) : BaselineRepository {
    override suspend fun forSlot(slotId: Long): SlotBaselineEntity? = dao.forSlot(slotId)

    override suspend fun forSlots(slotIds: List<Long>): List<SlotBaselineEntity> {
        if (slotIds.isEmpty()) return emptyList()
        return dao.forSlots(slotIds)
    }

    override suspend fun all(): List<SlotBaselineEntity> = dao.all()

    override suspend fun save(entity: SlotBaselineEntity) {
        val current = dao.forSlot(entity.slotId)
        dao.upsert(entity.copy(id = current?.id ?: entity.id))
    }
}
