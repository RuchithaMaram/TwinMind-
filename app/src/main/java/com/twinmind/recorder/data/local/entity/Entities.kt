package com.twinmind.recorder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MeetingStatus { RECORDING, PAUSED, STOPPED, TRANSCRIBING, SUMMARIZING, DONE, ERROR }

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val durationMs: Long = 0L,
    val status: MeetingStatus = MeetingStatus.RECORDING,
    val summary: String? = null,
    val actionItems: String? = null,
    val keyPoints: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ChunkStatus { PENDING, UPLOADING, DONE, FAILED }

@Entity(tableName = "audio_chunks")
data class AudioChunkEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val sequenceNumber: Int,
    val filePath: String,
    val durationMs: Long,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey val chunkId: String,
    val meetingId: String,
    val sequenceNumber: Int,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
