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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SafetyWalkActivity : ComponentActivity() {

    private lateinit var arSurfaceView: GLSurfaceView
    private lateinit var overlayView: ImageView
    private lateinit var assistEngine: AssistEngine
    private lateinit var assistRenderer: AssistOverlayRenderer
    private lateinit var assistFeedback: AssistFeedbackController
    private var assistTrafficDetector: AssistTrafficDetector? = null
    private lateinit var assistSignalTimerReader: AssistSignalTimerReader
    private lateinit var trafficExecutor: ExecutorService
    private var frameSource: ArCoreFrameSource? = null

    @Volatile private var latestTrafficEvidence = TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList())
    @Volatile private var trafficBusy = false

    private lateinit var bleNusManager: BleNusManager
    private lateinit var proximityController: ProximityVibrationController

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

        assistEngine   = AssistEngine()
        assistRenderer = AssistOverlayRenderer()
        assistFeedback = AssistFeedbackController(this)

        assistTrafficDetector = runCatching { AssistTrafficDetector(this) }
            .onFailure { Log.w(TAG, "AssistTrafficDetector 비활성 (model_a_traffic.tflite 없음)", it) }
            .getOrNull()
        assistSignalTimerReader = AssistSignalTimerReader()
        trafficExecutor = Executors.newSingleThreadExecutor()

        bleNusManager       = (application as SmartCaneApplication).bleNusManager
        // 테스트 Activity: 쿨다운 무시, 장애물 감지 즉시 진동
        proximityController = ProximityVibrationController(
            send = { cmd -> bleNusManager.sendCommand(cmd) },
            respectCooldown = false
        )

        // ARCore 세션 충돌 방지: 서비스 실행 중이면 종료 (토글 상태는 유지됨)
        SafetyWalkService.stopForActivity(this)

        if (hasCameraPermission()) startArCore()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
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
        trafficExecutor.shutdownNow()
        assistTrafficDetector?.close()
        assistSignalTimerReader.close()
        assistFeedback.shutdown()
        // 카메라 해제 후 — 안전보행 토글이 켜져있으면 서비스 재시작
        SafetyWalkService.restartIfEnabled(this)
        super.onDestroy()
    }

    private fun startArCore() {
        if (frameSource != null) { frameSource?.resume(); return }
        frameSource = ArCoreFrameSource(
            activity    = this,
            surfaceView = arSurfaceView,
            onFrame     = ::handleFrame,
            onStatus    = { Log.d(TAG, it) }
        ).also { it.setup(); it.resume() }
    }

    private fun handleFrame(frame: ArFrameData) {
        val portrait = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        val bitmap = frame.cameraBitmap?.toDisplayBitmap(portrait)

        scheduleTrafficAnalysis(bitmap, frame.viewWidth, frame.viewHeight)

        val decision = assistEngine.update(frame, latestTrafficEvidence)
        assistFeedback.apply(decision)
        proximityController.update(decision)

        val overlay = assistRenderer.render(frame.viewWidth, frame.viewHeight, decision)
        runOnUiThread { overlayView.setImageBitmap(overlay) }
    }

    private fun scheduleTrafficAnalysis(bitmap: Bitmap?, width: Int, height: Int) {
        bitmap ?: return
        val detector = assistTrafficDetector ?: return
        if (trafficBusy || trafficExecutor.isShutdown) return
        trafficBusy = true
        trafficExecutor.execute {
            try {
                val detected   = detector.analyze(bitmap)
                val withTimers = assistSignalTimerReader.attachTimers(bitmap, detected)
                latestTrafficEvidence = withTimers.scaled(bitmap, width, height)
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
                right = d.right * sx, bottom = d.bottom * sy,
                ocrLeft = d.ocrLeft?.times(sx), ocrTop = d.ocrTop?.times(sy),
                ocrRight = d.ocrRight?.times(sx), ocrBottom = d.ocrBottom?.times(sy)
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
