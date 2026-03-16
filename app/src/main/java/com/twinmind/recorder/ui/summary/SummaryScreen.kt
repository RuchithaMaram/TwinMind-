package com.twinmind.recorder.ui.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.data.local.entity.TranscriptEntity
import com.twinmind.recorder.ui.recording.ChatBottomBar
import com.twinmind.recorder.ui.theme.*
import com.twinmind.recorder.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    meetingId: String,
    onNavigateBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(meetingId) { viewModel.init(meetingId) }

    var selectedTab by remember { mutableIntStateOf(1) }
    var showMenu by remember { mutableStateOf(false) }
    var showFabOptions by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBackIosNew, null,
                            modifier = Modifier.size(14.dp), tint = TealDark)
                        Spacer(Modifier.width(2.dp))
                        Text("Back", color = TealDark,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                },
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                            )
                            Text(
                                text = FormatUtils.formatDuration(uiState.meeting?.durationMs ?: 0),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextDark
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreHoriz, null, tint = TextDark)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            OverflowMenuItem(Icons.Outlined.Edit, "Rename") { showMenu = false }
                            OverflowMenuItem(Icons.Outlined.Link, "Share Summary Link") { showMenu = false }
                            OverflowMenuItem(Icons.Outlined.ContentCopy, "Copy Summary") { showMenu = false }
                            OverflowMenuItem(Icons.Outlined.ContentCopy, "Copy Transcript") { showMenu = false }
                            DropdownMenuItem(
                                text = {
                                    Text("Delete Note", color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium)
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp))
                                },
                                onClick = { showMenu = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgWhite)
            )
        },
        bottomBar = { ChatBottomBar() },
        containerColor = BgWhite,
        floatingActionButton = {
            Box(modifier = Modifier.padding(bottom = 60.dp)) {
                if (showFabOptions) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 64.dp)
                    ) {
                        FabOptionChip("Edit Your Notes") { showFabOptions = false }
                        FabOptionChip("Edit Summary") { showFabOptions = false }
                        SmallFloatingActionButton(
                            onClick = { showFabOptions = false },
                            containerColor = BgWhite,
                            contentColor = TextDark,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        ) {
                            Icon(Icons.Filled.Close, null)
                        }
                    }
                } else {
                    SmallFloatingActionButton(
                        onClick = { showFabOptions = true },
                        containerColor = BgWhite,
                        contentColor = TealDark,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, null)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    text = if (uiState.title.isNotBlank()) uiState.title
                    else uiState.meeting?.title ?: "Untitled",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TealDark
                    )
                )
                Spacer(Modifier.height(4.dp))
                uiState.meeting?.startTimeMs?.let { ts ->
                    val dur = FormatUtils.formatDuration(uiState.meeting?.durationMs ?: 0)
                    Text(
                        text = "${FormatUtils.formatDate(ts)} · $dur",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLight
                    )
                }
            }

            SummaryTabRow(selectedIndex = selectedTab, onTabSelected = { selectedTab = it })
            HorizontalDivider(color = DividerColor)

            when (selectedTab) {
                0 -> QuestionsTab()
                1 -> NotesTabContent(uiState = uiState,
                    onRetrySummary = { viewModel.retrySummary(meetingId) },
                    onRetryTranscript = { viewModel.retryTranscription(meetingId) })
                2 -> TranscriptTabContent(
                    transcripts = uiState.transcripts,
                    isLoading = uiState.isLoadingTranscript
                )
            }
        }
    }
}

@Composable
private fun SummaryTabRow(selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Questions", "Notes", "Transcript")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        tabs.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            Column(
                modifier = Modifier.clickable { onTabSelected(idx) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) TealDark else TextLight,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (selected) 60.dp else 0.dp)
                        .background(TealDark, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

@Composable
private fun NotesTabContent(
    uiState: SummaryUiState,
    onRetrySummary: () -> Unit,
    onRetryTranscript: () -> Unit
) {
    when {
        uiState.error != null ->
            ErrorContent(uiState.error!!, onRetrySummary, onRetryTranscript)

        uiState.meeting?.status == MeetingStatus.TRANSCRIBING ->
            LoadingContent(isTranscribing = true)

        uiState.meeting?.status == MeetingStatus.SUMMARIZING && uiState.summary.isBlank() ->
            LoadingContent(isTranscribing = false)

        uiState.meeting?.status == MeetingStatus.STOPPED && uiState.summary.isBlank()
                && uiState.transcripts.isEmpty() ->
            StoppedNoDataContent(onRetryTranscript)

        else -> NotesContent(uiState = uiState)
    }
}

@Composable
private fun NotesContent(uiState: SummaryUiState) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 100.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.isStreaming) {
            item { SyncingBanner() }
        }

        item { ShareBanner() }

        item {
            SummarySection(
                header = "Summary",
                showRefresh = true
            ) {
                SummaryTextContent(
                    title = uiState.title,
                    body = uiState.summary,
                    bulletPoints = uiState.keyPoints
                )
            }
        }

        if (uiState.actionItems.isNotEmpty()) {
            item {
                ActionItemsSection(
                    items = uiState.actionItems,
                    expanded = false
                )
            }
        }

        item { YourNotesSection() }
    }
}

