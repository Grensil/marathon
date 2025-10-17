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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
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

    @Volatile
    private var initialStepCount: Int? = null

    @Volatile
    private var currentTotalSteps: Int = 0

    @Volatile
    private var sessionStartSteps: Int = 0

    companion object {
        private const val TAG = "Logd"
    }

    /**
     * 걸음 수를 실시간으로 관찰
     */
    fun observeSteps(): Flow<Int> = callbackFlow {
        Log.d(TAG, "=== observeSteps called ===")
        Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "TYPE_STEP_COUNTER available: ${stepCounterSensor != null}")
        Log.d(TAG, "TYPE_STEP_DETECTOR available: ${stepDetectorSensor != null}")

        // 권한 체크 (Android 10 이상)
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            )
            Log.d(TAG, "ACTIVITY_RECOGNITION permission check: $permission")
            Log.d(TAG, "ACTIVITY_RECOGNITION permission granted: ${permission == PackageManager.PERMISSION_GRANTED}")
            permission == PackageManager.PERMISSION_GRANTED
        } else {
            Log.d(TAG, "Android version < 10 (SDK ${Build.VERSION.SDK_INT}), no runtime permission needed")
            true
        }

        // 위치 권한도 체크 (일부 기기에서 필요할 수 있음)
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        Log.d(TAG, "ACCESS_FINE_LOCATION permission: ${fineLocationPermission == PackageManager.PERMISSION_GRANTED}")

        // 모든 센서 정보 출력
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        Log.d(TAG, "Total sensors available: ${allSensors.size}")
        allSensors.filter { it.type == Sensor.TYPE_STEP_COUNTER || it.type == Sensor.TYPE_STEP_DETECTOR }
            .forEach { sensor ->
                Log.d(TAG, "Step sensor found: ${sensor.name}, type=${sensor.type}, vendor=${sensor.vendor}")
            }

        val stepCounterListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    currentTotalSteps = it.values[0].toInt()
                    Log.d(TAG, ">>> STEP_COUNTER onSensorChanged! totalSteps=$currentTotalSteps")

                    // 첫 측정 시 초기값 저장 및 STEP_DETECTOR 카운트와 동기화
                    val initCount = initialStepCount
                    if (initCount == null) {
                        initialStepCount = currentTotalSteps - sessionStartSteps
                        Log.d(TAG, "Initial step count set: ${currentTotalSteps - sessionStartSteps} (currentTotal=$currentTotalSteps, detectorSteps=$sessionStartSteps)")
                    } else {
                        // STEP_COUNTER 값으로 세션 걸음 수 업데이트
                        sessionStartSteps = currentTotalSteps - initCount
                        Log.d(TAG, "STEP_COUNTER sync: sessionSteps=$sessionStartSteps (total=$currentTotalSteps, initial=$initCount)")
                        trySend(sessionStartSteps).also { result ->
                            Log.d(TAG, "STEP_COUNTER trySend: $result")
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(TAG, "STEP_COUNTER accuracy: $accuracy")
            }
        }

        // STEP_DETECTOR 리스너 (걸음마다 즉시 반응)
        val stepDetectorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                // STEP_DETECTOR가 발화할 때마다 세션 걸음 수 1 증가
                sessionStartSteps++
                Log.d(TAG, ">>> STEP_DETECTOR fired! Incremented to: $sessionStartSteps")
                trySend(sessionStartSteps).also { result ->
                    Log.d(TAG, "STEP_DETECTOR trySend: $result")
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(TAG, "STEP_DETECTOR accuracy: $accuracy")
            }
        }

        // 센서 등록
        if (stepCounterSensor == null) {
            Log.d(TAG, "Step counter sensor not available on this device!")
            close(IllegalStateException("Step counter sensor not available"))
            return@callbackFlow
        }

        if (!hasPermission) {
            Log.d(TAG, "ACTIVITY_RECOGNITION permission not granted!")
            close(IllegalStateException("ACTIVITY_RECOGNITION permission not granted"))
            return@callbackFlow
        }

        // 즉시 0 방출 (Flow가 시작되었음을 알림)
        trySend(0).also { result ->
            Log.d(TAG, "Initial 0 sent: $result")
        }

        // STEP_COUNTER 등록
        val counterRegistered = sensorManager.registerListener(
            stepCounterListener,
            stepCounterSensor,
            SensorManager.SENSOR_DELAY_UI
        )
        Log.d(TAG, "STEP_COUNTER registered: $counterRegistered")
        Log.d(TAG, "STEP_COUNTER info: ${stepCounterSensor.name}, vendor: ${stepCounterSensor.vendor}")

        // STEP_DETECTOR 등록 (더 빠른 반응)
        val detectorRegistered = if (stepDetectorSensor != null) {
            sensorManager.registerListener(
                stepDetectorListener,
                stepDetectorSensor,
                SensorManager.SENSOR_DELAY_UI
            ).also { registered ->
                Log.d(TAG, "STEP_DETECTOR registered: $registered")
                Log.d(TAG, "STEP_DETECTOR info: ${stepDetectorSensor.name}, vendor: ${stepDetectorSensor.vendor}")
            }
        } else {
            Log.d(TAG, "STEP_DETECTOR not available")
            false
        }

        if (!counterRegistered && !detectorRegistered) {
            Log.d(TAG, "Failed to register any sensor listener!")
            close(IllegalStateException("Failed to register sensor listener"))
        } else {
            Log.d(TAG, "Sensor listener(s) registered successfully!")
            Log.d(TAG, "Counter: $counterRegistered, Detector: $detectorRegistered")
        }

        // 주기적으로 현재 걸음 수 방출 (센서가 느릴 때를 대비)
        launch {
            while (true) {
                delay(2000) // 2초마다
                Log.d(TAG, "Periodic update: session steps = $sessionStartSteps")
                trySend(sessionStartSteps)
            }
        }

        awaitClose {
            Log.d(TAG, "=== Unregistering sensor listeners ===")
            sensorManager.unregisterListener(stepCounterListener)
            sensorManager.unregisterListener(stepDetectorListener)
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
