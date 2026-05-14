package com.ssafy.smartcane.lumen2.ar

import android.app.Activity
import android.media.Image
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.SemanticLabel
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * ASSIST 모드 전용 ARCore 프레임 소스.
 * depth + semantics 항상 활성, CPU 이미지 640px 기준.
 */
class ArCoreFrameSource(
    private val activity: Activity,
    private val surfaceView: GLSurfaceView,
    private val onFrame: (ArFrameData) -> Unit,
    private val onStatus: (String) -> Unit
) : GLSurfaceView.Renderer {

    private var session: Session? = null
    private var installRequested = false
    private val backgroundRenderer = CameraBackgroundRenderer()
    private var depthSupported = false
    private var semanticsSupported = false
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var lastTimestamp = 0L
    private var lastDeliveredAtMillis = 0L
    private var lastCpuImageAtMillis = 0L
    private var textureCoordinatesReady = false

    private companion object {
        private const val FRAME_INTERVAL_MS = 66L      // ~15 fps
        private const val CPU_IMAGE_INTERVAL_MS = 700L // bitmap 은 700ms 마다
        private const val CPU_IMAGE_MAX_SIDE = 640
    }

    fun setup() {
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    fun resume() {
        if (session == null && !createSession()) return
        session?.resume()
        surfaceView.onResume()
    }

    fun pause() {
        surfaceView.onPause()
        session?.pause()
    }

    fun close() {
        session?.close()
        session = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        backgroundRenderer.create()
        session?.setCameraTextureName(backgroundRenderer.textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        session?.setDisplayGeometry(
            activity.windowManager.defaultDisplay.rotation,
            viewportWidth, viewportHeight
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        val activeSession = session ?: return
        activeSession.setCameraTextureName(backgroundRenderer.textureId)
        activeSession.setDisplayGeometry(
            activity.windowManager.defaultDisplay.rotation,
            viewportWidth, viewportHeight
        )
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1f) // 완전 빨간색으로 변경 (디버깅용)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val frame = try {
            activeSession.update()
        } catch (t: Throwable) {
            // Log.e("ArCoreFrameSource", "Session update failed", t)
            return
        }

        // 프레임이 들어오는지 로그 (100프레임마다 한 번씩)
        if (lastTimestamp % 100 == 0L) {
            // Log.d("ArCoreFrameSource", "Rendering frame: ${frame.timestamp}")
        }

        if (frame.hasDisplayGeometryChanged() || !textureCoordinatesReady) {
            backgroundRenderer.updateTexCoords { input, output ->
                frame.transformCoordinates2d(
                    com.google.ar.core.Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, input,
                    com.google.ar.core.Coordinates2d.TEXTURE_NORMALIZED, output
                )
            }
            textureCoordinatesReady = true
        }
        backgroundRenderer.draw()

        if (frame.timestamp == 0L || frame.timestamp == lastTimestamp) return
        lastTimestamp = frame.timestamp

        val now = System.currentTimeMillis()
        if (now - lastDeliveredAtMillis < FRAME_INTERVAL_MS) return
        lastDeliveredAtMillis = now

        val needsCpuImage = now - lastCpuImageAtMillis >= CPU_IMAGE_INTERVAL_MS
        if (needsCpuImage) {
            lastCpuImageAtMillis = now
            val cameraImage = try {
                frame.acquireCameraImage()
            } catch (_: NotYetAvailableException) {
                return
            } catch (t: Throwable) {
                onStatus("Camera image failed: ${t.javaClass.simpleName}")
                return
            }
            try {
                onFrame(readFrame(frame, cameraImage))
            } catch (t: Throwable) {
                onStatus("Frame read failed: ${t.javaClass.simpleName}")
            } finally {
                cameraImage.close()
            }
        } else {
            try {
                onFrame(readFrame(frame, null))
            } catch (t: Throwable) {
                onStatus("Frame read failed: ${t.javaClass.simpleName}")
            }
        }
    }

    private fun createSession(): Boolean {
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    onStatus("Install ARCore requested")
                    false
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    val newSession = Session(activity)

                    // CPU 이미지 접근을 위한 CameraConfig 선택 (S10 등 기기 호환성)
                    try {
                        val filter = com.google.ar.core.CameraConfigFilter(newSession)
                        val configs = newSession.getSupportedCameraConfigs(filter)
                        val cpuCapable = configs.filter { it.imageSize.width > 0 && it.imageSize.height > 0 }
                        val chosen = cpuCapable.minByOrNull { it.imageSize.width.toLong() * it.imageSize.height }
                        if (chosen != null) {
                            newSession.cameraConfig = chosen
                            onStatus("CameraConfig chosen: ${chosen.imageSize.width}x${chosen.imageSize.height}")
                        }
                    } catch (e: Exception) {
                        onStatus("CameraConfig selection failed: ${e.message}")
                    }

                    val config = Config(newSession)
                    semanticsSupported = newSession.isSemanticModeSupported(Config.SemanticMode.ENABLED)
                    config.depthMode = Config.DepthMode.DISABLED
                    if (semanticsSupported) config.semanticMode = Config.SemanticMode.ENABLED
                    config.focusMode = Config.FocusMode.AUTO
                    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    newSession.configure(config)
                    if (backgroundRenderer.textureId != 0) {
                        newSession.setCameraTextureName(backgroundRenderer.textureId)
                    }
                    session = newSession
                    onStatus("ARCore ready depth=$depthSupported semantics=$semanticsSupported")
                    true
                }
            }
        } catch (t: Throwable) {
            onStatus("ARCore unavailable: ${t.message}")
            false
        }
    }

    private fun readFrame(frame: Frame, cameraImage: Image?): ArFrameData {
        val bitmap = cameraImage?.let {
            CameraImageConverter.toBitmap(it, CPU_IMAGE_MAX_SIDE)
        }
        val semantics = readSemantics(frame)
        val semanticFractions = readSemanticFractions(frame)
        val pose = frame.camera.pose
        return ArFrameData(
            cameraBitmap = bitmap,
            timestampNanos = frame.timestamp,
            viewWidth = viewportWidth,
            viewHeight = viewportHeight,
            displayUvCoords = backgroundRenderer.currentTexCoords(),
            depthWidth = 0,
            depthHeight = 0,
            depthMillimeters = null,
            semanticWidth = semantics?.first ?: 0,
            semanticHeight = semantics?.second ?: 0,
            semanticLabels = semantics?.third,
            semanticFractions = semanticFractions,
            poseTranslation = pose.translation.copyOf(),
            poseQuaternion = pose.rotationQuaternion.copyOf(),
            intrinsics = readIntrinsics(frame),
            tracking = frame.camera.trackingState == TrackingState.TRACKING,
            depthSupported = false,
            semanticsSupported = semanticsSupported
        )
    }

    private fun readIntrinsics(frame: Frame): CameraIntrinsicsData {
        return try {
            val intrinsics = frame.camera.imageIntrinsics
            val focal = FloatArray(2)
            val principal = FloatArray(2)
            val dims = IntArray(2)
            intrinsics.getFocalLength(focal, 0)
            intrinsics.getPrincipalPoint(principal, 0)
            intrinsics.getImageDimensions(dims, 0)
            CameraIntrinsicsData(dims[0], dims[1], focal[0], focal[1], principal[0], principal[1])
        } catch (_: Throwable) {
            CameraIntrinsicsData(
                viewportWidth, viewportHeight,
                viewportWidth.toFloat(), viewportWidth.toFloat(),
                viewportWidth / 2f, viewportHeight / 2f
            )
        }
    }

    private fun readSemantics(frame: Frame): Triple<Int, Int, ByteArray>? {
        val image = try { frame.acquireSemanticImage() } catch (_: Throwable) { null } ?: return null
        return image.useImage {
            val data = ByteArray(width * height)
            copyPlane(planes[0].buffer, planes[0].rowStride, width, height, data)
            Triple(width, height, data)
        }
    }

    private fun readSemanticFractions(frame: Frame): FloatArray? {
        return try {
            FloatArray(12).also {
                it[0]  = frame.getSemanticLabelFraction(SemanticLabel.UNLABELED)
                it[1]  = frame.getSemanticLabelFraction(SemanticLabel.SKY)
                it[2]  = frame.getSemanticLabelFraction(SemanticLabel.BUILDING)
                it[3]  = frame.getSemanticLabelFraction(SemanticLabel.TREE)
                it[4]  = frame.getSemanticLabelFraction(SemanticLabel.ROAD)
                it[5]  = frame.getSemanticLabelFraction(SemanticLabel.SIDEWALK)
                it[6]  = frame.getSemanticLabelFraction(SemanticLabel.TERRAIN)
                it[7]  = frame.getSemanticLabelFraction(SemanticLabel.STRUCTURE)
                it[8]  = frame.getSemanticLabelFraction(SemanticLabel.OBJECT)
                it[9]  = frame.getSemanticLabelFraction(SemanticLabel.VEHICLE)
                it[10] = frame.getSemanticLabelFraction(SemanticLabel.PERSON)
                it[11] = frame.getSemanticLabelFraction(SemanticLabel.WATER)
            }
        } catch (_: Throwable) { null }
    }

    private inline fun <T> Image.useImage(block: Image.() -> T): T {
        return try { block() } finally { close() }
    }

    private fun copyPlane(buffer: ByteBuffer, rowStride: Int, width: Int, height: Int, out: ByteArray) {
        var offset = 0
        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) out[offset++] = buffer.get(rowStart + col)
        }
    }
}
