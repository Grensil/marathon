package com.example.history

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HistoryScreen(
    onAudioEvent: ((AudioEvent) -> Unit)? = null,
    viewModel: RunningViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Collect audio events and forward to caller
    if (onAudioEvent != null) {
        LaunchedEffect(Unit) {
            viewModel.audioEvents.collect { event ->
                onAudioEvent(event)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onPermissionGranted()
        }
    }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val requiredPermissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 아직 허용되지 않은 권한만 요청
        val notGranted = requiredPermissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            viewModel.onPermissionGranted()
        }
    }

    // derivedStateOf: state는 매초 바뀌지만, 이 값들은 거의 안 바뀜 → 불필요한 리컴포지션 방지
    val isInRunningMode by remember { derivedStateOf { state.isRunning || state.isPaused } }
    val showDialog by remember { derivedStateOf { state.showCompletionDialog } }
    val completedRecord by remember { derivedStateOf { state.completedRecord } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isInRunningMode) {
            RunningModeScreen(
                state = state,
                formatElapsedTime = viewModel::formatElapsedTime,
                formatDistance = viewModel::formatDistance,
                onPauseClick = viewModel::pauseRunning,
                onResumeClick = viewModel::resumeRunning,
                onStopClick = viewModel::stopRunning
            )
        } else {
            IdleModeScreen(
                state = state,
                onStartClick = viewModel::startRunning
            )
        }

        // Completion Dialog
        completedRecord?.let { record ->
            if (showDialog) {
                CompletionDialog(
                    record = record,
                    onDismiss = { viewModel.dismissCompletionDialog() },
                    formatElapsedTime = viewModel::formatElapsedTime,
                    formatDistance = viewModel::formatDistance
                )
            }
        }
    }
}

@Composable
private fun IdleModeScreen(
    state: RunningState,
    onStartClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.marathon_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.ready_to_run),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Distance placeholder
        Text(
            text = "0.00",
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        )
        Text(
            text = stringResource(R.string.unit_km),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.weight(1f))

        // START button
        Button(
            onClick = onStartClick,
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            enabled = !state.isLoading
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = stringResource(R.string.start),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RunningModeScreen(
    state: RunningState,
    formatElapsedTime: (Long) -> String,
    formatDistance: (Double) -> String,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Status indicator - pill/chip style
        val statusColor = if (state.isPaused) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isPaused) stringResource(R.string.status_paused)
                           else stringResource(R.string.status_running),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Elapsed Time
        Text(
            text = formatElapsedTime(state.elapsedTime),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Distance - main focus
        Text(
            text = formatDistance(state.distance),
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 96.sp
        )
        Text(
            text = stringResource(R.string.unit_km),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // remember(key): state는 매초 바뀌지만, 각 메트릭은 덜 바뀜 → 불필요한 문자열 변환 방지
        val heartRateText = remember(state.currentHeartRate) {
            state.currentHeartRate?.toString() ?: "--"
        }
        val cadenceText = remember(state.currentCadence) {
            state.currentCadence?.toString() ?: "--"
        }
        val altitudeText = remember(state.currentAltitude) {
            state.currentAltitude?.let { String.format("%.0f", it) } ?: "--"
        }
        val caloriesText = remember(state.calories) {
            state.calories.toString()
        }

        // Metrics Grid - 2x2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.label_current_pace),
                value = state.currentPace,
                unit = stringResource(R.string.unit_min_km)
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.label_avg_pace),
                value = state.averagePace,
                unit = stringResource(R.string.unit_min_km)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.label_heart_rate),
                value = heartRateText,
                unit = stringResource(R.string.unit_bpm),
                accentColor = MaterialTheme.colorScheme.tertiary
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.label_cadence),
                value = cadenceText,
                unit = stringResource(R.string.unit_spm)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Altitude & Calories row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.label_altitude),
                value = altitudeText,
                unit = stringResource(R.string.unit_m)
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.label_calories),
                value = caloriesText,
                unit = stringResource(R.string.unit_kcal),
                accentColor = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isPaused) {
                // STOP button - outlined for clear distinction
                OutlinedButton(
                    onClick = onStopClick,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    ),
                    enabled = !state.isLoading
                ) {
                    Text(
                        text = stringResource(R.string.action_stop),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // RESUME button
                Button(
                    onClick = onResumeClick,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = !state.isLoading
                ) {
                    Text(
                        text = stringResource(R.string.action_resume),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                // PAUSE button
                Button(
                    onClick = onPauseClick,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.action_pause),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IdleModePreview() {
    MaterialTheme {
        IdleModeScreen(
            state = RunningState(hasPermissions = true),
            onStartClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RunningModePreview() {
    MaterialTheme {
        RunningModeScreen(
            state = RunningState(
                isRunning = true,
                elapsedTime = 125000L,
                distance = 1500.0,
                currentPace = "05:30",
                averagePace = "05:45",
                currentHeartRate = 145,
                currentCadence = 175,
                currentAltitude = 45.0,
                calories = 120
            ),
            formatElapsedTime = { "02:05" },
            formatDistance = { "1.50" },
            onPauseClick = {},
            onResumeClick = {},
            onStopClick = {}
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    accentColor: Color? = null
) {
    val isPlaceholder = value == "--:--" || value == "--" || value == "0"
    val valueAlpha = if (isPlaceholder) 0.35f else 1f

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = (accentColor ?: MaterialTheme.colorScheme.onBackground)
                        .copy(alpha = valueAlpha)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CompletionDialog(
    record: RunningRecord,
    onDismiss: () -> Unit,
    formatElapsedTime: (Long) -> String,
    formatDistance: (Double) -> String
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.run_complete),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Distance
                Text(
                    text = formatDistance(record.distance),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.unit_km),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = stringResource(R.string.label_time),
                        value = formatElapsedTime(record.elapsedTime)
                    )
                    StatItem(
                        label = stringResource(R.string.label_avg_pace),
                        value = record.averagePace,
                        subValue = stringResource(R.string.unit_min_km)
                    )
                    StatItem(
                        label = stringResource(R.string.label_cadence),
                        value = record.averageCadence?.toString() ?: "--",
                        subValue = if (record.averageCadence != null) stringResource(R.string.unit_spm) else null
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    stringResource(R.string.action_done),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    )
}

@Composable
private fun StatItem(label: String, value: String, subValue: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        if (subValue != null) {
            Text(
                text = subValue,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
