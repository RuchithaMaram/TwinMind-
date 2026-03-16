package com.twinmind.recorder.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.recorder.data.local.entity.MeetingEntity
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.data.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
) : ViewModel() {

    val meetings: StateFlow<List<MeetingEntity>> = meetingRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { cleanupStaleSessions() }
    }

    private suspend fun cleanupStaleSessions() {
        val stale = meetingRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            .value
            .filter { it.status == MeetingStatus.RECORDING || it.status == MeetingStatus.PAUSED }

        stale.forEach { meeting ->
            meetingRepository.updateStatus(meeting.id, MeetingStatus.STOPPED)
        }
    }

    fun createNewMeeting(): String {
        val id    = UUID.randomUUID().toString()
        val title = buildTitle()
        viewModelScope.launch {
            meetingRepository.createMeeting(
                MeetingEntity(
                    id           = id,
                    title        = title,
                    startTimeMs  = System.currentTimeMillis()
                )
            )
        }
        return id
    }

    fun deleteMeeting(id: String) {
        viewModelScope.launch { meetingRepository.deleteMeeting(id) }
    }

    private fun buildTitle(): String {
        val sdf = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault())
        return "Meeting — ${sdf.format(Date())}"
    }
}