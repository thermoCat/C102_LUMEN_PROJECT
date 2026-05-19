package com.ssafy.smartcane.segformer.pose

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.tan

/**
 * Pre-computed intrinsics for the DJI Osmo Action 4 in webcam mode (UVC).
 *
 * Loaded from `assets/osmo_action4_intrinsics.json` if present (produced by
 * `tools/calibrate_osmo.py`). Falls back to a synthetic guess derived from the
 * published FOV spec so the app still runs without a calibration ??but distance
 * readings will be off by ~5-15% until you run a real calibration.
 *
 * Important: the calibration is recorded at one resolution (e.g. 1920x1080).
 * When the camera reports a different preview resolution, we linearly scale
 * fx/fy/cx/cy so the pinhole model still applies ??this is exact for square
 * pixels (universally true on consumer cameras).
 */
object OsmoAction4Intrinsics {
    private const val TAG = "OsmoAction4Intrinsics"
    private const val ASSET_NAME = "osmo_action4_intrinsics.json"

    // Fallback (uncalibrated) parameters. Derived from the Osmo Action 4
    // dewarp-mode horizontal FOV of ~125 deg at 16:9 1080p; replace once
    // you have a real calibration.
    private const val FALLBACK_RESOLUTION_W = 1920
    private const val FALLBACK_RESOLUTION_H = 1080
    private const val FALLBACK_HORIZONTAL_FOV_DEG = 125.0

    @Volatile private var calibration: Calibration? = null

    fun ensureLoaded(context: Context) {
        if (calibration != null) return
        calibration = try {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                parseJson(JSONObject(reader.readText()))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "No $ASSET_NAME asset found; using synthetic intrinsics. " +
                "Run tools/calibrate_osmo.py for accurate ground-plane distances.", t)
            fallbackCalibration()
        }
    }

    fun forSource(sourceWidth: Int, sourceHeight: Int): CameraIntrinsics {
        val cal = calibration ?: fallbackCalibration().also { calibration = it }
        val scaleX = sourceWidth.toFloat() / cal.refWidth
        val scaleY = sourceHeight.toFloat() / cal.refHeight
        return CameraIntrinsics(
            fx = cal.fx * scaleX,
            fy = cal.fy * scaleY,
            cx = cal.cx * scaleX,
            cy = cal.cy * scaleY,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )
    }

    fun forCroppedSource(
        rawWidth: Int,
        rawHeight: Int,
        cropLeft: Float,
        cropTop: Float,
        cropWidth: Float,
        cropHeight: Float,
        outputWidth: Int,
        outputHeight: Int,
    ): CameraIntrinsics {
        val raw = forSource(rawWidth, rawHeight)
        val scaleX = outputWidth / cropWidth
        val scaleY = outputHeight / cropHeight
        return CameraIntrinsics(
            fx = raw.fx * scaleX,
            fy = raw.fy * scaleY,
            cx = (raw.cx - cropLeft) * scaleX,
            cy = (raw.cy - cropTop) * scaleY,
            sourceWidth = outputWidth,
            sourceHeight = outputHeight,
        )
    }

    fun forTransformedSource(
        rawWidth: Int,
        rawHeight: Int,
        rotationDegrees: Int,
        cropLeft: Float,
        cropTop: Float,
        cropWidth: Float,
        cropHeight: Float,
        outputWidth: Int,
        outputHeight: Int,
    ): CameraIntrinsics {
        val raw = forSource(rawWidth, rawHeight)
        val rotation = ((rotationDegrees % 360) + 360) % 360
        val (rotFx, rotFy, rotCx, rotCy) = when (rotation) {
            90 -> Quad(raw.fy, raw.fx, rawHeight - raw.cy, raw.cx)
            180 -> Quad(raw.fx, raw.fy, rawWidth - raw.cx, rawHeight - raw.cy)
            270 -> Quad(raw.fy, raw.fx, raw.cy, rawWidth - raw.cx)
            else -> Quad(raw.fx, raw.fy, raw.cx, raw.cy)
        }
        val scaleX = outputWidth / cropWidth
        val scaleY = outputHeight / cropHeight
        return CameraIntrinsics(
            fx = rotFx * scaleX,
            fy = rotFy * scaleY,
            cx = (rotCx - cropLeft) * scaleX,
            cy = (rotCy - cropTop) * scaleY,
            sourceWidth = outputWidth,
            sourceHeight = outputHeight,
        )
    }

    /**
     * Distortion coefficients (k1, k2, p1, p2, k3) from the calibration JSON,
     * or zeros if no calibration is loaded. Consumed by [OsmoUndistortMap] to
     * pre-build a remap LUT applied to each UVC frame.
     */
    fun distortionCoefficients(): FloatArray {
        val cal = calibration ?: fallbackCalibration().also { calibration = it }
        return cal.dist
    }

    fun hasMeasuredCalibration(): Boolean = calibration?.measured == true

    private fun parseJson(json: JSONObject): Calibration {
        val res = json.getJSONArray("resolution")
        val k = json.getJSONArray("K")
        val dist = json.getJSONArray("dist")
        val row0 = k.getJSONArray(0)
        val row1 = k.getJSONArray(1)
        return Calibration(
            refWidth = res.getInt(0),
            refHeight = res.getInt(1),
            fx = row0.getDouble(0).toFloat(),
            fy = row1.getDouble(1).toFloat(),
            cx = row0.getDouble(2).toFloat(),
            cy = row1.getDouble(2).toFloat(),
            dist = FloatArray(dist.length()) { dist.getDouble(it).toFloat() },
            measured = true,
        )
    }

    private fun fallbackCalibration(): Calibration {
        val w = FALLBACK_RESOLUTION_W
        val h = FALLBACK_RESOLUTION_H
        val hfovRad = FALLBACK_HORIZONTAL_FOV_DEG * PI / 180.0
        val fx = (w / 2.0 / tan(hfovRad / 2.0)).toFloat()
        return Calibration(
            refWidth = w,
            refHeight = h,
            fx = fx,
            fy = fx,
            cx = w / 2f,
            cy = h / 2f,
            dist = FloatArray(5),
            measured = false,
        )
    }

    private data class Calibration(
        val refWidth: Int,
        val refHeight: Int,
        val fx: Float,
        val fy: Float,
        val cx: Float,
        val cy: Float,
        val dist: FloatArray,
        val measured: Boolean,
    )

    private data class Quad(
        val fx: Float,
        val fy: Float,
        val cx: Float,
        val cy: Float,
    )
}
