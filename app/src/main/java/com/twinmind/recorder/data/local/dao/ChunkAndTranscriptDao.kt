package com.twinmind.recorder.data.local.dao

import androidx.room.*
import com.twinmind.recorder.data.local.entity.AudioChunkEntity
import com.twinmind.recorder.data.local.entity.ChunkStatus
import com.twinmind.recorder.data.local.entity.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {

    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId ORDER BY sequenceNumber ASC")
    fun observeByMeeting(meetingId: String): Flow<List<AudioChunkEntity>>

    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId ORDER BY sequenceNumber ASC")
    suspend fun getByMeeting(meetingId: String): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE status = 'PENDING' OR status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getPendingOrFailed(): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE id = :id")
    suspend fun getById(id: String): AudioChunkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: AudioChunkEntity)

    @Query("UPDATE audio_chunks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ChunkStatus)

    @Query("UPDATE audio_chunks SET status = :status, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markFailedAndIncrement(id: String, status: ChunkStatus = ChunkStatus.FAILED)

    @Query("UPDATE audio_chunks SET status = 'PENDING', retryCount = 0 WHERE meetingId = :meetingId")
    suspend fun resetAllToRetry(meetingId: String)

    @Query("DELETE FROM audio_chunks WHERE meetingId = :meetingId")
    suspend fun deleteByMeeting(meetingId: String)
}

@Dao
interface TranscriptDao {

    @Query("SELECT * FROM transcripts WHERE meetingId = :meetingId ORDER BY sequenceNumber ASC")
    fun observeByMeeting(meetingId: String): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE meetingId = :meetingId ORDER BY sequenceNumber ASC")
    suspend fun getByMeeting(meetingId: String): List<TranscriptEntity>

    @Query("SELECT COUNT(*) FROM transcripts WHERE meetingId = :meetingId")
    suspend fun countByMeeting(meetingId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcript: TranscriptEntity)

    @Query("DELETE FROM transcripts WHERE meetingId = :meetingId")
    suspend fun deleteByMeeting(meetingId: String)
}
