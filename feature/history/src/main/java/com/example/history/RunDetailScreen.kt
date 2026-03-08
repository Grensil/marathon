package com.example.history

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthcare.domain.model.RoutePoint
import kotlinx.collections.immutable.persistentListOf
import com.example.healthcare.domain.model.RunHistory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RunDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: RunDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    RunDetailContent(
        state = state,
        onBack = onBack
    )
}

@Composable
fun RunDetailContent(
    state: RunDetailState,
    onBack: () -> Unit
) {
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
            val session = state.session

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
                            .clickable { onBack() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.End) {
                        val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
                        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        Text(
                            text = dateFormat.format(Date(session.startTime)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = timeFormat.format(Date(session.startTime)),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Main distance
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format("%.2f", session.distanceMeters / 1000.0),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.label_kilometers),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Route Map with Google Maps
                if (state.locationPoints.size >= 2) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        RouteMapView(
                            locationPoints = state.locationPoints,
                            routeColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        )
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
                        label = stringResource(R.string.label_duration),
                        value = formatDuration(session.durationMs),
                        unit = ""
                    )
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.label_avg_pace),
                        value = session.averagePace,
                        unit = stringResource(R.string.unit_min_km)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.label_cadence),
                        value = session.averageCadence?.toString() ?: "0",
                        unit = stringResource(R.string.unit_spm)
                    )
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.label_heart_rate),
                        value = session.averageHeartRate?.toString() ?: "0",
                        unit = stringResource(R.string.unit_bpm),
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
                        label = stringResource(R.string.label_calories),
                        value = "${session.calories}",
                        unit = stringResource(R.string.unit_kcal),
                        valueColor = MaterialTheme.colorScheme.secondary
                    )
                    DetailStatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.label_steps),
                        value = if (session.totalSteps > 0) "${session.totalSteps}" else "0",
                        unit = stringResource(R.string.unit_steps)
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
                    text = stringResource(R.string.session_not_found),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RunDetailPreview() {
    val dummySession = RunHistory(
        id = "1",
        startTime = System.currentTimeMillis(),
        durationMs = 3600000,
        distanceMeters = 10000.0,
        averagePace = "06:00",
        averageHeartRate = 150,
        averageCadence = 180,
        calories = 600,
        totalSteps = 10000
    )
    val dummyState = RunDetailState(
        session = dummySession,
        locationPoints = persistentListOf(), // Maps might crash in preview
        isLoading = false
    )

    MaterialTheme {
        RunDetailContent(
            state = dummyState,
            onBack = {}
        )
    }
}

@Composable
private fun RouteMapView(
    locationPoints: List<RoutePoint>,
    routeColor: Color,
    modifier: Modifier = Modifier
) {
    val latLngPoints = remember(locationPoints) {
        locationPoints.map { LatLng(it.latitude, it.longitude) }
    }

    if (latLngPoints.size < 2) return

    val bounds = remember(latLngPoints) {
        val builder = LatLngBounds.Builder()
        latLngPoints.forEach { builder.include(it) }
        builder.build()
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bounds.center, 15f)
    }

    LaunchedEffect(bounds) {
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngBounds(bounds, 64),
            durationMs = 500
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            scrollGesturesEnabled = true,
            zoomGesturesEnabled = true,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            mapToolbarEnabled = false
        )
    ) {
        // Route polyline
        Polyline(
            points = latLngPoints,
            color = routeColor,
            width = 12f
        )

        // Start marker (green)
        Marker(
            state = MarkerState(position = latLngPoints.first()),
            title = "Start",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        )

        // End marker (red)
        Marker(
            state = MarkerState(position = latLngPoints.last()),
            title = "Finish",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        )
    }
}

@Composable
private fun DetailStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onBackground
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
            Text(
                text = unit ?: "",
                fontSize = 12.sp,
                color = if (unit.isNullOrEmpty()) Color.Transparent
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
