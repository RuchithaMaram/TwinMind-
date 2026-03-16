package com.twinmind.recorder.ui.summary

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.twinmind.recorder.data.local.entity.MeetingEntity
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.data.local.entity.TranscriptEntity
import com.twinmind.recorder.data.repository.MeetingRepository
import com.twinmind.recorder.data.repository.TranscriptionRepository
import com.twinmind.recorder.worker.SummaryWorker
import com.twinmind.recorder.worker.TranscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryUiState(
    val meeting: MeetingEntity?           = null,
    val transcripts: List<TranscriptEntity> = emptyList(),
    val title: String                     = "",
    val summary: String                   = "",
    val actionItems: List<String>         = emptyList(),
    val keyPoints: List<String>           = emptyList(),
    val isLoadingTranscript: Boolean      = true,
    val isLoadingSummary: Boolean         = false,
    val isStreaming: Boolean              = false,
    val error: String?                    = null,
)

private const val TAG = "SummaryViewModel"

@HiltViewModel
class SummaryViewModel @Inject constructor(
    application: Application,
    private val meetingRepository: MeetingRepository,
    private val transcriptionRepository: TranscriptionRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()
    private val gson = Gson()

    fun init(meetingId: String) {
        viewModelScope.launch {
            meetingRepository.observeById(meetingId).collect { meeting ->
                if (meeting != null) {
                    Log.d(TAG, "Meeting update: status=${meeting.status} summary=${meeting.summary?.take(50)}")
                    handleMeetingUpdate(meeting, meetingId)
                }
            }
        }

        viewModelScope.launch {
            transcriptionRepository.observeTranscripts(meetingId).collect { transcripts ->
                _uiState.update { it.copy(transcripts = transcripts, isLoadingTranscript = false) }
            }
        }

        viewModelScope.launch {
            val meeting = meetingRepository.getById(meetingId) ?: return@launch
            when (meeting.status) {
                MeetingStatus.TRANSCRIBING -> {
                    Log.d(TAG, "Re-kicking TranscriptionWorker for $meetingId")
                    TranscriptionWorker.enqueueMeetingAll(getApplication(), meetingId)
                }
                MeetingStatus.SUMMARIZING -> {
                    Log.d(TAG, "Re-kicking SummaryWorker for $meetingId")
                    SummaryWorker.enqueue(getApplication(), meetingId)
                }
                MeetingStatus.STOPPED -> {
                    Log.d(TAG, "Meeting STOPPED with no transcription, starting now")
                    meetingRepository.updateStatus(meetingId, MeetingStatus.TRANSCRIBING)
                    TranscriptionWorker.enqueueMeetingAll(getApplication(), meetingId)
                }
                else -> Unit
            }
        }
    }

    private fun handleMeetingUpdate(meeting: MeetingEntity, meetingId: String) {
        val actionItems = parseJsonList(meeting.actionItems)
        val keyPoints   = parseJsonList(meeting.keyPoints)

        val (parsedTitle, parsedBody) = parseSummaryField(meeting.summary)

        _uiState.update { current ->
            current.copy(
                meeting          = meeting,
                isLoadingSummary = meeting.status == MeetingStatus.TRANSCRIBING ||
                        meeting.status == MeetingStatus.SUMMARIZING,
                isStreaming      = meeting.status == MeetingStatus.SUMMARIZING,
                error            = if (meeting.status == MeetingStatus.ERROR)
                    meeting.errorMessage ?: "Something went wrong"
                else null,
                title       = parsedTitle.ifBlank { current.title },
                summary     = parsedBody.ifBlank  { current.summary },
                actionItems = if (actionItems.isNotEmpty()) actionItems else current.actionItems,
                keyPoints   = if (keyPoints.isNotEmpty())   keyPoints   else current.keyPoints,
            )
        }
    }

    fun retrySummary(meetingId: String) {
        _uiState.update { it.copy(error = null, isLoadingSummary = true) }
        viewModelScope.launch {
            meetingRepository.updateStatus(meetingId, MeetingStatus.SUMMARIZING)
            SummaryWorker.enqueue(getApplication(), meetingId)
        }
    }

    fun retryTranscription(meetingId: String) {
        _uiState.update { it.copy(error = null, isLoadingTranscript = true) }
        viewModelScope.launch {
            meetingRepository.updateStatus(meetingId, MeetingStatus.TRANSCRIBING)
            TranscriptionWorker.enqueueMeetingAll(getApplication(), meetingId)
        }
    }

    private fun parseSummaryField(raw: String?): Pair<String, String> {
        if (raw.isNullOrBlank()) return Pair("", "")
        val lines = raw.lines()
        return if (lines.size >= 3 && lines[1].isBlank()) {
            Pair(lines[0].trim(), lines.drop(2).joinToString("\n").trim())
        } else {
            Pair("", raw.trim())
        }
    }

    private fun parseJsonList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}