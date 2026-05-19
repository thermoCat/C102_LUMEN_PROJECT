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
import com.ssafy.smartcane.lumen2.assist.AssistTrafficDetector
import com.ssafy.smartcane.intersection.IntersectionDetector
import com.ssafy.smartcane.lumen2.assist.IntersectionContext
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
    private var assistTrafficDetector: AssistTrafficDetector? = null
    private lateinit var trafficExecutor: ExecutorService
    private var frameSource: ArCoreFrameSource? = null

    @Volatile private var latestTrafficEvidence = TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList())
    @Volatile private var trafficBusy = false
    @Volatile private var topBarBottomPx: Float = 0f

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

        assistTrafficDetector = runCatching { AssistTrafficDetector(this) }
            .onFailure { Log.w(TAG, "AssistTrafficDetector 비활성 (${TFLiteRunner.DEFAULT_MODEL_FILE_NAME} 없음)", it) }
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
                // 버튼 top Y를 GLSurfaceView(= 비트맵) 좌표계로 변환
                topBarBottomPx = maxOf(0f, (btnWinLoc[1] - glWinLoc[1]).toFloat())
                topBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        // ARCore 세션 충돌 방지: 서비스 실행 중이면 종료 (토글 상태는 유지됨)
        SafetyWalkService.stopForActivity(this)

        // 1. Renderer 먼저 등록 (NPE 방지: 지연 없이 즉시 실행해야 함)
        frameSource = ArCoreFrameSource(
            activity    = this,
            surfaceView = arSurfaceView,
            onFrame     = ::handleFrame,
            onStatus    = { Log.d(TAG, "AR Status: $it") }
        ).also { it.setup() }

        // 2. 실제 ARCore 세션 시작만 지연 처리 (서비스와의 충돌 방지)
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
        // ARCore 세션은 onCreate에서 지연 시작하므로 여기서 즉시 resume하지 않음
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
        assistTrafficDetector?.close()
        assistFeedback.shutdown()
        // 카메라 해제 후 — 안전보행 토글이 켜져있으면 서비스 재시작
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

        val overlay = assistRenderer.render(frame.viewWidth, frame.viewHeight, decision, topBarBottomPx)
        runOnUiThread { overlayView.setImageBitmap(overlay) }
    }

    private fun scheduleTrafficAnalysis(bitmap: Bitmap?, width: Int, height: Int) {
        bitmap ?: return
        val detector = assistTrafficDetector ?: return
        if (trafficBusy || trafficExecutor.isShutdown) return
        trafficBusy = true
        trafficExecutor.execute {
            try {
                val detected = detector.analyze(bitmap)
                val scaled = detected.scaled(bitmap, width, height)

                // 횡단보도/신호 감지 처리
                val intersectionCtx = if (
                    scaled.status == TrafficSceneStatus.CROSSWALK ||
                    scaled.status == TrafficSceneStatus.GREEN_LIGHT ||
                    scaled.status == TrafficSceneStatus.RED_LIGHT
                ) {
                    when (scaled.status) {
                        // 1단계: 횡단보도만 잡힘 → 위치 파악 + 방면 안내
                        TrafficSceneStatus.CROSSWALK -> crosswalkPipeline.onCrosswalkDetected()
                        // 2단계: 사용자가 방향 전환 후 신호등 바라볼 때 신호 상태 안내
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

                latestTrafficEvidence = scaled.copy(intersectionContext = intersectionCtx)
            } catch (_: Throwable) {
                latestTrafficEvidence = TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList())
            } finally {
                trafficBusy = false
            }
        }
    }

    private fun TrafficSceneEvidence.scaled(src: Bitmap, dstW: Int, dstH: Int): TrafficSceneEvidence {
        val sx = dstW.toFloat() / src.width.toFloat().coerceAtLeast(1f)
        val sy = dstH.toFloat() / src.height.toFloat().coerceAtLeast(1f)
        return copy(detections = detections.map { d ->
            d.copy(
                left = d.left * sx, top = d.top * sy,
                right = d.right * sx, bottom = d.bottom * sy
            )
        })
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
