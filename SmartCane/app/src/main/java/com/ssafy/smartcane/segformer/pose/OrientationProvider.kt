package com.ssafy.smartcane.segformer.pose

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

/**
 * Subscribes to the fused TYPE_ROTATION_VECTOR sensor and exposes the gravity
 * direction expressed in the back camera's optical frame.
 *
 * Provides per-frame pitch/roll information without ARCore; combined with a
 * fixed camera height this is enough to convert mask pixels to metric ground
 * coordinates ("X m ahead, Y m to the side") in [GroundPlaneProjector].
 *
 * For external cameras rigidly mounted to the phone (e.g. DJI Osmo Action 4),
 * pass a non-null [cameraMountRotation] that maps a vector expressed in the
 * phone back-camera optical frame to the same vector expressed in the
 * external camera's optical frame. Identity for the built-in camera, see
 * [OsmoMount.R_PHONE_TO_OSMO] for a 3x3 row-major example.
 */
class OrientationProvider(
    context: Context,
    private val displayRotation: Int,
    private val cameraMountRotation: FloatArray? = null,
) : SensorEventListener {
    private val sensorManager = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrixNatural = FloatArray(9)
    private val rotationMatrixScreen = FloatArray(9)

    private val lock = Any()
    private val cameraToWorldRotation = FloatArray(9)
    private var gx = 0f
    private var gy = 1f
    private var gz = 0f
    private var timestampNs = 0L
    private var hasReading = false
    private var registered = false

    fun start() {
        val sensor = rotationSensor ?: return
        if (registered) return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        registered = true
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
    }

    fun snapshot(): GravitySnapshot = synchronized(lock) {
        GravitySnapshot(
            gravityCam = floatArrayOf(gx, gy, gz),
            cameraToWorldRotation = cameraToWorldRotation.copyOf(),
            timestampNs = timestampNs,
            hasReading = hasReading,
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrixNatural, event.values)

        val (axisX, axisY) = remapAxes(displayRotation)
        SensorManager.remapCoordinateSystem(
            rotationMatrixNatural,
            axisX,
            axisY,
            rotationMatrixScreen,
        )

        // R_screen (row-major) maps screen-frame vectors to world ENU.
        // Physical down in world ENU is (0, 0, -1); converting that back to
        // screen coordinates requires R_screen^T * down = -(R[2,0], R[2,1], R[2,2]).
        val gScreenX = -rotationMatrixScreen[6]
        val gScreenY = -rotationMatrixScreen[7]
        val gScreenZ = -rotationMatrixScreen[8]

        // Screen frame (x=right, y=up of screen, z=out of screen toward user)
        // -> back-camera optical frame (x=right, y=down, z=forward into scene).
        val gravityPhoneCam = floatArrayOf(gScreenX, -gScreenY, -gScreenZ)
        val rPhoneCamToWorld = floatArrayOf(
            rotationMatrixScreen[0], -rotationMatrixScreen[1], -rotationMatrixScreen[2],
            rotationMatrixScreen[3], -rotationMatrixScreen[4], -rotationMatrixScreen[5],
            rotationMatrixScreen[6], -rotationMatrixScreen[7], -rotationMatrixScreen[8],
        )

        // Apply rigid mount rotation R_phone_to_ext so downstream consumers see
        // gravity / camera-to-world in the *external* camera's optical frame
        // when a UVC camera is used. With no mount rotation this is a no-op.
        val mount = cameraMountRotation
        val gravityFinal: FloatArray
        val rFinal: FloatArray
        if (mount != null) {
            // gravity_ext = R_phone_to_ext * gravity_phone
            gravityFinal = rotateVec(mount, gravityPhoneCam)
            // R_ext_to_world = R_phone_to_world * R_ext_to_phone
            //                = R_phone_to_world * R_phone_to_ext^T
            rFinal = mulRotation(rPhoneCamToWorld, transpose3(mount))
        } else {
            gravityFinal = gravityPhoneCam
            rFinal = rPhoneCamToWorld
        }

        synchronized(lock) {
            gx = gravityFinal[0]
            gy = gravityFinal[1]
            gz = gravityFinal[2]
            System.arraycopy(rFinal, 0, cameraToWorldRotation, 0, 9)
            timestampNs = event.timestamp
            hasReading = true
        }
    }

    private fun rotateVec(r: FloatArray, v: FloatArray): FloatArray = floatArrayOf(
        r[0] * v[0] + r[1] * v[1] + r[2] * v[2],
        r[3] * v[0] + r[4] * v[1] + r[5] * v[2],
        r[6] * v[0] + r[7] * v[1] + r[8] * v[2],
    )

    private fun mulRotation(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[0] * b[0] + a[1] * b[3] + a[2] * b[6],
        a[0] * b[1] + a[1] * b[4] + a[2] * b[7],
        a[0] * b[2] + a[1] * b[5] + a[2] * b[8],
        a[3] * b[0] + a[4] * b[3] + a[5] * b[6],
        a[3] * b[1] + a[4] * b[4] + a[5] * b[7],
        a[3] * b[2] + a[4] * b[5] + a[5] * b[8],
        a[6] * b[0] + a[7] * b[3] + a[8] * b[6],
        a[6] * b[1] + a[7] * b[4] + a[8] * b[7],
        a[6] * b[2] + a[7] * b[5] + a[8] * b[8],
    )

    private fun transpose3(m: FloatArray): FloatArray = floatArrayOf(
        m[0], m[3], m[6],
        m[1], m[4], m[7],
        m[2], m[5], m[8],
    )

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun remapAxes(rotation: Int): Pair<Int, Int> = when (rotation) {
        Surface.ROTATION_0 -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }
}

data class GravitySnapshot(
    val gravityCam: FloatArray,
    val cameraToWorldRotation: FloatArray,
    val timestampNs: Long,
    val hasReading: Boolean,
)
