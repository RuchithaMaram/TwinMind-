package com.twinmind.recorder.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.twinmind.recorder.data.local.dao.AudioChunkDao
import com.twinmind.recorder.data.local.dao.MeetingDao
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.data.repository.TranscriptionRepository
import com.twinmind.recorder.service.CHANNEL_ID
import com.twinmind.recorder.service.NOTIFICATION_ID
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

private const val TAG            = "TranscriptionWorker"
private const val KEY_MEETING_ID = "meeting_id"
private const val KEY_CHUNK_ID   = "chunk_id"
private const val KEY_ALL_CHUNKS = "all_chunks"

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transcriptionRepository: TranscriptionRepository,
    private val audioChunkDao: AudioChunkDao,
    private val meetingDao: MeetingDao,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Transcribing audio...")
            .setContentText("Processing your recording")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NOTIFICATION_ID + 10, notification)
    }

    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()
        val chunkId   = inputData.getString(KEY_CHUNK_ID)
        val allChunks = inputData.getBoolean(KEY_ALL_CHUNKS, false)

        return try {
            if (allChunks || chunkId == null) {
                val chunks = audioChunkDao.getByMeeting(meetingId)
                    .filter { it.status.name != "DONE" }

                Log.d(TAG, "Processing ${chunks.size} chunks for $meetingId")

                if (chunks.isEmpty()) {
                    Log.w(TAG, "No chunks found for $meetingId — checking all")
                    val allChunksList = audioChunkDao.getByMeeting(meetingId)
                    Log.d(TAG, "Total chunks in DB: ${allChunksList.size}")
                    if (allChunksList.isEmpty()) {
                        meetingDao.setError(meetingId, "No audio recorded", MeetingStatus.ERROR)
                        return Result.failure()
                    }
                }

                var anyFailed = false
                chunks.forEach { chunk ->
                    transcriptionRepository.transcribeChunk(chunk)
                        .onFailure { anyFailed = true }
                }

                if (anyFailed) return Result.retry()

                meetingDao.updateStatus(meetingId, MeetingStatus.SUMMARIZING)
                SummaryWorker.enqueue(applicationContext, meetingId)
                Result.success()

            } else {
                val chunk = audioChunkDao.getById(chunkId) ?: return Result.failure()
                transcriptionRepository.transcribeChunk(chunk).fold(
                    onSuccess = { Result.success() },
                    onFailure = { Result.retry() }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {

        fun enqueue(context: Context, meetingId: String, chunkId: String) {
            val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_MEETING_ID, meetingId)
                        .putString(KEY_CHUNK_ID, chunkId)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.SECONDS)
                .addTag("transcribe_$meetingId")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun enqueueMeetingAll(context: Context, meetingId: String) {
            val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_MEETING_ID, meetingId)
                        .putBoolean(KEY_ALL_CHUNKS, true)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.SECONDS)
                .addTag("transcribe_all_$meetingId")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}