package com.twinmind.recorder.ui.recording

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.recorder.data.local.entity.MeetingEntity
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.data.local.entity.TranscriptEntity
import com.twinmind.recorder.data.repository.MeetingRepository
import com.twinmind.recorder.data.repository.TranscriptionRepository
import com.twinmind.recorder.service.ACTION_STOP
import com.twinmind.recorder.service.RecordingService
import com.twinmind.recorder.service.RecordingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordingUiState(
    val meeting: MeetingEntity?        = null,
    val elapsedMs: Long                = 0L,
    val status: RecordingStatus        = RecordingStatus.RECORDING,
    val isRecording: Boolean           = true,
    val isPaused: Boolean              = false,
    val liveTranscripts: List<TranscriptEntity> = emptyList(),
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    application: Application,
    private val meetingRepository: MeetingRepository,
    private val transcriptionRepository: TranscriptionRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var timerRunning = false

    fun init(meetingId: String) {
        startRecordingService(meetingId)

        viewModelScope.launch {
            meetingRepository.observeById(meetingId).collect { meeting ->
                val paused = meeting?.status == MeetingStatus.PAUSED
                val recordingStatus = when (meeting?.status) {
                    MeetingStatus.PAUSED -> RecordingStatus.PAUSED_CALL
                    MeetingStatus.STOPPED,
                    MeetingStatus.TRANSCRIBING,
                    MeetingStatus.SUMMARIZING,
                    MeetingStatus.DONE,
                    MeetingStatus.ERROR -> RecordingStatus.STOPPED
                    else -> RecordingStatus.RECORDING
                }
                val stillRecording = meeting?.status == MeetingStatus.RECORDING ||
                        meeting?.status == MeetingStatus.PAUSED

                _uiState.update {
                    it.copy(
                        meeting     = meeting,
                        isPaused    = paused,
                        status      = recordingStatus,
                        isRecording = stillRecording
                    )
                }

                if (!stillRecording) stopTimer()
            }
        }

        viewModelScope.launch {
            transcriptionRepository.observeTranscripts(meetingId).collect { transcripts ->
                _uiState.update { it.copy(liveTranscripts = transcripts) }
            }
        }

        startTimer()
    }

    private fun startTimer() {
        if (timerRunning) return
        timerRunning = true
        viewModelScope.launch {
            while (timerRunning) {
                delay(1000L)
                if (!_uiState.value.isPaused && _uiState.value.isRecording) {
                    _uiState.update { it.copy(elapsedMs = it.elapsedMs + 1000L) }
                }
            }
        }
    }

    private fun stopTimer() { timerRunning = false }

    private fun startRecordingService(meetingId: String) {
        val intent = RecordingService.startIntent(getApplication(), meetingId)
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun stopRecording(meetingId: String) {
        stopTimer()
        _uiState.update { it.copy(isRecording = false, status = RecordingStatus.STOPPED) }
        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}