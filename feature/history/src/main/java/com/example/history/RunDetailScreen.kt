package com.example.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthcare.data.local.entity.LocationPointEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RunDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: RunDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.session != null) {
            val session = state.session!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Back button and title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "<",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .let { mod ->
                                mod
                            }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.End) {
                        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        Text(
                            text = dateFormat.format(Date(session.startTime)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = timeFormat.format(Date(session.startTime)),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Main distance display
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format("%.2f", session.distanceMeters / 1000.0),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "kilometers",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Route Map Card
                if (state.locationPoints.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.5f)
                                .padding(16.dp)
                        ) {
                            RouteCanvas(
                                locationPoints = state.locationPoints,
                                routeColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Duration",
                        value = formatDuration(session.durationMs),
                        icon = null
                    )
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Avg Pace",
                        value = session.averagePace,
                        unit = "min/km"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Cadence",
                        value = session.averageCadence?.toString() ?: "--",
                        unit = "spm"
                    )
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Heart Rate",
                        value = session.averageHeartRate?.toString() ?: "--",
                        unit = "bpm",
                        valueColor = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Calories",
                        value = "${session.calories}",
                        unit = "kcal",
                        valueColor = MaterialTheme.colorScheme.secondary
                    )
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Steps",
                        value = if (session.totalSteps > 0) "${session.totalSteps}" else "--",
                        unit = "steps"
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Session not found",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun RouteCanvas(
    locationPoints: List<LocationPointEntity>,
    routeColor: Color
) {
    if (locationPoints.size < 2) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Not enough GPS data",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 14.sp
            )
        }
        return
    }

    val minLat = locationPoints.minOf { it.latitude }
    val maxLat = locationPoints.maxOf { it.latitude }
    val minLon = locationPoints.minOf { it.longitude }
    val maxLon = locationPoints.maxOf { it.longitude }

    val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
    val lonRange = (maxLon - minLon).coerceAtLeast(0.0001)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val padding = 24f
        val width = size.width - padding * 2
        val height = size.height - padding * 2

        val path = Path()
        var started = false

        locationPoints.forEach { point ->
            val x = padding + ((point.longitude - minLon) / lonRange * width).toFloat()
            val y = padding + ((maxLat - point.latitude) / latRange * height).toFloat()

            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw route
        drawPath(
            path = path,
            color = routeColor,
            style = Stroke(
                width = 4f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Start point
        val startPoint = locationPoints.first()
        val startX = padding + ((startPoint.longitude - minLon) / lonRange * width).toFloat()
        val startY = padding + ((maxLat - startPoint.latitude) / latRange * height).toFloat()
        drawCircle(
            color = Color(0xFF4CAF50),
            radius = 8f,
            center = Offset(startX, startY)
        )

        // End point
        val endPoint = locationPoints.last()
        val endX = padding + ((endPoint.longitude - minLon) / lonRange * width).toFloat()
        val endY = padding + ((maxLat - endPoint.latitude) / latRange * height).toFloat()
        drawCircle(
            color = Color(0xFFFF3B30),
            radius = 8f,
            center = Offset(endX, endY)
        )
    }
}

@Composable
private fun DetailStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String? = null,
    icon: String? = null,
    valueColor: Color = Color.White
) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                textAlign = TextAlign.Center
            )
            if (unit != null) {
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
