package com.ssafy.smartcane.lumen2

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ssafy.smartcane.R
import com.ssafy.smartcane.SmartCaneApplication
import com.ssafy.smartcane.ble.BleNusManager
import com.ssafy.smartcane.lumen2.assist.AssistEngine
import com.ssafy.smartcane.lumen2.assist.AssistFeedbackController
import com.ssafy.smartcane.lumen2.assist.AssistOverlayRenderer
import com.ssafy.smartcane.lumen2.assist.AssistSignalTimerReader
import com.ssafy.smartcane.lumen2.assist.AssistTrafficDetector
import com.ssafy.smartcane.lumen2.assist.TrafficSceneEvidence
import com.ssafy.smartcane.lumen2.assist.TrafficSceneStatus
import com.ssafy.smartcane.lumen2.ar.ArCoreFrameSource
import com.ssafy.smartcane.lumen2.ar.ArFrameData
import com.ssafy.smartcane.lumen2.ble.ProximityVibrationController
import com.ssafy.smartcane.lumen2.ml.MlKitAnalyzers
import com.ssafy.smartcane.lumen2.ui.OverlayRenderer
import com.ssafy.smartcane.lumen2.ui.VisualMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SafetyWalkActivity : ComponentActivity() {
    private lateinit var arSurfaceView: GLSurfaceView
    private lateinit var overlayView: ImageView
    private lateinit var renderer: OverlayRenderer
    private lateinit var mlKit: MlKitAnalyzers
    private lateinit var assistEngine: AssistEngine
    private lateinit var assistRenderer: AssistOverlayRenderer
    private lateinit var assistFeedback: AssistFeedbackController
    private var assistTrafficDetector: AssistTrafficDetector? = null
    private lateinit var assistSignalTimerReader: AssistSignalTimerReader
    private lateinit var trafficExecutor: ExecutorService
    private var frameSource: ArCoreFrameSource? = null
    private var mode = VisualMode.ASSIST
    private var lastAssistFrameMillis = 0L
    @Volatile private var latestTrafficEvidence = TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList())
    @Volatile private var trafficBusy = false

    private lateinit var bleNusManager: BleNusManager
    private lateinit var proximityController: ProximityVibrationController

    private companion object {
        private const val TAG = "SafetyWalkActivity"
        private const val ASSIST_FRAME_INTERVAL_MS = 33L
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startArCore() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.safety_walk_activity)

        arSurfaceView = findViewById(R.id.arSurfaceView)
        overlayView = findViewById(R.id.overlayView)
        renderer = OverlayRenderer()
        mlKit = MlKitAnalyzers()
        assistEngine = AssistEngine()
        assistRenderer = AssistOverlayRenderer()
        assistFeedback = AssistFeedbackController(this)
        assistTrafficDetector = runCatching { AssistTrafficDetector(this) }
            .onFailure { Log.w(TAG, "AssistTrafficDetector 비활성 (model_a_traffic.tflite 없음)", it) }
            .getOrNull()
        assistSignalTimerReader = AssistSignalTimerReader()
        trafficExecutor = Executors.newSingleThreadExecutor()

        bleNusManager = (application as SmartCaneApplication).bleNusManager
        proximityController = ProximityVibrationController { cmd -> bleNusManager.sendCommand(cmd) }

        bindButtons()

        if (hasCameraPermission()) startArCore() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        frameSource?.resume()
    }

    override fun onPause() {
        proximityController.reset()
        frameSource?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        proximityController.reset()
        frameSource?.close()
        mlKit.close()
        trafficExecutor.shutdownNow()
        assistTrafficDetector?.close()
        assistSignalTimerReader.close()
        assistFeedback.shutdown()
        super.onDestroy()
    }

    private fun startArCore() {
        if (frameSource != null) {
            frameSource?.resume()
            return
        }
        frameSource = ArCoreFrameSource(
            activity = this,
            surfaceView = arSurfaceView,
            modeProvider = { mode },
            onFrame = ::handleFrame,
            onStatus = { Log.d(TAG, it) }
        ).also {
            it.setup()
            it.resume()
        }
    }

    private fun handleFrame(frame: ArFrameData) {
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val displayBitmap = frame.cameraBitmap?.toDisplayBitmap(portrait)
        if (mode == VisualMode.TEXT_RECOGNITION && displayBitmap != null) mlKit.processText(displayBitmap)
        if (mode == VisualMode.OBJECT_DETECTION && displayBitmap != null) mlKit.processObjects(displayBitmap)
        val overlayWidth = frame.viewWidth
        val overlayHeight = frame.viewHeight
        if (mode == VisualMode.ASSIST) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAssistFrameMillis < ASSIST_FRAME_INTERVAL_MS) return
            lastAssistFrameMillis = now
        }

        val overlay = when (mode) {
            VisualMode.ASSIST -> {
                val assistBitmap = displayBitmap ?: frame.cameraBitmap
                scheduleTrafficAnalysis(assistBitmap, overlayWidth, overlayHeight)
                val decision = assistEngine.update(frame, latestTrafficEvidence)
                assistFeedback.apply(decision)
                proximityController.update(decision)
                assistRenderer.render(overlayWidth, overlayHeight, decision)
            }
            VisualMode.SEMANTICS -> renderer.semantics(frame, overlayWidth, overlayHeight, portrait)
            VisualMode.DEPTH -> renderer.depth(frame, overlayWidth, overlayHeight, portrait)
            VisualMode.POSE_INTRINSICS -> renderer.poseIntrinsics(frame, overlayWidth, overlayHeight)
            VisualMode.TEXT_RECOGNITION -> if (displayBitmap != null) {
                renderer.text(
                    frame.copy(cameraBitmap = displayBitmap),
                    overlayWidth,
                    overlayHeight,
                    mlKit.latestText,
                    mlKit.latestTextInputWidth,
                    mlKit.latestTextInputHeight,
                    false
                )
            } else {
                renderer.poseIntrinsics(frame, overlayWidth, overlayHeight)
            }
            VisualMode.OBJECT_DETECTION -> if (displayBitmap != null) {
                renderer.objects(
                    frame.copy(cameraBitmap = displayBitmap),
                    overlayWidth,
                    overlayHeight,
                    mlKit.latestObjects,
                    mlKit.latestObjectInputWidth,
                    mlKit.latestObjectInputHeight,
                    mlKit.latestObjectStatus,
                    false
                )
            } else {
                renderer.poseIntrinsics(frame, overlayWidth, overlayHeight)
            }
        }

        runOnUiThread {
            overlayView.setImageBitmap(overlay)
        }
    }

    private fun scheduleTrafficAnalysis(bitmap: Bitmap?, overlayWidth: Int, overlayHeight: Int) {
        bitmap ?: return
        val detector = assistTrafficDetector ?: return
        if (trafficBusy || trafficExecutor.isShutdown) return
        trafficBusy = true
        trafficExecutor.execute {
            try {
                val detected = detector.analyze(bitmap)
                val withTimers = assistSignalTimerReader.attachTimers(bitmap, detected)
                latestTrafficEvidence = withTimers.toOverlayCoordinates(bitmap, overlayWidth, overlayHeight)
            } catch (_: Throwable) {
                latestTrafficEvidence = TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList())
            } finally {
                trafficBusy = false
            }
        }
    }

    private fun TrafficSceneEvidence.toOverlayCoordinates(
        sourceBitmap: Bitmap,
        overlayWidth: Int,
        overlayHeight: Int
    ): TrafficSceneEvidence {
        val scaleX = overlayWidth.toFloat() / sourceBitmap.width.toFloat().coerceAtLeast(1f)
        val scaleY = overlayHeight.toFloat() / sourceBitmap.height.toFloat().coerceAtLeast(1f)
        return copy(
            detections = detections.map { detection ->
                detection.copy(
                    left = detection.left * scaleX,
                    top = detection.top * scaleY,
                    right = detection.right * scaleX,
                    bottom = detection.bottom * scaleY,
                    ocrLeft = detection.ocrLeft?.times(scaleX),
                    ocrTop = detection.ocrTop?.times(scaleY),
                    ocrRight = detection.ocrRight?.times(scaleX),
                    ocrBottom = detection.ocrBottom?.times(scaleY)
                )
            }
        )
    }

    private fun bindButtons() {
        findViewById<Button>(R.id.assistButton).setOnClickListener { setMode(VisualMode.ASSIST) }
        findViewById<Button>(R.id.semanticsButton).setOnClickListener { setMode(VisualMode.SEMANTICS) }
        findViewById<Button>(R.id.depthButton).setOnClickListener { setMode(VisualMode.DEPTH) }
        findViewById<Button>(R.id.poseButton).setOnClickListener { setMode(VisualMode.POSE_INTRINSICS) }
        findViewById<Button>(R.id.textButton).setOnClickListener { setMode(VisualMode.TEXT_RECOGNITION) }
        findViewById<Button>(R.id.objectButton).setOnClickListener { setMode(VisualMode.OBJECT_DETECTION) }
        setMode(VisualMode.ASSIST)
    }

    private fun setMode(next: VisualMode) {
        if (mode == VisualMode.ASSIST && next != VisualMode.ASSIST) {
            // ASSIST 이외 모드는 진동 판정을 멈춘다 → 손목 끄기
            proximityController.reset()
        }
        mode = next
        val buttons = listOf(
            VisualMode.ASSIST to findViewById<Button>(R.id.assistButton),
            VisualMode.SEMANTICS to findViewById<Button>(R.id.semanticsButton),
            VisualMode.DEPTH to findViewById<Button>(R.id.depthButton),
            VisualMode.POSE_INTRINSICS to findViewById<Button>(R.id.poseButton),
            VisualMode.TEXT_RECOGNITION to findViewById<Button>(R.id.textButton),
            VisualMode.OBJECT_DETECTION to findViewById<Button>(R.id.objectButton)
        )
        buttons.forEach { (buttonMode, button) ->
            button.alpha = if (buttonMode == next) 1f else 0.55f
        }
    }

    private fun Bitmap.toDisplayBitmap(portrait: Boolean): Bitmap {
        if (!portrait || height >= width) return this
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}