@Composable
private fun SyncingBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.CheckCircle, null,
            modifier = Modifier.size(20.dp), tint = GreenCheck)
        Text(
            "Syncing transcripts...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ShareBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = BgShareBanner
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚡", fontSize = 16.sp)
                Text(
                    "Share a link to this summary!",
                    style = MaterialTheme.typography.titleSmall,
                    color = TealDark
                )
            }
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealDark)
            ) {
                Text("Share now", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SummarySection(
    header: String,
    showRefresh: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                header,
                style = MaterialTheme.typography.titleLarge,
                color = TextDark,
                fontWeight = FontWeight.Bold
            )
            if (showRefresh) {
                IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Sync, null,
                        modifier = Modifier.size(18.dp), tint = TextLight)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SummaryTextContent(title: String, body: String, bulletPoints: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = TextDark,
                fontWeight = FontWeight.Bold
            )
        }
        if (bulletPoints.isNotEmpty()) {
            bulletPoints.forEach { point ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("•", color = TextDark, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp))
                    Text(point, style = MaterialTheme.typography.bodyMedium, color = TextDark)
                }
            }
        } else if (body.isNotBlank()) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextDark)
        } else {
            Text(
                "\"...\"",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid
            )
        }
    }
}

@Composable
private fun ActionItemsSection(items: List<String>, expanded: Boolean) {
    var isExpanded by remember { mutableStateOf(expanded) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Action Items",
                style = MaterialTheme.typography.titleLarge,
                color = TextDark,
                fontWeight = FontWeight.Bold
            )
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                null, modifier = Modifier.size(20.dp), tint = TextLight
            )
        }
        Spacer(Modifier.height(10.dp))
        items.take(if (isExpanded) items.size else 3).forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = false,
                    onCheckedChange = {},
                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                    colors = CheckboxDefaults.colors(
                        uncheckedColor = DividerColor,
                        checkmarkColor = TealDark,
                        checkedColor = TealDark
                    )
                )
                Text(item, style = MaterialTheme.typography.bodyMedium, color = TextDark,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun YourNotesSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your Notes",
            style = MaterialTheme.typography.titleLarge,
            color = TextDark,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Click 'Edit Notes' to add your own notes or provide instructions to regenerate summary " +
                    "(e.g. correct spellings to fix transcription errors)",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLight
        )
    }
}

@Composable
private fun QuestionsTab() {
    val questions = listOf(
        "📝" to "Draft a summary with next steps",
        "🌟" to "Find memorable or funny quotes",
        "💡" to "What are the key insights?",
        "📋" to "Summarize key decisions made",
        "✅" to "What did I agree to do?",
        "✉️" to "Write a follow-up email",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        questions.forEach { (emoji, q) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = BgSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {}
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(emoji, fontSize = 16.sp)
                        Text(q, style = MaterialTheme.typography.bodyMedium, color = TextDark)
                    }
                    Icon(Icons.Filled.ArrowForward, null,
                        modifier = Modifier.size(16.dp), tint = TealDark)
                }
            }
        }
    }
}

@Composable
private fun TranscriptTabContent(
    transcripts: List<TranscriptEntity>,
    isLoading: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && transcripts.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = TealDark, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
                Text("Transcribing...", style = MaterialTheme.typography.bodyMedium,
                    color = TextLight)
            }
        } else if (transcripts.isEmpty()) {
            Text(
                "No transcript available",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = TextLight
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp,),
                modifier = Modifier.fillMaxSize()
            ) {
                transcripts.forEachIndexed { _, entry ->
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(
                                FormatUtils.formatShortDate(entry.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = TealDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                entry.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextDark
                            )
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = DividerColor)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {},
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy Transcript")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(isTranscribing: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = TealDark
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (isTranscribing) "Transcribing audio..." else "Generating summary...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You can close the app — it will continue in the background.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLight,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StoppedNoDataContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.ErrorOutline, null,
            modifier = Modifier.size(40.dp), tint = TextLight)
        Spacer(Modifier.height(16.dp))
        Text("No transcript found",
            style = MaterialTheme.typography.titleMedium, color = TextDark)
        Spacer(Modifier.height(8.dp))
        Text("The recording may have been too short or the audio could not be processed.",
            style = MaterialTheme.typography.bodySmall,
            color = TextLight, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = TealDark),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun ErrorContent(error: String, onRetrySummary: () -> Unit, onRetryTranscript: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(error, style = MaterialTheme.typography.bodySmall, color = TextLight,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onRetryTranscript,
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) { Text("Retry Transcript") }
            Button(
                onClick = onRetrySummary,
                colors = ButtonDefaults.buttonColors(containerColor = TealDark)
            ) { Text("Retry Summary") }
        }
    }
}

@Composable
private fun FabOptionChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = BgWhite,
        shadowElevation = 4.dp
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall,
            color = TextDark,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OverflowMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp), tint = TextMid) },
        onClick = onClick
    )
}