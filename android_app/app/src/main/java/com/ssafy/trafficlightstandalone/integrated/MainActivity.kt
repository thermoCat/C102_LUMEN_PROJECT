package com.ssafy.trafficlightstandalone.integrated

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.ssafy.trafficlightstandalone.integrated.ble.BleSignalTransmitter
import com.ssafy.trafficlightstandalone.integrated.camera.CameraController
import com.ssafy.trafficlightstandalone.integrated.databinding.ActivityMainBinding
import com.ssafy.trafficlightstandalone.integrated.inference.LiteRtYoloDetector
import com.ssafy.trafficlightstandalone.integrated.model.InferenceResult
import com.ssafy.trafficlightstandalone.integrated.model.ModelConfig
import com.ssafy.trafficlightstandalone.integrated.model.TrafficLightRoiSource
import com.ssafy.trafficlightstandalone.integrated.model.TrafficLightState
import com.ssafy.trafficlightstandalone.integrated.util.FpsStatsTracker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var analyzerExecutor: ExecutorService
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private val fpsStatsTracker = FpsStatsTracker(windowSize = 10)

    private var detector: LiteRtYoloDetector? = null
    private var cameraController: CameraController? = null
    private var bleTransmitter: BleSignalTransmitter? = null
    private var currentModelConfig = ModelConfig.MODEL_0000_YOLOV8N_260408

    // Runtime-adjustable settings
    private var digitalZoom = 1.0f
    private var twoPasEnabled = true
    private var topCropRatio = 0.6f
    private var bleEnabled = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasRequiredPermissions()) initializeDetectorAndCamera()
            else showPermissionState()
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bleTransmitter?.isBluetoothEnabled() == true) bleTransmitter?.start()
            else binding.bleStatusText.text = getString(R.string.ble_status_enable_bluetooth)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analyzerExecutor = Executors.newSingleThreadExecutor()
        binding.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        binding.bleStatusText.text = getString(R.string.ble_status_idle)

        setupZoomGesture()
        setupZoomButtons()
        setupDigitalZoomButtons()
        setupSettingsButton()

        if (hasRequiredPermissions()) initializeDetectorAndCamera()
        else permissionLauncher.launch(requiredPermissions())
    }

    override fun onDestroy() {
        bleTransmitter?.close()
        detector?.close()
        analyzerExecutor.shutdownNow()
        super.onDestroy()
    }

    // ── Permissions ─────────────────────────────────────────────────────────

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return list.toTypedArray()
    }

    private fun hasRequiredPermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    // ── Init ─────────────────────────────────────────────────────────────────

    private fun initializeDetectorAndCamera() {
        showLoadingState(getString(R.string.status_loading_model))
        analyzerExecutor.execute {
            runCatching {
                val builtDetector = LiteRtYoloDetector(applicationContext, currentModelConfig).apply {
                    twoPasEnabled = this@MainActivity.twoPasEnabled
                    topCropRatio = this@MainActivity.topCropRatio
                    digitalZoom = this@MainActivity.digitalZoom
                }
                val builtController = CameraController(
                    context = this,
                    lifecycleOwner = this,
                    previewView = binding.previewView,
                    analyzerExecutor = analyzerExecutor,
                    onResult = ::handleInferenceResult,
                    onError = ::handleCameraError,
                    onCameraReady = { cam ->
                        cam.cameraInfo.zoomState.observe(this) { zoomState ->
                            runOnUiThread {
                                binding.tvOpticalZoomLevel.text =
                                    String.format("%.1fx", zoomState.zoomRatio)
                            }
                        }
                    },
                )
                runOnUiThread {
                    detector = builtDetector
                    cameraController = builtController
                    if (bleTransmitter == null) {
                        bleTransmitter = BleSignalTransmitter(
                            context = applicationContext,
                            onStatusChanged = { binding.bleStatusText.text = it },
                            onError = { binding.bleStatusText.text = it.message ?: "BLE 오류" },
                        )
                    }
                    binding.statusText.text = getString(R.string.status_camera_ready)
                    binding.detailText.text = getString(R.string.detail_waiting_for_frame)
                    binding.loadingIndicator.visibility = View.GONE
                    updateModelLabel()
                    if (bleEnabled) ensureBluetoothReady()
                    builtController.start(builtDetector)
                }
            }.onFailure(::handleCameraError)
        }
    }

    private fun restartWithNewModel() {
        detector?.close()
        detector = null
        cameraController = null
        initializeDetectorAndCamera()
    }

    // ── Inference result ─────────────────────────────────────────────────────

    private fun handleInferenceResult(result: InferenceResult) {
        bleTransmitter?.takeIf { bleEnabled }?.onInferenceResult(result)
        runOnUiThread {
            try {
                val stats = fpsStatsTracker.record(result, SystemClock.elapsedRealtime())
                binding.loadingIndicator.visibility = View.GONE
                binding.overlayView.setResult(
                    detections = result.detections,
                    sourceWidth = result.sourceWidth,
                    sourceHeight = result.sourceHeight,
                    trafficLightRoi = result.trafficLightRoi,
                    trafficLightRoiSource = result.trafficLightRoiSource,
                )
                val trafficLightSummary = buildTrafficLightSummary(result)
                binding.statusText.text = getString(
                    R.string.status_detection_perf_template,
                    result.detections.size,
                    result.inferenceTimeMs,
                    stats.inferenceFps,
                    stats.callbackFps ?: stats.pipelineFps,
                )
                binding.detailText.text = if (result.detections.isEmpty()) {
                    getString(
                        R.string.detail_no_detection_perf_template,
                        (result.peakScore * 100f).toInt(),
                        result.pipelineTimeMs,
                    ) + trafficLightSummary
                } else {
                    val summary = result.detections
                        .groupBy { it.className }.entries
                        .sortedByDescending { it.value.size }
                        .joinToString(", ") { "${formatClassName(it.key)} x${it.value.size}" }
                    getString(
                        R.string.detail_detection_perf_template,
                        summary,
                        result.pipelineTimeMs,
                        stats.avgPipelineMs,
                    ) + trafficLightSummary
                }
                binding.tvInferenceMs.text = getString(R.string.stat_inference_ms, result.inferenceTimeMs)
                binding.tvInferenceFps.text = getString(R.string.stat_inference_fps, stats.inferenceFps)
                binding.tvOutputFps.text = getString(R.string.stat_output_fps, stats.callbackFps ?: stats.pipelineFps)
            } catch (t: Throwable) {
                handleCameraError(t)
            }
        }
    }

    private fun handleCameraError(t: Throwable) {
        runOnUiThread {
            binding.loadingIndicator.visibility = View.GONE
            binding.statusText.text = getString(R.string.status_error)
            binding.detailText.text = t.message ?: t.javaClass.simpleName
        }
    }

    private fun showPermissionState() {
        binding.loadingIndicator.visibility = View.GONE
        binding.statusText.text = getString(R.string.status_permission_required)
        binding.detailText.text = getString(R.string.detail_permission_required)
        binding.bleStatusText.text = getString(R.string.ble_status_permission_required)
    }

    private fun showLoadingState(message: String) {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.statusText.text = message
        binding.detailText.text = getString(R.string.detail_initializing)
    }

    // ── Optical zoom ─────────────────────────────────────────────────────────

    private fun setupZoomGesture() {
        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val cam = cameraController?.getCameraForPinch() ?: return true
                    val zoomState = cam.cameraInfo.zoomState.value ?: return true
                    val newRatio = (zoomState.zoomRatio * detector.scaleFactor)
                        .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                    cam.cameraControl.setZoomRatio(newRatio)
                    return true
                }
            },
        )
        binding.overlayView.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) view.performClick()
            true
        }
    }

    private fun setupZoomButtons() {
        binding.btnZoomIn.setOnClickListener {
            cameraController?.zoomIn()
        }
        binding.btnZoomOut.setOnClickListener {
            cameraController?.zoomOut()
        }
    }

    // ── Digital zoom ─────────────────────────────────────────────────────────

    private fun setupDigitalZoomButtons() {
        updateDigitalZoomLabel()
        binding.btnDigitalZoomIn.setOnClickListener {
            digitalZoom = (digitalZoom + 0.5f).coerceAtMost(8f)
            applyDigitalZoom()
        }
        binding.btnDigitalZoomOut.setOnClickListener {
            digitalZoom = (digitalZoom - 0.5f).coerceAtLeast(1.0f)
            applyDigitalZoom()
        }
    }

    private fun applyDigitalZoom() {
        detector?.digitalZoom = digitalZoom
        updateDigitalZoomLabel()
    }

    private fun updateDigitalZoomLabel() {
        binding.tvDigitalZoomLevel.text = getString(R.string.digital_zoom_label, digitalZoom)
    }

    private fun updateModelLabel() {
        binding.tvModelName.text = currentModelConfig.displayName
    }

    // ── Settings dialog ───────────────────────────────────────────────────────

    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupModel)
        val switchTwoPass = dialogView.findViewById<Switch>(R.id.switchTwoPass)
        val seekTopCrop = dialogView.findViewById<SeekBar>(R.id.seekBarTopCrop)
        val switchBle = dialogView.findViewById<Switch>(R.id.switchBle)
        val modelOptionMap = mutableMapOf<Int, ModelConfig>()

        ModelConfig.values().forEach { config ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = config.displayName
                setTextColor(0xFF212121.toInt())
                textSize = 14f
            }
            radioGroup.addView(button)
            modelOptionMap[button.id] = config
            if (config == currentModelConfig) {
                button.isChecked = true
            }
        }
        switchTwoPass.isChecked = twoPasEnabled
        seekTopCrop.progress = ((topCropRatio - 0.3f) / 0.6f * 100).toInt().coerceIn(0, 100)
        switchBle.isChecked = bleEnabled

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(dialogView)
            .setPositiveButton(R.string.settings_apply) { _, _ ->
                val newModel = modelOptionMap[radioGroup.checkedRadioButtonId] ?: currentModelConfig
                val modelChanged = newModel != currentModelConfig
                currentModelConfig = newModel

                twoPasEnabled = switchTwoPass.isChecked
                topCropRatio = 0.3f + (seekTopCrop.progress / 100f) * 0.6f
                bleEnabled = switchBle.isChecked

                detector?.twoPasEnabled = twoPasEnabled
                detector?.topCropRatio = topCropRatio

                if (bleEnabled) ensureBluetoothReady() else updateBleStatus()

                if (modelChanged) restartWithNewModel()
                else updateModelLabel()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    // ── BLE ──────────────────────────────────────────────────────────────────

    private fun ensureBluetoothReady() {
        val ble = bleTransmitter ?: return
        if (!ble.isBluetoothSupported()) {
            binding.bleStatusText.text = getString(R.string.ble_status_unsupported)
            return
        }
        if (!ble.isBluetoothEnabled()) {
            binding.bleStatusText.text = getString(R.string.ble_status_enable_bluetooth)
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        ble.start()
    }

    private fun updateBleStatus() {
        if (!bleEnabled) binding.bleStatusText.text = getString(R.string.ble_status_idle)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildTrafficLightSummary(result: InferenceResult): String {
        if (result.trafficLightRoiSource == TrafficLightRoiSource.NONE &&
            result.trafficLightState == TrafficLightState.NONE
        ) {
            return ""
        }
        return " | ROI ${formatTrafficLightRoiSource(result.trafficLightRoiSource)} / Signal ${formatTrafficLightState(result.trafficLightState)}"
    }

    private fun formatTrafficLightRoiSource(source: TrafficLightRoiSource): String = when (source) {
        TrafficLightRoiSource.PTL -> "PTL"
        TrafficLightRoiSource.EXPANDED -> "EXP"
        TrafficLightRoiSource.TRACKED -> "TRACK"
        TrafficLightRoiSource.NONE -> "NONE"
    }

    private fun formatTrafficLightState(state: TrafficLightState): String = when (state) {
        TrafficLightState.GREEN -> "GREEN"
        TrafficLightState.RED -> "RED"
        TrafficLightState.NONE -> "NONE"
    }

    private fun formatClassName(className: String): String =
        className.replace('_', ' ').split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
}
