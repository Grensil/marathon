package com.example.history

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HistoryScreen(
    viewModel: RunningViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onPermissionGranted()
        }
    }

    // 앱 시작 시 권한 요청
    LaunchedEffect(Unit) {
        val permissions = buildList {
            // 위치 권한
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            // 걸음 센서 권한 (Android 10 이상)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Health Connect 권한 체크를 건너뛰고 바로 시뮬레이션 모드로 시작
        Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // 제목
                Text(
                    text = if (state.isRunning) "Running in Progress" else "Start Running",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 시간 표시
                Text(
                    text = viewModel.formatElapsedTime(state.elapsedTime),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isRunning) MaterialTheme.colorScheme.primary else Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 거리 표시
                Text(
                    text = "${viewModel.formatDistance(state.distance)} km",
                    fontSize = 24.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 메트릭 카드들
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricCard(
                        title = "Current Pace",
                        value = state.currentPace,
                        unit = "min/km"
                    )
                    MetricCard(
                        title = "Avg Pace",
                        value = state.averagePace,
                        unit = "min/km"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricCard(
                        title = "Heart Rate",
                        value = state.currentHeartRate?.toString() ?: "--",
                        unit = "bpm",
                        subtitle = state.averageHeartRate?.let { "Avg: $it" }
                    )
                    MetricCard(
                        title = "Cadence",
                        value = state.currentCadence?.toString() ?: "--",
                        unit = "spm",
                        subtitle = state.averageCadence?.let { "Avg: $it" }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 고도
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    MetricCard(
                        title = "Altitude",
                        value = state.currentAltitude?.let { String.format("%.0f", it) } ?: "--",
                        unit = "m"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 칼로리
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Calories",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${state.calories} kcal",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }



            Spacer(modifier = Modifier.height(16.dp))

                Spacer(modifier = Modifier.weight(1f))

                // 버튼 영역
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!state.isRunning || state.isPaused) {
                        // START 또는 RESUME 버튼
                        Button(
                            onClick = {
                                if (state.isPaused) {
                                    viewModel.resumeRunning()
                                } else {
                                    viewModel.startRunning()
                                }
                            },
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = !state.isLoading
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = if (state.isPaused) "RESUME" else "START",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // PAUSE 상태일 때만 STOP 버튼 표시
                        if (state.isPaused) {
                            Spacer(modifier = Modifier.width(24.dp))
                            Button(
                                onClick = { viewModel.stopRunning() },
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red
                                ),
                                enabled = !state.isLoading
                            ) {
                                Text(
                                    text = "STOP",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // PAUSE 버튼 (러닝 중일 때)
                        Button(
                            onClick = { viewModel.pauseRunning() },
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFA500) // Orange
                            ),
                            enabled = !state.isLoading
                        ) {
                            Text(
                                text = "PAUSE",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 에러 메시지
                state.error?.let { error ->
                    Text(
                        text = error,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

        // 완료 다이얼로그
        state.completedRecord?.let { record ->
            if (state.showCompletionDialog) {
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
private fun CompletionDialog(
    record: RunningRecord,
    onDismiss: () -> Unit,
    formatElapsedTime: (Long) -> String,
    formatDistance: (Double) -> String
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Running Completed!",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // 시간
                Text(
                    text = formatElapsedTime(record.elapsedTime),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 거리
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Distance:", fontWeight = FontWeight.Medium)
                    Text("${formatDistance(record.distance)} km", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 평균 페이스
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Avg Pace:", fontWeight = FontWeight.Medium)
                    Text("${record.averagePace} min/km", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 케이던스
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cadence:", fontWeight = FontWeight.Medium)
                    Text(
                        text = record.averageCadence?.let { "$it spm" } ?: "--",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            subtitle?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

