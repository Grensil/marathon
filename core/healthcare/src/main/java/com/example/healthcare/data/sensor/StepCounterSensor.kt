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
import com.example.healthcare.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepCounterSensor @Inject constructor(
    private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    @Suppress("unused")
    private val stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    @Volatile private var initialStepCount: Int? = null
    @Volatile private var currentTotalSteps: Int = 0
    @Volatile private var sessionSteps: Int = 0

    companion object {
        private const val TAG = "StepCounter"
    }

    fun observeSteps(): Flow<Int> = callbackFlow {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (stepCounterSensor == null) {
            close(IllegalStateException("Step counter sensor not available"))
            return@callbackFlow
        }

        if (!hasPermission) {
            close(IllegalStateException("ACTIVITY_RECOGNITION permission not granted"))
            return@callbackFlow
        }

        trySend(0)

        val stepCounterListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    currentTotalSteps = it.values[0].toInt()
                    val initCount = initialStepCount
                    if (initCount == null) {
                        initialStepCount = currentTotalSteps - sessionSteps
                    } else {
                        sessionSteps = currentTotalSteps - initCount
                        trySend(sessionSteps)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val counterRegistered = sensorManager.registerListener(
            stepCounterListener, stepCounterSensor, SensorManager.SENSOR_DELAY_UI
        )

        if (!counterRegistered) {
            close(IllegalStateException("Failed to register sensor listener"))
        }

        // 주기적 업데이트 (센서 지연 대비)
        launch {
            while (isActive) {
                delay(2000)
                trySend(sessionSteps)
            }
        }

        awaitClose {
            sensorManager.unregisterListener(stepCounterListener)
        }
    }

    fun resetSession() {
        initialStepCount = null
        sessionSteps = 0
    }

    fun isAvailable(): Boolean = stepCounterSensor != null
}
