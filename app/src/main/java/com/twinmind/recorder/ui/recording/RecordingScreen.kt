package com.twinmind.recorder.ui.recording

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.data.local.entity.TranscriptEntity
import com.twinmind.recorder.service.RecordingStatus
import com.twinmind.recorder.ui.theme.*
import com.twinmind.recorder.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    meetingId: String,
    onNavigateBack: () -> Unit,
    onRecordingStopped: (String) -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(meetingId) { viewModel.init(meetingId) }

    LaunchedEffect(uiState.meeting?.status) {
        val s = uiState.meeting?.status
        if (s == MeetingStatus.TRANSCRIBING ||
            s == MeetingStatus.SUMMARIZING  ||
            s == MeetingStatus.DONE) {
            onRecordingStopped(meetingId)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            RecordingTopBar(
                elapsedMs   = uiState.elapsedMs,
                status      = uiState.status,
                isRecording = uiState.isRecording,
                onBack      = onNavigateBack
            )
        },
        bottomBar = {
            StopBar(
                elapsedMs = uiState.elapsedMs,
                onStop    = { viewModel.stopRecording(meetingId) }
            )
        },
        containerColor = BgWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val titleText = when (uiState.status) {
                RecordingStatus.PAUSED_CALL  -> "Paused — Phone call"
                RecordingStatus.PAUSED_FOCUS -> "Paused — Audio focus lost"
                else                         -> "Listening and taking notes..."
            }
            Text(
                text     = titleText,
                style    = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color      = TealDark
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )

            RecordingTabRow(
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )
            HorizontalDivider(color = DividerColor)

            when (selectedTab) {
                0 -> LiveTranscriptTab(transcripts = uiState.liveTranscripts)
                1 -> NotesTab(status = uiState.status)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingTopBar(
    elapsedMs: Long,
    status: RecordingStatus,
    isRecording: Boolean,
    onBack: () -> Unit
) {
    val blink = rememberInfiniteTransition(label = "blink")
    val dotAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue  = 0.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dot"
    )

    TopAppBar(
        navigationIcon = {
            TextButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBackIosNew, null,
                    modifier = Modifier.size(13.dp), tint = TealDark)
                Spacer(Modifier.width(2.dp))
                Text("Back", color = TealDark,
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
        title = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(
                                Color(0xFFE53935).copy(
                                    alpha = if (isRecording &&
                                        status == RecordingStatus.RECORDING) dotAlpha
                                    else 1f
                                )
                            )
                    )
                    Text(
                        FormatUtils.formatDuration(elapsedMs),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextDark
                    )
                }
            }
        },
        actions = { Spacer(Modifier.width(56.dp)) },
        colors  = TopAppBarDefaults.topAppBarColors(containerColor = BgWhite)
    )
}

@Composable
fun StopBar(
    elapsedMs: Long,
    onStop: () -> Unit
) {
    Surface(
        modifier        = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color           = BgWhite
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = { },
                shape          = RoundedCornerShape(24.dp),
                border         = ButtonDefaults.outlinedButtonBorder(enabled = true),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Outlined.AutoAwesome, null,
                    modifier = Modifier.size(14.dp), tint = OrangeAccent)
                Spacer(Modifier.width(6.dp))
                Text("Chat", color = TextDark,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
            }

            Surface(
                shape = RoundedCornerShape(32.dp),
                color = TealDark
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 18.dp, end = 6.dp,
                            top = 6.dp, bottom = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Pause, null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White)

                    Text(
                        FormatUtils.formatDuration(elapsedMs),
                        style      = MaterialTheme.typography.titleSmall,
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        onClick = onStop,
                        shape   = RoundedCornerShape(24.dp),
                        color   = Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Filled.Stop, null,
                                modifier = Modifier.size(16.dp),
                                tint     = Color(0xFFE53935))
                            Text("Stop",
                                style      = MaterialTheme.typography.labelMedium,
                                color      = TextDark,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteTabRow(selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Transcript", "Notes")
    RecordingTabRow(selectedIndex = selectedIndex, onTabSelected = onTabSelected)
}

@Composable
fun RecordingTabRow(selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Transcript", "Notes")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        tabs.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(Modifier)
                    .padding(vertical = 10.dp)
                    .then(
                        Modifier.clickable(
                            onClick = { onTabSelected(idx) },
                            indication = null,
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            }
                        )
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = if (selected) TealDark else TextLight,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (selected) 70.dp else 0.dp)
                        .background(TealDark, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

@Composable
private fun LiveTranscriptTab(transcripts: List<TranscriptEntity>) {
    if (transcripts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(TealDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Equalizer, null,
                        modifier = Modifier.size(28.dp),
                        tint     = Color.White)
                }
                Text("Live Transcript",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = TealDark,
                    fontWeight = FontWeight.Bold)
                Text(
                    "Your conversation will appear here in about 15 seconds. " +
                            "The Transcript updates automatically as you speak.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = TextLight,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier            = Modifier.fillMaxSize()
        ) {
            items(transcripts) { entry ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        FormatUtils.formatShortDate(entry.createdAt),
                        style      = MaterialTheme.typography.labelMedium,
                        color      = TealDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDark)
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = DividerColor)
                }
            }
        }
    }
}

@Composable
private fun NotesTab(status: RecordingStatus) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        if (status == RecordingStatus.PAUSED_CALL ||
            status == RecordingStatus.PAUSED_FOCUS) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF3E0)
            ) {
                Text(
                    status.label,
                    modifier   = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style      = MaterialTheme.typography.labelMedium,
                    color      = Color(0xFFE65100),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        val inf = rememberInfiniteTransition(label = "dots")
        val d1 by inf.animateFloat(0.3f, 1f,
            infiniteRepeatable(tween(500), RepeatMode.Reverse), "d1")
        val d2 by inf.animateFloat(0.3f, 1f,
            infiniteRepeatable(tween(500, delayMillis = 170), RepeatMode.Reverse), "d2")
        val d3 by inf.animateFloat(0.3f, 1f,
            infiniteRepeatable(tween(500, delayMillis = 340), RepeatMode.Reverse), "d3")

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(d1, d2, d3).forEach { alpha ->
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(TealDark.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
fun ChatBottomBar() {
    Surface(
        modifier        = Modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color           = BgWhite
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = BgWhite,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            Modifier.background(BgWhite,
                                RoundedCornerShape(28.dp))
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null,
                        modifier = Modifier.size(15.dp),
                        tint     = OrangeAccent)
                    Spacer(Modifier.width(6.dp))
                    Text("Chat with ", style = MaterialTheme.typography.bodyMedium,
                        color = TextMid)
                    Text("this note", style = MaterialTheme.typography.bodyMedium,
                        color = OrangeAccent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}