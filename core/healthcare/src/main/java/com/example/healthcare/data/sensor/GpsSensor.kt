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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GPS 데이터 모델
 */
data class GpsData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?, // 고도 (미터)
    val speed: Float?, // 속도 (m/s)
    val accuracy: Float, // 정확도 (미터)
    val timestamp: Long
)

/**
 * GPS 센서를 통한 위치, 속도, 고도 측정
 */
@Singleton
class GpsSensor @Inject constructor(
    private val context: Context
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    companion object {
        private const val TAG = "Logd"
        private const val MIN_TIME_BETWEEN_UPDATES = 1000L // 1초
        private const val MIN_DISTANCE_CHANGE = 0f // 0미터 (모든 업데이트 받기)
    }

    /**
     * GPS 권한 확인
     */
    fun hasLocationPermission(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocationGranted || coarseLocationGranted
    }

    /**
     * GPS 활성화 여부 확인
     */
    fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * GPS 데이터를 실시간으로 관찰
     */
    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<GpsData> = callbackFlow {
        Log.d(TAG, "=== observeLocation called ===")
        Log.d(TAG, "GPS enabled: ${isGpsEnabled()}")
        Log.d(TAG, "Has permission: ${hasLocationPermission()}")

        if (!hasLocationPermission()) {
            Log.d(TAG, "Location permission not granted!")
            close(IllegalStateException("Location permission not granted"))
            return@callbackFlow
        }

        if (!isGpsEnabled()) {
            Log.d(TAG, "GPS is disabled!")
            close(IllegalStateException("GPS is disabled"))
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, ">>> Location changed: lat=${location.latitude}, lon=${location.longitude}, " +
                        "altitude=${location.altitude}, speed=${location.speed}, accuracy=${location.accuracy}")

                val gpsData = GpsData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    speed = if (location.hasSpeed()) location.speed else null,
                    accuracy = location.accuracy,
                    timestamp = location.time
                )

                trySend(gpsData).also { result ->
                    Log.d(TAG, "trySend result: $result")
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                Log.d(TAG, "GPS status changed: provider=$provider, status=$status")
            }

            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "GPS provider enabled: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "GPS provider disabled: $provider")
            }
        }

        try {
            // 즉시 더미 데이터 방출 (Flow가 시작되었음을 알림)
            trySend(GpsData(0.0, 0.0, null, null, 0f, 0L)).also { result ->
                Log.d(TAG, "Initial dummy GPS data sent: $result")
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE,
                listener
            )
            Log.d(TAG, "GPS listener registered successfully")

            // 마지막 알려진 위치 가져오기 (즉시 첫 데이터 제공)
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { lastLocation ->
                Log.d(TAG, "Last known location: lat=${lastLocation.latitude}, lon=${lastLocation.longitude}")
                trySend(GpsData(
                    latitude = lastLocation.latitude,
                    longitude = lastLocation.longitude,
                    altitude = if (lastLocation.hasAltitude()) lastLocation.altitude else null,
                    speed = if (lastLocation.hasSpeed()) lastLocation.speed else null,
                    accuracy = lastLocation.accuracy,
                    timestamp = lastLocation.time
                ))
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "Security exception when requesting location updates: ${e.message}")
            close(e)
        } catch (e: Exception) {
            Log.d(TAG, "Exception when requesting location updates: ${e.message}")
            close(e)
        }

        awaitClose {
            Log.d(TAG, "=== Removing location listener ===")
            locationManager.removeUpdates(listener)
        }
    }

    /**
     * 두 GPS 좌표 사이의 거리 계산 (미터)
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
