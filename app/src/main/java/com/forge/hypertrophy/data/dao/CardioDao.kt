package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.forge.hypertrophy.data.entity.CardioLogEntity
import com.forge.hypertrophy.data.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardioDao {
    @Query("SELECT * FROM cardio_log WHERE sessionId = :sessionId")
    fun observeLog(sessionId: Long): Flow<CardioLogEntity?>

    @Insert
    suspend fun insert(log: CardioLogEntity): Long

    @Update
    suspend fun update(log: CardioLogEntity)

    @Query("DELETE FROM cardio_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM track_point WHERE cardioLogId = :cardioLogId ORDER BY sequenceIndex")
    fun observeTrackPoints(cardioLogId: Long): Flow<List<TrackPointEntity>>

    @Insert
    suspend fun insertTrackPoints(points: List<TrackPointEntity>): List<Long>

    @Query("SELECT * FROM cardio_log ORDER BY id DESC")
    fun observeAll(): Flow<List<CardioLogEntity>>

    @Query(
        """
        SELECT gearId AS gearId, SUM(distanceM) AS distanceM
        FROM cardio_log
        WHERE gearId IS NOT NULL
        GROUP BY gearId
        """,
    )
    fun observeMileage(): Flow<List<GearMileage>>
}

data class GearMileage(
    val gearId: Long,
    val distanceM: Double,
)
