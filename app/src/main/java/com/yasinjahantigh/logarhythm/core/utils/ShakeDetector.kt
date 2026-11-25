package com.yasinjahantigh.logarhythm.core.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Lightweight accelerometer shake detector requiring two sharp acceleration peaks within 600ms
 * and enforcing a 3.5s cooldown so normal handling and pocket bumps don't trigger bug reports.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var firstPeakMs = 0L
    private var lastTriggerMs = 0L
    private var registered = false

    fun start() {
        if (registered || accelerometer == null) return
        registered = sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        ) == true
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
        firstPeakMs = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.size < 3) return
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce < G_THRESHOLD) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < COOLDOWN_MS) return

        val elapsedSinceFirst = now - firstPeakMs
        if (firstPeakMs == 0L || elapsedSinceFirst > DOUBLE_PEAK_WINDOW_MS) {
            firstPeakMs = now
        } else if (elapsedSinceFirst >= MIN_PEAK_SEPARATION_MS) {
            firstPeakMs = 0L
            lastTriggerMs = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val G_THRESHOLD = 2.7f
        const val MIN_PEAK_SEPARATION_MS = 110L
        const val DOUBLE_PEAK_WINDOW_MS = 650L
        const val COOLDOWN_MS = 3_500L
    }
}
