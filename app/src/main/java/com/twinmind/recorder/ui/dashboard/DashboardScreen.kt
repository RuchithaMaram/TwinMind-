package com.twinmind.recorder.ui.dashboard

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twinmind.recorder.data.local.entity.MeetingEntity
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.ui.theme.*
import com.twinmind.recorder.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNewRecording: (String) -> Unit,
    onMeetingClick: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val meetings by viewModel.meetings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "TwinMind",
                        style = MaterialTheme.typography.titleLarge,
                        color = TealDark,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.AccountCircle, null,
                            tint = TextMid, modifier = Modifier.size(26.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgWhite)
            )
        },
        containerColor = BgWhite,
        floatingActionButton = {
            RecordFab(onClick = {
                val id = viewModel.createNewMeeting()
                onNewRecording(id)
            })
        }
    ) { padding ->
        if (meetings.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Text(
                        "Recent Recordings",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextLight,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
                items(meetings, key = { it.id }) { meeting ->
                    MeetingRow(
                        meeting = meeting,
                        onClick = { onMeetingClick(meeting.id) },
                        onDelete = { viewModel.deleteMeeting(meeting.id) }
                    )
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(TealUltraLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.MicNone, null,
                modifier = Modifier.size(40.dp), tint = TealDark)
        }
        Spacer(Modifier.height(20.dp))
        Text("No recordings yet",
            style = MaterialTheme.typography.titleMedium,
            color = TextDark, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Tap the mic button to start recording",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLight)
    }
}

@Composable
private fun MeetingRow(
    meeting: MeetingEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val (statusColor, statusLabel) = when (meeting.status) {
        MeetingStatus.RECORDING    -> TealMid   to "Recording"
        MeetingStatus.PAUSED       -> Color(0xFFE65100) to "Paused"
        MeetingStatus.TRANSCRIBING -> TealMid   to "Transcribing"
        MeetingStatus.SUMMARIZING  -> TealMid   to "Summarizing"
        MeetingStatus.DONE         -> GreenCheck to "Done"
        MeetingStatus.ERROR        -> MaterialTheme.colorScheme.error to "Error"
        MeetingStatus.STOPPED      -> TextLight  to "Stopped"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                meeting.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    FormatUtils.formatDate(meeting.startTimeMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLight
                )
                if (meeting.durationMs > 0) {
                    Text("·", style = MaterialTheme.typography.bodySmall, color = TextLight)
                    Text(
                        FormatUtils.formatDuration(meeting.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLight
                    )
                }
            }
        }

        if (meeting.status != MeetingStatus.DONE) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor.copy(alpha = 0.1f)
            ) {
                Text(
                    statusLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Icon(
            Icons.Filled.ChevronRight, null,
            modifier = Modifier.size(18.dp), tint = TextLight
        )
    }
    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 24.dp))
}

@Composable
private fun RecordFab(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Box(
        modifier = Modifier.padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(TealDark.copy(alpha = 0.15f))
        )
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            containerColor = TealDark,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(6.dp)
        ) {
            Icon(Icons.Filled.Mic, "Start Recording", modifier = Modifier.size(26.dp))
        }
    }
}
