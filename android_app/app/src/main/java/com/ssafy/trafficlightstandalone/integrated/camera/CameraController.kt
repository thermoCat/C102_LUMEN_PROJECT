package com.ssafy.trafficlightstandalone.integrated.camera

import android.content.Context
import android.os.SystemClock
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ssafy.trafficlightstandalone.integrated.inference.LiteRtYoloDetector
import com.ssafy.trafficlightstandalone.integrated.model.InferenceResult
import java.util.concurrent.ExecutorService

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val analyzerExecutor: ExecutorService,
    private val onResult: (InferenceResult) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onCameraReady: (Camera) -> Unit = {},
) {
    private var camera: Camera? = null
    private var currentLinearZoom = 0f

    fun start(detector: LiteRtYoloDetector) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val cameraProvider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                        .build()

                    analysis.setAnalyzer(analyzerExecutor) { image ->
                        try {
                            val startedAt = SystemClock.elapsedRealtime()
                            val result = detector.detect(image)
                            onResult(result.copy(pipelineTimeMs = SystemClock.elapsedRealtime() - startedAt))
                        } catch (throwable: Throwable) {
                            onError(throwable)
                        } finally {
                            image.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    onCameraReady(camera!!)
                }.onFailure(onError)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** 광학 줌: 선형 값 0.0–1.0 */
    fun setLinearZoom(zoom: Float) {
        currentLinearZoom = zoom.coerceIn(0f, 1f)
        camera?.cameraControl?.setLinearZoom(currentLinearZoom)
    }

    fun zoomIn(step: Float = 0.1f) = setLinearZoom(currentLinearZoom + step)
    fun zoomOut(step: Float = 0.1f) = setLinearZoom(currentLinearZoom - step)
    fun getCurrentLinearZoom(): Float = currentLinearZoom

    /** 줌 비율(배율) 직접 설정 */
    fun setZoomRatio(ratio: Float) {
        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        val clamped = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    fun getCameraForPinch(): Camera? = camera
}
