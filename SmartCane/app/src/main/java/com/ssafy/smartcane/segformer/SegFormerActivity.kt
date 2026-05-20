package com.ssafy.smartcane.segformer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.ssafy.smartcane.R
import com.ssafy.smartcane.SmartCaneApplication
import com.ssafy.smartcane.crosswalk.CrosswalkPipeline
import com.ssafy.smartcane.crosswalk.PhoneSpeechOutput
import com.ssafy.smartcane.crosswalk.WatchSpeechOutput
import com.ssafy.smartcane.detection.TFLiteRunner
import com.ssafy.smartcane.lumen2.assist.AssistFeedbackController
import com.ssafy.smartcane.segformer.ble.SegFormerProximityController
import com.ssafy.smartcane.segformer.camera.CameraController
import com.ssafy.smartcane.segformer.camera.UvcCameraController
import com.ssafy.smartcane.databinding.ActivitySegformerBinding
import com.ssafy.smartcane.segformer.inference.LiteRtSegFormerSegmenter
import com.ssafy.smartcane.segformer.model.ClassGroundSummary
import com.ssafy.smartcane.segformer.model.GroundProjection
import com.ssafy.smartcane.segformer.model.SegmentationResult
import com.ssafy.smartcane.segformer.pose.OrientationProvider
import com.ssafy.smartcane.segformer.pose.OsmoAction4Intrinsics
import com.ssafy.smartcane.segformer.pose.OsmoMount
import com.ssafy.smartcane.segformer.pose.WorldPoseProvider
import com.ssafy.smartcane.segformer.util.FpsStatsTracker
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class SegFormerActivity : ComponentActivity() {
    private lateinit var binding: ActivitySegformerBinding
    private lateinit var analyzerExecutor: ExecutorService
    private val fpsStatsTracker = FpsStatsTracker(windowSize = 10)

    private var segmenter: LiteRtSegFormerSegmenter? = null
    private var cameraController: CameraController? = null
    private var uvcCameraController: UvcCameraController? = null
    private var orientationProvider: OrientationProvider? = null
    private var worldPoseProvider: WorldPoseProvider? = null
    private var proximityController: SegFormerProximityController? = null

    // 횡단보도 파이프라인
    private lateinit var assistFeedback: AssistFeedbackController
    private lateinit var crosswalkPipeline: CrosswalkPipeline

    // YOLO 병렬 추론용
    private var yoloRunner: TFLiteRunner? = null
    private var yoloExecutor: ExecutorService? = null
    private val yoloBusy = AtomicBoolean(false)
    @Volatile private var latestYoloDetections: List<TFLiteRunner.Result> = emptyList()

    // 횡단보도 파이프라인
    private lateinit var assistFeedback: AssistFeedbackController
    private lateinit var crosswalkPipeline: CrosswalkPipeline

    /** Persistent diagnostic line ??survives inference status overwrites. */
    private var cameraDiagnostic: String = ""

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                initializeDetectorAndCamera()
            } else {
                showPermissionState()
            }
        }

    private val usbAttachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.i(TAG, "USB intent received: $action")
            if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED &&
                cameraController == null && uvcCameraController == null
            ) {
                // App was running with no camera bound; a UVC device just appeared.
                if (hasCameraPermission()) initializeDetectorAndCamera()
            } else if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED &&
                cameraController != null && uvcCameraController == null
            ) {
                // We were on CameraX fallback but Osmo just showed up.
                // Cleanest path is to re-init the whole pipeline.
                Log.i(TAG, "UVC device attached after CameraX bind; re-initialising.")
                initializeDetectorAndCamera()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySegformerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analyzerExecutor = Executors.newSingleThreadExecutor()
        binding.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

        val bleNusManager = (application as SmartCaneApplication).bleNusManager
        proximityController = SegFormerProximityController(
            send = { cmd -> bleNusManager.sendCommand(cmd) }
        )

        // 횡단보도 파이프라인 초기화
        assistFeedback = AssistFeedbackController(this)
        val phoneSpeech = PhoneSpeechOutput(assistFeedback)
        val speechOutput = WatchSpeechOutput(context = this, fallback = phoneSpeech)
        crosswalkPipeline = CrosswalkPipeline(
            context = this,
            bleNusManager = bleNusManager,
            speechOutput = speechOutput
        )

        // YOLO 병렬 파이프라인 초기화 — assets 없으면 비활성으로 동작
        yoloExecutor = Executors.newSingleThreadExecutor()
        yoloRunner = runCatching { TFLiteRunner(applicationContext) }
            .onFailure { Log.w(TAG, "YOLO TFLiteRunner 초기화 실패 — YOLO 비활성", it) }
            .getOrNull()

        if (hasCameraPermission()) {
            initializeDetectorAndCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStart() {
        super.onStart()
        orientationProvider?.start()
        worldPoseProvider?.start()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbAttachReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbAttachReceiver, filter)
        }
    }

    override fun onStop() {
        orientationProvider?.stop()
        worldPoseProvider?.stop()
        proximityController?.reset()
        latestYoloDetections = emptyList()
        runOnUiThread { binding.overlayView.setYoloDetections(emptyList()) }
        runCatching { unregisterReceiver(usbAttachReceiver) }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            Log.i(TAG, "onNewIntent USB_DEVICE_ATTACHED, re-checking camera source")
            if (hasCameraPermission() && segmenter == null) {
                initializeDetectorAndCamera()
            }
        }
    }

    override fun onDestroy() {
        worldPoseProvider?.stop()
        uvcCameraController?.stop()
        segmenter?.close()
        proximityController?.reset()
        proximityController = null
        // YOLO 정리 — 추론 완료 후 close (native 크래시 방지)
        val yoloExec = yoloExecutor
        val runner = yoloRunner
        yoloExecutor = null
        yoloRunner = null
        latestYoloDetections = emptyList()
        if (runner != null && yoloExec != null && !yoloExec.isShutdown) {
            yoloExec.execute { runCatching { runner.close() } }
        } else {
            runCatching { runner?.close() }
        }
        yoloExec?.shutdown()
        analyzerExecutor.shutdown()
        crosswalkPipeline.destroy()
        assistFeedback.shutdown()
        super.onDestroy()
    }

    /**
     * Walks all attached USB devices and reports whether something we can use
     * as a UVC source is present. Match order:
     *   1. Exact Osmo Action 4 (VID 0x2CA3, PID 0x0023) ??what `device_filter_uvc.xml` looks for.
     *   2. Any DJI device (VID 0x2CA3) ??covers the case where Osmo is in a
     *      different USB mode that changes the PID but is still a UVC source.
     *   3. Any device that exposes a UVC Video Streaming interface
     *      (class 14 / subclass 2) ??generic webcam fallback.
     * Also logs the full attached-device list so it can be inspected via Logcat.
     */
    private fun detectUvcDevice(): UvcDetection {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList.values.toList()
        val summary = if (devices.isEmpty()) "no USB devices attached" else
            devices.joinToString("; ") { describeDevice(it) }
        Log.i(TAG, "USB enumeration: $summary")

        devices.firstOrNull { it.vendorId == OSMO_VID && it.productId == OSMO_PID }?.let {
            return UvcDetection(it, "Osmo Action 4 (exact match)", summary)
        }
        devices.firstOrNull { it.vendorId != OSMO_VID && hasUvcInterface(it) }?.let {
            return UvcDetection(it, "Generic UVC device ${describeDevice(it)}", summary)
        }
        devices.firstOrNull { it.vendorId == OSMO_VID }?.let {
            Log.w(TAG, "DJI device found with unexpected PID 0x${it.productId.toString(16)}; " +
                "if this is the Osmo in MTP mode, switch it to Webcam mode on the device screen.")
            val pid = "0x%04X".format(it.productId)
            return UvcDetection(
                null,
                "DJI device PID=$pid is not the expected Webcam PID 0x0023; select Webcam mode on the Osmo",
                summary,
            )
        }
        return UvcDetection(null, null, summary)
    }

    private fun hasUvcInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VIDEO && iface.interfaceSubclass == 2) {
                return true
            }
        }
        return false
    }

    private fun describeDevice(d: UsbDevice): String {
        val vid = "0x%04X".format(d.vendorId)
        val pid = "0x%04X".format(d.productId)
        val name = d.productName ?: d.deviceName
        return "$name [VID=$vid PID=$pid]"
    }

    private data class UvcDetection(
        val device: UsbDevice?,
        val matchReason: String?,
        val enumerationSummary: String,
    )

    private fun detectionDetail(detection: UvcDetection): String {
        val reason = detection.matchReason
        return if (reason == null) {
            detection.enumerationSummary
        } else {
            "${detection.enumerationSummary} | $reason"
        }
    }

    private fun stopActivePipeline() {
        runCatching { cameraController?.stop() }
        runCatching { uvcCameraController?.stop() }
        runCatching { orientationProvider?.stop() }
        runCatching { worldPoseProvider?.stop() }
        runCatching { segmenter?.close() }
        cameraController = null
        uvcCameraController = null
        orientationProvider = null
        worldPoseProvider = null
        segmenter = null
        cameraDiagnostic = ""
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializeDetectorAndCamera() {
        stopActivePipeline()
        showLoadingState(getString(R.string.status_loading_model))
        analyzerExecutor.execute {
            runCatching {
                val builtSegmenter = LiteRtSegFormerSegmenter(applicationContext)
                val detection = detectUvcDevice()
                val osmoAttached = detection.device != null
                Log.i(TAG, "USB scan: osmoAttached=$osmoAttached; reason=${detection.matchReason}; enumeration=${detection.enumerationSummary}")
                if (osmoAttached) {
                    OsmoAction4Intrinsics.ensureLoaded(applicationContext)
                }
                val displayRotation = binding.previewView.display?.rotation ?: 0
                val builtOrientation = OrientationProvider(
                    context = applicationContext,
                    displayRotation = displayRotation,
                    cameraMountRotation = if (osmoAttached) OsmoMount.R_PHONE_TO_OSMO else null,
                )
                val builtWorldPoseProvider = WorldPoseProvider()
                builtSegmenter.orientationProvider = builtOrientation
                builtSegmenter.worldPoseProvider = builtWorldPoseProvider

                val (builtCameraController, builtUvcController) = if (osmoAttached) {
                    // MIUI does not expose UVC through Camera2, so use libausbc directly.
                    null to UvcCameraController(
                        context = applicationContext,
                        previewView = binding.uvcPreview,
                        framePreviewView = binding.uvcFramePreview,
                        analyzerExecutor = analyzerExecutor,
                        onResult = ::handleInferenceResult,
                        onError = ::handleCameraError,
                        onDiagnostic = { diag ->
                            runOnUiThread {
                                cameraDiagnostic = "USB[${detectionDetail(detection)}] | $diag"
                                binding.detailText.text = cameraDiagnostic
                            }
                        },
                        targetVendorId = detection.device?.vendorId,
                        targetProductId = detection.device?.productId,
                        targetDeviceName = detection.device?.productName ?: detection.device?.deviceName,
                        onBitmap = ::scheduleYoloAnalysis,
                    )
                } else {
                    CameraController(
                        context = this,
                        lifecycleOwner = this,
                        previewView = binding.previewView,
                        analyzerExecutor = analyzerExecutor,
                        onResult = ::handleInferenceResult,
                        onError = ::handleCameraError,
                        onCameraBound = { _, diagnostic ->
                            runOnUiThread {
                                cameraDiagnostic = "USB[${detectionDetail(detection)}] | CamX[$diagnostic]"
                                binding.detailText.text = cameraDiagnostic
                            }
                        },
                        onBitmap = ::scheduleYoloAnalysis,
                    ) to null
                }

                runOnUiThread {
                    segmenter = builtSegmenter
                    cameraController = builtCameraController
                    uvcCameraController = builtUvcController
                    orientationProvider = builtOrientation
                    worldPoseProvider = builtWorldPoseProvider
                    builtOrientation.start()
                    builtWorldPoseProvider.start()
                    binding.statusText.text = if (osmoAttached) "Binding Osmo via UVC..." else "Binding built-in camera..."
                    binding.detailText.text = "USB scan: ${detectionDetail(detection)}"
                    binding.loadingIndicator.visibility = View.GONE
                    if (osmoAttached) {
                        binding.previewView.visibility = View.GONE
                        binding.uvcPreview.visibility = View.VISIBLE
                        binding.uvcFramePreview.visibility = View.VISIBLE
                        builtUvcController!!.start(builtSegmenter)
                    } else {
                        binding.previewView.visibility = View.VISIBLE
                        binding.uvcPreview.visibility = View.GONE
                        binding.uvcFramePreview.visibility = View.GONE
                        builtCameraController!!.start(builtSegmenter)
                    }
                }
            }.onFailure(::handleCameraError)
        }
    }

    /**
     * 카메라 컨트롤러가 매 프레임 전달하는 ARGB_8888 Bitmap 을 받아 YOLO 추론을
     * 단일 스레드 executor 로 분기한다. busy 가드로 프레임 적체를 방지하고,
     * 결과는 @Volatile latestYoloDetections 에 게시한 뒤 입력 Bitmap 은 recycle.
     */
    private fun scheduleYoloAnalysis(bitmap: Bitmap) {
        val runner = yoloRunner
        val executor = yoloExecutor
        if (runner == null || executor == null || executor.isShutdown) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        if (!yoloBusy.compareAndSet(false, true)) {
            // 이전 추론이 아직 진행 중 → 이번 프레임은 드롭
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        executor.execute {
            try {
                val results = runner.detectAll(bitmap)
                latestYoloDetections = results
                triggerCrosswalkPipeline(results)
            } catch (t: Throwable) {
                Log.w(TAG, "YOLO 추론 실패", t)
                latestYoloDetections = emptyList()
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                yoloBusy.set(false)
            }
        }
    }

    private fun triggerCrosswalkPipeline(results: List<TFLiteRunner.Result>) {
        val crosswalk = results.filter { it.label == "crosswalk" }.maxByOrNull { it.confidence }
        val green     = results.filter { it.label == "green_light" }.maxByOrNull { it.confidence }
        val red       = results.filter { it.label == "red_light" }.maxByOrNull { it.confidence }

        when {
            green != null  -> crosswalkPipeline.onSignalDetected(green = true)
            red != null    -> crosswalkPipeline.onSignalDetected(green = false)
            crosswalk != null && crosswalk.confidence >= 0.6f -> crosswalkPipeline.onCrosswalkDetected()
        }
    }

    private fun handleInferenceResult(result: SegmentationResult) {
        val yolo = latestYoloDetections
        proximityController?.update(result, yolo)
        runOnUiThread {
            val stats = fpsStatsTracker.record(
                inferenceTimeMs = result.inferenceTimeMs,
                pipelineTimeMs = result.pipelineTimeMs,
                callbackTimestampMs = SystemClock.elapsedRealtime(),
            )
            binding.loadingIndicator.visibility = View.GONE
            binding.overlayView.setResult(
                mask = result.mask,
                maskWidth = result.maskWidth,
                maskHeight = result.maskHeight,
                sourceWidth = result.sourceWidth,
                sourceHeight = result.sourceHeight,
                letterbox = result.letterbox,
                virtualBrailleGuide = result.groundProjection?.virtualBrailleGuide,
            )
            binding.overlayView.setYoloDetections(yolo)
            binding.statusText.text = getString(
                R.string.status_segmentation_perf_template,
                result.classNames.getOrElse(result.dominantClassIndex) { getString(R.string.class_unknown) },
                result.inferenceTimeMs,
                stats.inferenceFps,
                stats.callbackFps ?: stats.pipelineFps,
            )
            val perfLine = getString(
                R.string.detail_segmentation_perf_template,
                summarizeForegroundClasses(result),
                result.pipelineTimeMs,
                stats.avgPipelineMs,
            )
            val navLine = formatNavigationLine(result)
            val pixelTotal = result.classPixelCounts.sum()
            val nonBgPixels = result.classPixelCounts.drop(1).sum()
            val frameLine = "frame: ${result.sourceWidth}x${result.sourceHeight}, non-bg=$nonBgPixels/$pixelTotal"
            val yoloLine = if (yolo.isEmpty()) {
                "YOLO: none"
            } else {
                "YOLO: " + yolo.take(3).joinToString(", ") { d ->
                    "%s %.0f%%".format(Locale.US, d.label, d.confidence * 100f)
                }
            }
            val parts = listOfNotNull(
                cameraDiagnostic.takeIf { it.isNotEmpty() },
                frameLine,
                perfLine,
                navLine,
                yoloLine,
            )
            binding.detailText.text = parts.joinToString("\n")
        }
    }

    private fun formatNavigationLine(result: SegmentationResult): String? {
        val projection = result.groundProjection
        if (projection == null) {
            return when {
                result.pose == null -> getString(R.string.nav_orientation_pending)
                else -> getString(R.string.nav_intrinsics_pending)
            }
        }
        val entries = projection.classSummaries
            .filter { it.classIndex != BACKGROUND_CLASS_INDEX }
            .sortedBy { it.minForwardM }
            .take(MAX_NAVIGATION_ENTRIES)
        val virtualGuide = formatVirtualBrailleGuide(projection)
        if (entries.isEmpty()) {
            return virtualGuide ?: getString(R.string.nav_no_ground_classes)
        }
        val parts = entries.joinToString(" | ") { formatNavigationEntry(it, result) }
        val navParts = if (virtualGuide == null) parts else "$parts | $virtualGuide"
        return getString(R.string.nav_summary_template, navParts, projection.cameraHeightM)
    }

    private fun formatVirtualBrailleGuide(projection: GroundProjection): String? {
        val guide = projection.virtualBrailleGuide ?: return null
        val mode = getString(R.string.nav_virtual_braille_world)
        return getString(
            R.string.nav_virtual_braille_guide_template,
            mode,
            guide.startForwardM,
            guide.endForwardM,
            guide.anchorCount,
        )
    }

    private fun formatNavigationEntry(
        summary: ClassGroundSummary,
        result: SegmentationResult,
    ): String {
        val className = result.classNames.getOrElse(summary.classIndex) {
            getString(R.string.class_unknown)
        }
        val lateralAbs = abs(summary.centroidLateralM)
        val lateral = if (summary.centroidLateralM >= 0f) {
            getString(R.string.nav_lateral_right, lateralAbs)
        } else {
            getString(R.string.nav_lateral_left, lateralAbs)
        }
        return getString(
            R.string.nav_entry_template,
            className,
            summary.minForwardM,
            lateral,
        )
    }

    private fun summarizeForegroundClasses(result: SegmentationResult): String {
        val totalPixels = result.classPixelCounts.sum().coerceAtLeast(1).toFloat()
        val foregroundClasses = result.classPixelCounts.indices
            .filter { it != BACKGROUND_CLASS_INDEX && result.classPixelCounts[it] > 0 }
            .sortedByDescending { result.classPixelCounts[it] }
            .take(MAX_SUMMARY_CLASSES)

        if (foregroundClasses.isEmpty()) {
            return getString(R.string.detail_no_foreground)
        }

        return foregroundClasses.joinToString(", ") { classIndex ->
            val percent = result.classPixelCounts[classIndex] * 100f / totalPixels
            val className = result.classNames.getOrElse(classIndex) { getString(R.string.class_unknown) }
            "%s %.1f%%".format(Locale.US, className, percent)
        }
    }

    private fun handleCameraError(throwable: Throwable) {
        runOnUiThread {
            binding.loadingIndicator.visibility = View.GONE
            binding.statusText.text = getString(R.string.status_error)
            binding.detailText.text = throwable.message ?: throwable.javaClass.simpleName
        }
    }

    private fun showPermissionState() {
        binding.loadingIndicator.visibility = View.GONE
        binding.statusText.text = getString(R.string.status_permission_required)
        binding.detailText.text = getString(R.string.detail_permission_required)
    }

    private fun showLoadingState(message: String) {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.statusText.text = message
        binding.detailText.text = getString(R.string.detail_initializing)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val BACKGROUND_CLASS_INDEX = 0
        private const val MAX_SUMMARY_CLASSES = 3
        private const val MAX_NAVIGATION_ENTRIES = 3
        private const val OSMO_VID = 0x2CA3
        private const val OSMO_PID = 0x0023
    }
}
