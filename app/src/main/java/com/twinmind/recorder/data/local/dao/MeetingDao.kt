package com.twinmind.recorder.data.local.dao

import androidx.room.*
import com.twinmind.recorder.data.local.entity.MeetingEntity
import com.twinmind.recorder.data.local.entity.MeetingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {

    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun observeById(id: String): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getById(id: String): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE status = 'RECORDING' OR status = 'PAUSED' LIMIT 1")
    suspend fun getActiveSession(): MeetingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meeting: MeetingEntity)

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Query("UPDATE meetings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: MeetingStatus)

    @Query("UPDATE meetings SET summary = :summary, actionItems = :actionItems, keyPoints = :keyPoints, status = :status WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String, actionItems: String, keyPoints: String, status: MeetingStatus)

    @Query("UPDATE meetings SET endTimeMs = :endTime, durationMs = :duration, status = :status WHERE id = :id")
    suspend fun finalizeRecording(id: String, endTime: Long, duration: Long, status: MeetingStatus)

    @Query("UPDATE meetings SET errorMessage = :error, status = :status WHERE id = :id")
    suspend fun setError(id: String, error: String, status: MeetingStatus)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun delete(id: String)
}
