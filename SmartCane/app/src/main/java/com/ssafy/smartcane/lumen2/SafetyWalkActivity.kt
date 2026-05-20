package com.ssafy.smartcane.lumen2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ssafy.smartcane.R
import com.ssafy.smartcane.SmartCaneApplication
import com.ssafy.smartcane.ble.BleNusManager
import com.ssafy.smartcane.crosswalk.CrosswalkPipeline
import com.ssafy.smartcane.crosswalk.PhoneSpeechOutput
import com.ssafy.smartcane.crosswalk.WatchSpeechOutput
import com.ssafy.smartcane.detection.TFLiteRunner
import com.ssafy.smartcane.lumen2.assist.AssistEngine
import com.ssafy.smartcane.lumen2.assist.AssistFeedbackController
import com.ssafy.smartcane.lumen2.assist.AssistOverlayRenderer
import com.ssafy.smartcane.intersection.IntersectionDetector
import com.ssafy.smartcane.lumen2.assist.IntersectionContext
import com.ssafy.smartcane.lumen2.assist.TrafficDetection
import com.ssafy.smartcane.lumen2.assist.TrafficDetectionLabel
import com.ssafy.smartcane.lumen2.assist.TrafficSceneEvidence
import com.ssafy.smartcane.lumen2.assist.TrafficSceneStatus
import com.ssafy.smartcane.navigation.HeadingProvider
import com.ssafy.smartcane.util.LocationHelper
import com.ssafy.smartcane.lumen2.ar.ArCoreFrameSource
import com.ssafy.smartcane.lumen2.ar.ArFrameData
import com.ssafy.smartcane.lumen2.ble.ProximityVibrationController
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SafetyWalkActivity : ComponentActivity() {

    private lateinit var arSurfaceView: GLSurfaceView
    private lateinit var overlayView: ImageView
    private lateinit var assistEngine: AssistEngine
    private lateinit var assistRenderer: AssistOverlayRenderer
    private lateinit var assistFeedback: AssistFeedbackController
    private var tfliteRunner: TFLiteRunner? = null
    private lateinit var trafficExecutor: ExecutorService
    private var frameSource: ArCoreFrameSource? = null

    @Volatile private var latestTrafficEvidence = TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList())
    @Volatile private var latestRawDetections: List<TFLiteRunner.Result> = emptyList()
    @Volatile private var trafficBusy = false
    @Volatile private var topBarBottomPx: Float = 0f
    @Volatile private var buttonRightPx: Float = 0f
    @Volatile private var buttonHeightPx: Float = 0f

    private lateinit var bleNusManager: BleNusManager
    private lateinit var proximityController: ProximityVibrationController
    private lateinit var headingProvider: HeadingProvider
    private lateinit var crosswalkPipeline: CrosswalkPipeline

    private companion object {
        private const val TAG = "SafetyWalkActivity"
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startArCore() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.safety_walk_activity)

        arSurfaceView = findViewById(R.id.arSurfaceView)
        overlayView   = findViewById(R.id.overlayView)
        overlayView.visibility = android.view.View.VISIBLE
        overlayView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        assistEngine   = AssistEngine()
        assistRenderer = AssistOverlayRenderer()
        assistFeedback = AssistFeedbackController(this)

        tfliteRunner = runCatching { TFLiteRunner(this) }
            .onFailure { Log.w(TAG, "TFLiteRunner 비활성 (${TFLiteRunner.DEFAULT_MODEL_FILE_NAME} 없음)", it) }
            .getOrNull()
        trafficExecutor = Executors.newSingleThreadExecutor()

        bleNusManager       = (application as SmartCaneApplication).bleNusManager
        proximityController = ProximityVibrationController(send = { cmd -> bleNusManager.sendCommand(cmd) })

        val phoneSpeech = PhoneSpeechOutput(assistFeedback)
        val speechOutput = WatchSpeechOutput(context = this, fallback = phoneSpeech)
        crosswalkPipeline = CrosswalkPipeline(
            context = this,
            bleNusManager = bleNusManager,
            speechOutput = speechOutput
        )
        headingProvider = HeadingProvider(this) { heading ->
            crosswalkPipeline.currentHeading = heading
        }

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val topBar = findViewById<android.view.View>(R.id.topButtonBar)
        val btnBack = findViewById<android.view.View>(R.id.btnBack)
        topBar.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val btnWinLoc = IntArray(2)
                btnBack.getLocationInWindow(btnWinLoc)
                val glWinLoc = IntArray(2)
                arSurfaceView.getLocationInWindow(glWinLoc)
                topBarBottomPx = maxOf(0f, (btnWinLoc[1] - glWinLoc[1]).toFloat())
                buttonRightPx = maxOf(0f, (btnWinLoc[0] + btnBack.width - glWinLoc[0]).toFloat())
                buttonHeightPx = btnBack.height.toFloat()
                topBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        SafetyWalkService.stopForActivity(this)

        frameSource = ArCoreFrameSource(
            activity    = this,
            surfaceView = arSurfaceView,
            onFrame     = ::handleFrame,
            onStatus    = { Log.d(TAG, "AR Status: $it") }
        ).also { it.setup() }

        arSurfaceView.postDelayed({
            if (!isFinishing) {
                if (hasCameraPermission()) startArCore()
                else permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }, 1500)
    }

    override fun onResume() {
        super.onResume()
        headingProvider.start()
    }

    override fun onPause() {
        headingProvider.stop()
        proximityController.reset()
        frameSource?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        crosswalkPipeline.destroy()
        proximityController.reset()
        frameSource?.close()
        trafficExecutor.shutdownNow()
        tfliteRunner?.close()
        assistFeedback.shutdown()
        SafetyWalkService.restartIfEnabled(this)
        super.onDestroy()
    }

    private fun startArCore() {
        try {
            frameSource?.resume()
        } catch (e: Exception) {
            Log.e(TAG, "ARCore 시작 실패", e)
            finish()
        }
    }

    private fun handleFrame(frame: ArFrameData) {
        val portrait = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        val bitmap = frame.cameraBitmap?.toDisplayBitmap(portrait)

        scheduleTrafficAnalysis(bitmap, frame.viewWidth, frame.viewHeight)

        val decision = assistEngine.update(frame, latestTrafficEvidence)
        assistFeedback.apply(decision)
        proximityController.update(decision)

        val overlay = assistRenderer.render(
            frame.viewWidth, frame.viewHeight, decision,
            topBarBottomPx, buttonRightPx, buttonHeightPx, latestRawDetections
        )
        runOnUiThread { overlayView.setImageBitmap(overlay) }
    }

    private fun scheduleTrafficAnalysis(bitmap: Bitmap?, width: Int, height: Int) {
        bitmap ?: return
        val runner = tfliteRunner ?: return
        if (trafficBusy || trafficExecutor.isShutdown) return
        trafficBusy = true
        trafficExecutor.execute {
            try {
                val raw = runner.detectAll(bitmap)
                latestRawDetections = raw
                val evidence = raw.toTrafficEvidence(width, height)

                val intersectionCtx = if (
                    evidence.status == TrafficSceneStatus.CROSSWALK ||
                    evidence.status == TrafficSceneStatus.GREEN_LIGHT ||
                    evidence.status == TrafficSceneStatus.RED_LIGHT
                ) {
                    when (evidence.status) {
                        TrafficSceneStatus.CROSSWALK   -> crosswalkPipeline.onCrosswalkDetected()
                        TrafficSceneStatus.GREEN_LIGHT -> crosswalkPipeline.onSignalDetected(green = true)
                        TrafficSceneStatus.RED_LIGHT   -> crosswalkPipeline.onSignalDetected(green = false)
                        else -> Unit
                    }
                    val loc = LocationHelper.getLastKnownLocation(this@SafetyWalkActivity)
                    if (loc != null) {
                        when (IntersectionDetector.nearbyNodeCount(loc.first, loc.second)) {
                            0, 1 -> IntersectionContext.NONE
                            2    -> IntersectionContext.T_JUNCTION
                            else -> IntersectionContext.INTERSECTION
                        }
                    } else IntersectionContext.NONE
                } else IntersectionContext.NONE

                latestTrafficEvidence = evidence.copy(intersectionContext = intersectionCtx)
            } catch (_: Throwable) {
                latestTrafficEvidence = TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList())
            } finally {
                trafficBusy = false
            }
        }
    }

    private fun List<TFLiteRunner.Result>.toTrafficEvidence(dstW: Int, dstH: Int): TrafficSceneEvidence {
        val detections = mapNotNull { r ->
            val label = when (r.label) {
                "crosswalk"                -> TrafficDetectionLabel.CROSSWALK
                "green_light"              -> TrafficDetectionLabel.GREEN_LIGHT
                "pedestrian_traffic_light" -> TrafficDetectionLabel.PEDESTRIAN_TRAFFIC_LIGHT
                "red_light"                -> TrafficDetectionLabel.RED_LIGHT
                else -> null
            } ?: return@mapNotNull null
            TrafficDetection(
                label      = label,
                confidence = r.confidence,
                left       = r.x1 * dstW,
                top        = r.y1 * dstH,
                right      = r.x2 * dstW,
                bottom     = r.y2 * dstH
            )
        }
        val hasCrosswalk = detections.any { it.label == TrafficDetectionLabel.CROSSWALK }
        val hasGreen     = detections.any { it.label == TrafficDetectionLabel.GREEN_LIGHT }
        val hasRed       = detections.any { it.label == TrafficDetectionLabel.RED_LIGHT }
        val status = when {
            hasCrosswalk && hasRed   -> TrafficSceneStatus.RED_LIGHT
            hasCrosswalk && hasGreen -> TrafficSceneStatus.GREEN_LIGHT
            hasCrosswalk             -> TrafficSceneStatus.CROSSWALK
            else                     -> TrafficSceneStatus.CLEAR
        }
        return TrafficSceneEvidence(status, detections)
    }

    private fun Bitmap.toDisplayBitmap(portrait: Boolean): Bitmap {
        if (!portrait || height >= width) return this
        val m = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}
