package com.ssafy.smartcane.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import android.os.Build

class HeadingProvider(
    context: Context,
    private val onHeadingChanged: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun start(): Boolean {
        if (rotationSensor == null) return false
        return sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            // Adjust for screen orientation
            val adjustedRotationMatrix = FloatArray(9)
            @Suppress("DEPRECATION")
            val rotation = windowManager.defaultDisplay.rotation

            var axisX = SensorManager.AXIS_X
            var axisY = SensorManager.AXIS_Y

            when (rotation) {
                Surface.ROTATION_90 -> {
                    axisX = SensorManager.AXIS_Y
                    axisY = SensorManager.AXIS_MINUS_X
                }
                Surface.ROTATION_180 -> {
                    axisX = SensorManager.AXIS_MINUS_X
                    axisY = SensorManager.AXIS_MINUS_Y
                }
                Surface.ROTATION_270 -> {
                    axisX = SensorManager.AXIS_MINUS_Y
                    axisY = SensorManager.AXIS_X
                }
            }
            SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, adjustedRotationMatrix)

            val orientation = FloatArray(3)
            SensorManager.getOrientation(adjustedRotationMatrix, orientation)
            
            // Azimuth is orientation[0], convert to degrees
            val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
            // Normalize to 0..360
            val heading = (azimuthDegrees + 360) % 360
            onHeadingChanged(heading)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
