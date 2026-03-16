package com.twinmind.recorder.data.repository

import com.twinmind.recorder.data.local.dao.MeetingDao
import com.twinmind.recorder.data.local.entity.MeetingEntity
import com.twinmind.recorder.data.local.entity.MeetingStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepository @Inject constructor(
    private val meetingDao: MeetingDao
) {
    fun observeAll(): Flow<List<MeetingEntity>> = meetingDao.observeAll()

    fun observeById(id: String): Flow<MeetingEntity?> = meetingDao.observeById(id)

    suspend fun getById(id: String): MeetingEntity? = meetingDao.getById(id)

    suspend fun getActiveSession(): MeetingEntity? = meetingDao.getActiveSession()

    suspend fun createMeeting(meeting: MeetingEntity) = meetingDao.insert(meeting)

    suspend fun updateStatus(id: String, status: MeetingStatus) =
        meetingDao.updateStatus(id, status)

    suspend fun finalizeRecording(id: String, endTime: Long, duration: Long) =
        meetingDao.finalizeRecording(id, endTime, duration, MeetingStatus.TRANSCRIBING)

    suspend fun updateSummary(
        id: String,
        summary: String,
        actionItems: String,
        keyPoints: String
    ) = meetingDao.updateSummary(id, summary, actionItems, keyPoints, MeetingStatus.DONE)

    suspend fun setError(id: String, error: String) =
        meetingDao.setError(id, error, MeetingStatus.ERROR)

    suspend fun deleteMeeting(id: String) = meetingDao.delete(id)
}
