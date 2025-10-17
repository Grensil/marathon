package com.example.healthcare.data.sensor

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 폰 센서 기반 걸음 수 측정
 */
@Singleton
class StepCounterSensor @Inject constructor(
    private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private var initialStepCount: Int? = null
    private var sessionStartSteps: Int = 0

    companion object {
        private const val TAG = "StepCounterSensor"
    }

    /**
     * 걸음 수를 실시간으로 관찰
     */
    fun observeSteps(): Flow<Int> = callbackFlow {
        Log.d(TAG, "=== observeSteps called ===")
        Log.d(TAG, "TYPE_STEP_COUNTER available: ${stepCounterSensor != null}")
        Log.d(TAG, "TYPE_STEP_DETECTOR available: ${stepDetectorSensor != null}")

        // 권한 체크 (Android 10 이상)
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            )
            Log.d(TAG, "ACTIVITY_RECOGNITION permission: ${permission == PackageManager.PERMISSION_GRANTED}")
            permission == PackageManager.PERMISSION_GRANTED
        } else {
            Log.d(TAG, "Android version < 10, no runtime permission needed")
            true
        }

        // 모든 센서 정보 출력
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        Log.d(TAG, "Total sensors available: ${allSensors.size}")
        allSensors.filter { it.type == Sensor.TYPE_STEP_COUNTER || it.type == Sensor.TYPE_STEP_DETECTOR }
            .forEach { sensor ->
                Log.d(TAG, "Step sensor found: ${sensor.name}, type=${sensor.type}, vendor=${sensor.vendor}")
            }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val totalSteps = it.values[0].toInt()
                    Log.d(TAG, ">>> onSensorChanged CALLED! totalSteps=$totalSteps, sensor=${it.sensor.name}")

                    // 첫 측정 시 초기값 저장
                    if (initialStepCount == null) {
                        initialStepCount = totalSteps
                        sessionStartSteps = 0
                        Log.d(TAG, "Initial step count set: $totalSteps")
                        // 초기값 설정 시에도 0을 전송
                        trySend(sessionStartSteps).also { result ->
                            Log.d(TAG, "Initial trySend result: $result")
                        }
                    } else {
                        // 세션 시작 이후 걸음 수 계산
                        sessionStartSteps = totalSteps - (initialStepCount ?: 0)
                        Log.d(TAG, "Session steps calculated: $sessionStartSteps (total=$totalSteps, initial=$initialStepCount)")
                        trySend(sessionStartSteps).also { result ->
                            Log.d(TAG, "trySend result: $result")
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(TAG, "Sensor accuracy changed: sensor=${sensor?.name}, accuracy=$accuracy")
            }
        }

        // 센서 등록
        if (stepCounterSensor == null) {
            Log.e(TAG, "Step counter sensor not available on this device!")
            close(IllegalStateException("Step counter sensor not available"))
            return@callbackFlow
        }

        if (!hasPermission) {
            Log.e(TAG, "ACTIVITY_RECOGNITION permission not granted!")
            close(IllegalStateException("ACTIVITY_RECOGNITION permission not granted"))
            return@callbackFlow
        }

        val registered = sensorManager.registerListener(
            listener,
            stepCounterSensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )

        Log.d(TAG, "Sensor registration result: $registered")

        if (!registered) {
            Log.e(TAG, "Failed to register sensor listener!")
            close(IllegalStateException("Failed to register sensor listener"))
        } else {
            Log.d(TAG, "Sensor listener successfully registered. Waiting for sensor events...")
            Log.d(TAG, "NOTE: TYPE_STEP_COUNTER only fires when you actually walk/move!")
        }

        awaitClose {
            Log.d(TAG, "=== Unregistering sensor listener ===")
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * 세션 초기화
     */
    fun resetSession() {
        Log.d(TAG, "resetSession called")
        initialStepCount = null
        sessionStartSteps = 0
    }

    /**
     * 걸음 센서 사용 가능 여부
     */
    fun isAvailable(): Boolean {
        return stepCounterSensor != null
    }
}
