package com.twinmind.recorder.worker

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.twinmind.recorder.data.local.dao.MeetingDao
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.data.repository.TranscriptionRepository
import com.twinmind.recorder.service.CHANNEL_ID
import com.twinmind.recorder.service.NOTIFICATION_ID
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val TAG            = "SummaryWorker"
private const val KEY_MEETING_ID = "meeting_id"

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transcriptionRepository: TranscriptionRepository,
    private val meetingDao: MeetingDao,
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Generating summary...")
            .setContentText("AI is processing your recording")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NOTIFICATION_ID + 11, notification)
    }

    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()

        return try {
            val fullTranscript = transcriptionRepository.getFullTranscript(meetingId)
            if (fullTranscript.isBlank()) {
                Log.e(TAG, "No transcript for $meetingId")
                meetingDao.setError(meetingId, "No transcript available", MeetingStatus.ERROR)
                return Result.failure()
            }

            Log.d(TAG, "Generating summary for $meetingId (${fullTranscript.length} chars)")
            meetingDao.updateStatus(meetingId, MeetingStatus.SUMMARIZING)

            transcriptionRepository.generateSummary(
                meetingId      = meetingId,
                fullTranscript = fullTranscript,
                onPartialUpdate = { title, summary, actionItems, keyPoints ->
                    val combined = if (title.isNotBlank()) "$title\n\n$summary" else summary
                    meetingDao.updateSummary(
                        id          = meetingId,
                        summary     = combined,
                        actionItems = gson.toJson(actionItems),
                        keyPoints   = gson.toJson(keyPoints),
                        status      = MeetingStatus.SUMMARIZING
                    )
                }
            ).onSuccess {
                val current = meetingDao.getById(meetingId)
                meetingDao.updateSummary(
                    id          = meetingId,
                    summary     = current?.summary ?: "",
                    actionItems = current?.actionItems ?: "[]",
                    keyPoints   = current?.keyPoints ?: "[]",
                    status      = MeetingStatus.DONE
                )
                Log.d(TAG, "Summary DONE for $meetingId")

            }.onFailure { e ->
                Log.e(TAG, "Summary failed", e)
                meetingDao.setError(meetingId, e.message ?: "Summary failed", MeetingStatus.ERROR)
                return Result.retry()
            }

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Worker exception", e)
            meetingDao.setError(meetingId, e.message ?: "Unknown error", MeetingStatus.ERROR)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun enqueue(context: Context, meetingId: String) {
            val request = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_MEETING_ID, meetingId)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.SECONDS)
                .addTag("summary_$meetingId")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}