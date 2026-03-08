package com.example.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthcare.domain.model.RunHistory
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryListScreen(
    onSessionClick: (String) -> Unit,
    viewModel: HistoryListViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    HistoryListContent(
        sessions = sessions,
        stats = stats,
        sortOrder = sortOrder,
        onSessionClick = onSessionClick,
        onSortOrderChange = viewModel::setSortOrder,
        onDeleteSession = viewModel::deleteSession
    )
}

@Composable
fun HistoryListContent(
    sessions: ImmutableList<RunHistory>,
    stats: RunStats,
    sortOrder: SortOrder,
    onSessionClick: (String) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val hasNoSessions by remember { derivedStateOf { sessions.isEmpty() } }
    val totalDistanceFormatted by remember { derivedStateOf { String.format("%.1f", stats.totalDistanceKm) } }
    val totalTimeFormatted by remember { derivedStateOf { formatTotalTime(stats.totalDurationMs) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.activity_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Summary Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(label = stringResource(R.string.total_runs), value = "${stats.totalRuns}")
                SummaryItem(label = stringResource(R.string.total_km), value = totalDistanceFormatted)
                SummaryItem(label = stringResource(R.string.total_time), value = totalTimeFormatted)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Runs header with Edit and Sort buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recent_runs),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.weight(1f))

            if (!hasNoSessions) {
                // Edit button
                TextButton(onClick = { isEditMode = !isEditMode }) {
                    Text(
                        text = if (isEditMode) stringResource(R.string.done) else stringResource(R.string.edit),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEditMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sort button
                Box {
                    TextButton(onClick = { showSortMenu = true }) {
                        Text(
                            text = stringResource(sortOrder.labelRes),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " \u25BE",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(order.labelRes),
                                        fontWeight = if (order == sortOrder) FontWeight.Bold
                                        else FontWeight.Normal,
                                        color = if (order == sortOrder) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSortOrderChange(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (hasNoSessions) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.no_runs_yet),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.start_first_run),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    var visible by remember { mutableStateOf(true) }

                    AnimatedVisibility(
                        visible = visible,
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(200))
                    ) {
                        RunSessionItem(
                            session = session,
                            isEditMode = isEditMode,
                            onClick = { onSessionClick(session.id) },
                            onDelete = {
                                visible = false
                                onDeleteSession(session.id)
                            }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryListPreview() {
    val dummySessions = persistentListOf(
        RunHistory(
            id = "1",
            startTime = System.currentTimeMillis(),
            durationMs = 3600000,
            distanceMeters = 10000.0,
            averagePace = "06:00"
        ),
        RunHistory(
            id = "2",
            startTime = System.currentTimeMillis() - 86400000,
            durationMs = 1800000,
            distanceMeters = 5000.0,
            averagePace = "06:00"
        )
    )
    val dummyStats = RunStats(
        totalRuns = 2,
        totalDistanceKm = 15.0,
        totalDurationMs = 5400000
    )

    MaterialTheme {
        HistoryListContent(
            sessions = dummySessions,
            stats = dummyStats,
            sortOrder = SortOrder.RECENT,
            onSessionClick = {},
            onSortOrderChange = {},
            onDeleteSession = {}
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun RunSessionItem(
    session: RunHistory,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // remember: SimpleDateFormat 객체 재생성 방지
            val dateFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }
            val monthFormat = remember { SimpleDateFormat("MMM", Locale.getDefault()) }
            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

            // Date circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dateFormat.format(Date(session.startTime)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = monthFormat.format(Date(session.startTime)).uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(R.string.run_label)} - ${timeFormat.format(Date(session.startTime))}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = formatDuration(session.durationMs),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "  |  ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = session.averagePace + " " + stringResource(R.string.unit_per_km),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%.2f", session.distanceMeters / 1000.0),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.unit_km),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Delete button (edit mode) - right side
            AnimatedVisibility(
                visible = isEditMode,
                enter = fadeIn(tween(200)) + slideInHorizontally { it },
                exit = fadeOut(tween(200)) + slideOutHorizontally { it }
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u2715",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

private fun formatTotalTime(ms: Long): String {
    val hours = ms / (1000 * 60 * 60)
    val minutes = (ms / (1000 * 60)) % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
