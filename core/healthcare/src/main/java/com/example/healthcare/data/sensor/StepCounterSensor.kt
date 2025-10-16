package com.example.healthcare.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
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

    private var initialStepCount: Int? = null
    private var sessionStartSteps: Int = 0

    companion object {
        private const val TAG = "StepCounterSensor"
    }

    /**
     * 걸음 수를 실시간으로 관찰
     */
    fun observeSteps(): Flow<Int> = callbackFlow {
        Log.d(TAG, "observeSteps called, sensor available: ${stepCounterSensor != null}")

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val totalSteps = it.values[0].toInt()
                    Log.d(TAG, "onSensorChanged: totalSteps=$totalSteps")

                    // 첫 측정 시 초기값 저장
                    if (initialStepCount == null) {
                        initialStepCount = totalSteps
                        sessionStartSteps = 0
                        Log.d(TAG, "Initial step count set: $totalSteps")
                    } else {
                        // 세션 시작 이후 걸음 수 계산
                        sessionStartSteps = totalSteps - (initialStepCount ?: 0)
                        Log.d(TAG, "Session steps calculated: $sessionStartSteps")
                    }

                    trySend(sessionStartSteps)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(TAG, "Sensor accuracy changed: $accuracy")
            }
        }

        // 센서 등록
        if (stepCounterSensor != null) {
            val registered = sensorManager.registerListener(
                listener,
                stepCounterSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Sensor registered: $registered")
        } else {
            // 걸음 센서가 없는 경우 시뮬레이션
            Log.w(TAG, "Step counter sensor not available, starting simulation")
            launch {
                var simulatedSteps = 0
                while (true) {
                    simulatedSteps += (2..4).random()
                    Log.d(TAG, "Simulated steps: $simulatedSteps")
                    trySend(simulatedSteps)
                    delay(2000)
                }
            }
        }

        awaitClose {
            Log.d(TAG, "Unregistering sensor listener")
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
