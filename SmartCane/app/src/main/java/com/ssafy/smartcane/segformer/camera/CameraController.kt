package com.ssafy.smartcane.segformer.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ssafy.smartcane.segformer.inference.LiteRtSegFormerSegmenter
import com.ssafy.smartcane.segformer.model.SegmentationResult
import com.ssafy.smartcane.segformer.pose.CameraIntrinsicsResolver
import com.ssafy.smartcane.segformer.pose.OsmoAction4Intrinsics
import java.util.concurrent.ExecutorService

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val analyzerExecutor: ExecutorService,
    private val onResult: (SegmentationResult) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onCameraBound: (lensFacing: Int, diagnostic: String) -> Unit = { _, _ -> },
    private val onBitmap: ((Bitmap) -> Unit)? = null,
) {
    /**
     * Binds the segmentation pipeline to a CameraX camera. Tries lens-facings
     * in the supplied order and uses the first one CameraX can satisfy on
     * this device. By default external (USB-OTG) is tried first so that an
     * attached UVC camera (e.g. DJI Osmo Action 4 in webcam mode) takes over
     * from the built-in back camera.
     *
     * Requires Android 12+ for [CameraSelector.LENS_FACING_EXTERNAL] support
     * and a vendor HAL that exposes the UVC device as an external camera. If
     * neither is true the external attempt is skipped silently and the back
     * camera is used.
     */
    private fun facingName(facing: Int): String = when (facing) {
        CameraSelector.LENS_FACING_FRONT -> "FRONT"
        CameraSelector.LENS_FACING_BACK -> "BACK"
        CameraSelector.LENS_FACING_EXTERNAL -> "EXTERNAL"
        else -> "lens=$facing"
    }

    fun start(
        segmenter: LiteRtSegFormerSegmenter,
        preferredLensFacings: List<Int> = listOf(
            CameraSelector.LENS_FACING_EXTERNAL,
            CameraSelector.LENS_FACING_BACK,
        ),
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val cameraProvider = providerFuture.get()
                    val availableFacings = cameraProvider.availableCameraInfos
                        .mapNotNull { info ->
                            runCatching {
                                info.lensFacing
                            }.getOrNull()
                        }
                    val availableLabel = availableFacings.joinToString(",") { facingName(it) }
                    Log.i(TAG, "CameraX available lens facings: $availableFacings ($availableLabel)")

                    val selected = preferredLensFacings.firstOrNull { facing ->
                        availableFacings.contains(facing) &&
                            CameraSelector.Builder().requireLensFacing(facing).build()
                                .filter(cameraProvider.availableCameraInfos)
                                .isNotEmpty()
                    } ?: CameraSelector.LENS_FACING_BACK

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(selected)
                        .build()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setTargetRotation(previewView.display.rotation)
                        .build()

                    analysis.setAnalyzer(analyzerExecutor) { image ->
                        try {
                            // YOLO 병렬 분기: 원본 프레임을 Bitmap으로 복제 후 콜백
                            onBitmap?.let { cb ->
                                runCatching { extractRgbaBitmap(image) }.getOrNull()?.let(cb)
                            }
                            val startedAt = SystemClock.elapsedRealtime()
                            val result = segmenter.segment(image)
                            onResult(
                                result.copy(
                                    pipelineTimeMs = SystemClock.elapsedRealtime() - startedAt,
                                ),
                            )
                        } catch (throwable: Throwable) {
                            onError(throwable)
                        } finally {
                            image.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        analysis,
                    )

                    val intrinsicsInfo = if (selected == CameraSelector.LENS_FACING_EXTERNAL) {
                        // External (UVC) cameras do not report Camera2 lens/focal
                        // metadata, so swap in the precomputed Osmo Action 4
                        // intrinsics instead of the built-in resolver.
                        OsmoAction4Intrinsics.ensureLoaded(context)
                        segmenter.intrinsicsResolver = null
                        segmenter.intrinsicsOverride =
                            OsmoAction4Intrinsics.forSource(1920, 1080)
                        Log.i(TAG, "Bound EXTERNAL camera; Osmo intrinsics override applied.")
                        "intrinsics=Osmo-override"
                    } else {
                        segmenter.intrinsicsOverride = null
                        val resolver = CameraIntrinsicsResolver.fromCameraInfo(camera.cameraInfo)
                        segmenter.intrinsicsResolver = resolver
                        val resolverStatus = if (resolver != null) "intrinsics=Camera2" else "intrinsics=NULL"
                        Log.i(TAG, "Bound ${facingName(selected)} camera; $resolverStatus")
                        resolverStatus
                    }
                    val diagnostic = "available=[$availableLabel] -> ${facingName(selected)} | $intrinsicsInfo"
                    onCameraBound(selected, diagnostic)
                }.onFailure(onError)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun stop() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    providerFuture.get().unbindAll()
                }.onFailure {
                    Log.w(TAG, "Failed to unbind CameraX use cases", it)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * ImageAnalysis(OUTPUT_IMAGE_FORMAT_RGBA_8888)이 전달한 ImageProxy 의
     * 첫 plane(packed RGBA) 을 ARGB_8888 Bitmap 으로 복제한다.
     * row padding 이 있으면 잘라낸다. 호출자는 사용 후 recycle 책임.
     */
    private fun extractRgbaBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + if (pixelStride > 0) rowPadding / pixelStride else 0
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        return if (rowPadding > 0 && paddedWidth > image.width) {
            val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
            if (cropped !== padded && !padded.isRecycled) padded.recycle()
            cropped
        } else {
            padded
        }
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
