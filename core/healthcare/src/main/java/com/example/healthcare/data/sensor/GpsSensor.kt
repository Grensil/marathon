package com.example.healthcare.data.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.healthcare.BuildConfig
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
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    companion object {
        private const val TAG = "GpsSensor"
        private const val MIN_TIME_BETWEEN_UPDATES = 1000L
        private const val MIN_DISTANCE_CHANGE = 0f
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<GpsData> = callbackFlow {
        if (!hasLocationPermission()) {
            close(IllegalStateException("Location permission not granted"))
            return@callbackFlow
        }

        if (!isGpsEnabled()) {
            close(IllegalStateException("GPS is disabled"))
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
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

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // 초기 더미 데이터 (Flow 시작 알림)
            trySend(GpsData(0.0, 0.0, null, null, 0f, 0L))

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE,
                listener
            )

            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { last ->
                trySend(GpsData(
                    latitude = last.latitude,
                    longitude = last.longitude,
                    altitude = if (last.hasAltitude()) last.altitude else null,
                    speed = if (last.hasSpeed()) last.speed else null,
                    accuracy = last.accuracy,
                    timestamp = last.time
                ))
            }
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Security exception: ${e.message}")
            close(e)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Exception: ${e.message}")
            close(e)
        }

        awaitClose {
            locationManager.removeUpdates(listener)
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
