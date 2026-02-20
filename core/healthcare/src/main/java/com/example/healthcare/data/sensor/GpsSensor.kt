package com.example.healthcare.data.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.healthcare.BuildConfig
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class GpsData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Float?,
    val accuracy: Float,
    val timestamp: Long
)

@Singleton
class GpsSensor @Inject constructor(
    private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "GpsSensor"
        private const val UPDATE_INTERVAL_MS = 2000L
        private const val FASTEST_INTERVAL_MS = 1000L
        private const val SMALLEST_DISPLACEMENT_M = 2f
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<GpsData> = callbackFlow {
        if (!hasLocationPermission()) {
            close(IllegalStateException("Location permission not granted"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(SMALLEST_DISPLACEMENT_M)
            .setWaitForAccurateLocation(false)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val gpsData = GpsData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        speed = if (location.hasSpeed()) location.speed else null,
                        accuracy = location.accuracy,
                        timestamp = location.time
                    )
                    trySend(gpsData)
                }
            }
        }

        try {
            // Emit initial dummy for Flow start signal
            trySend(GpsData(0.0, 0.0, null, null, 0f, 0L))

            // Get last known location for quick first fix
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    trySend(GpsData(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        altitude = if (it.hasAltitude()) it.altitude else null,
                        speed = if (it.hasSpeed()) it.speed else null,
                        accuracy = it.accuracy,
                        timestamp = it.time
                    ))
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Security exception: ${e.message}")
            close(e)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Exception: ${e.message}")
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
