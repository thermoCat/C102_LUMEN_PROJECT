package com.ssafy.smartcane.segformer.camera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import android.widget.ImageView
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.serenegiant.usb.USBMonitor
import com.ssafy.smartcane.segformer.inference.LiteRtSegFormerSegmenter
import com.ssafy.smartcane.segformer.model.SegmentationResult
import com.ssafy.smartcane.segformer.pose.OsmoAction4Intrinsics
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives an external UVC camera (DJI Osmo Action 4 in webcam mode) over USB-OTG
 * using the libausbc UVC library (com.jiangdg.ausbc.*).
 *
 * Used when MIUI's Camera2 HAL does not expose UVC devices as
 * LENS_FACING_EXTERNAL (the Redmi case). For phones that DO surface UVC via
 * Camera2, [CameraController] takes the cleaner CameraX path instead.
 *
 * Two parallel data streams:
 *   1. Display: libausbc renders the UVC frame directly onto the
 *      AspectRatioTextureView via openCamera(view, request).
 *   2. Inference: addPreviewDataCallBack delivers raw NV21 bytes; we convert
 *      to RGBA8888 in-place and hand the buffer to [LiteRtSegFormerSegmenter].
 */
class UvcCameraController(
    private val context: Context,
    private val previewView: AspectRatioTextureView,
    private val framePreviewView: ImageView? = null,
    private val analyzerExecutor: ExecutorService,
    private val onResult: (SegmentationResult) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onDiagnostic: (String) -> Unit = {},
    private val targetVendorId: Int? = null,
    private val targetProductId: Int? = null,
    private val targetDeviceName: String? = null,
    private val onBitmap: ((Bitmap) -> Unit)? = null,
) {
    private var client: MultiCameraClient? = null
    private var camera: MultiCameraClient.Camera? = null
    private var segmenter: LiteRtSegFormerSegmenter? = null
    private val frameBusy = AtomicBoolean(false)
    private val firstFrameReported = AtomicBoolean(false)
    private val frameProblemReported = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rgbaBuffer: ByteBuffer? = null
    private var rgbaBufferCapacity = 0
    private var lastFrameSize = "?"
    private var displayedPreviewBitmap: Bitmap? = null

    fun start(segmenter: LiteRtSegFormerSegmenter) {
        if (!started.compareAndSet(false, true)) return
        this.segmenter = segmenter
        // Request USB permission in our own code first so the UI can report a
        // clear permission sequence. libuvc 3.2.4+ also creates its internal
        // USBMonitor PendingIntent with FLAG_IMMUTABLE on Android S+.
        try {
            ensureUsbPermissionThenRegister()
        } catch (t: Throwable) {
            started.set(false)
            onError(t)
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { context.unregisterReceiver(permissionReceiver) }
        permissionReceiverRegistered = false
        runCatching { camera?.closeCamera() }
        runCatching { client?.unRegister() }
        runCatching { client?.destroy() }
        camera = null
        client = null
        mainHandler.post {
            framePreviewView?.setImageBitmap(null)
            displayedPreviewBitmap?.recycle()
            displayedPreviewBitmap = null
        }
    }

    private var permissionReceiverRegistered = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            Log.i(TAG, "USB permission broadcast: granted=$granted")
            onDiagnostic("USB permission: ${if (granted) "granted" else "denied"}")
            if (granted) {
                registerLibausbc()
            } else {
                onError(SecurityException("USB permission denied for Osmo Action 4"))
            }
        }
    }

    private fun ensureUsbPermissionThenRegister() {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val osmo = usbManager.deviceList.values.firstOrNull { isTargetDevice(it) }
        if (osmo == null) {
            // No device attached yet ??let libausbc's monitor pick it up on attach.
            // The pre-grant only matters if a device is already present at start.
            val target = targetDeviceName ?: "selected UVC device"
            throw IllegalStateException("$target is no longer attached")
        }
        if (usbManager.hasPermission(osmo)) {
            Log.i(TAG, "USB permission already granted for ${osmo.deviceName}")
            registerLibausbc()
            return
        }
        Log.i(TAG, "Requesting USB permission for ${osmo.deviceName}")
        onDiagnostic("Requesting USB permission")
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(permissionReceiver, filter)
        }
        permissionReceiverRegistered = true
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        usbManager.requestPermission(osmo, pendingIntent)
    }

    private fun registerLibausbc() {
        if (client != null) return
        val newClient = MultiCameraClient(context, deviceConnectCallback)
        newClient.register()
        client = newClient
        onDiagnostic("UVC client registered, waiting for device")
        Log.i(TAG, "MultiCameraClient registered")
    }

    private val deviceConnectCallback = object : IDeviceConnectCallBack {
        override fun onAttachDev(device: UsbDevice?) {
            device ?: return
            if (!isTargetDevice(device)) return
            Log.i(TAG, "onAttachDev VID=0x${"%04X".format(device.vendorId)} PID=0x${"%04X".format(device.productId)}")
            onDiagnostic("UVC attach VID=0x${"%04X".format(device.vendorId)} PID=0x${"%04X".format(device.productId)}")
            val client = client ?: return
            onDiagnostic(
                if (client.hasPermission(device) == true) {
                    "UVC permission ready, connecting"
                } else {
                    "UVC requesting permission via monitor"
                },
            )
                // permission already granted ??onConnectDev will follow
            client.requestPermission(device)
        }

        override fun onDetachDec(device: UsbDevice?) {
            Log.i(TAG, "onDetachDec")
            onDiagnostic("UVC detached")
            runCatching { camera?.closeCamera() }
            camera = null
        }

        override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            if (device == null || ctrlBlock == null) return
            if (!isTargetDevice(device)) return
            Log.i(TAG, "onConnectDev ??opening camera")
            onDiagnostic("UVC connected, preparing preview")
            try {
                val cam = MultiCameraClient.Camera(context, device)
                cam.setUsbControlBlock(ctrlBlock)
                cam.setCameraStateCallBack(cameraStateCallback)
                cam.addPreviewDataCallBack(previewDataCallback)
                val request = CameraRequest.Builder()
                    .setPreviewWidth(REQUESTED_WIDTH)
                    .setPreviewHeight(REQUESTED_HEIGHT)
                    .create()
                camera = cam
                firstFrameReported.set(false)
                frameProblemReported.set(false)
                openCameraWhenSurfaceReady(cam, request)
            } catch (t: Throwable) {
                onError(t)
            }
        }

        override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            Log.i(TAG, "onDisConnectDec")
            runCatching { camera?.closeCamera() }
            camera = null
        }

        override fun onCancelDev(device: UsbDevice?) {
            Log.w(TAG, "onCancelDev ??user denied USB permission")
            onDiagnostic("UVC permission denied")
        }
    }

    private fun isTargetDevice(device: UsbDevice): Boolean {
        val vendorId = targetVendorId
        val productId = targetProductId
        return if (vendorId != null && productId != null) {
            device.vendorId == vendorId && device.productId == productId
        } else {
            device.vendorId == OSMO_VID && device.productId == OSMO_PID
        }
    }

    private val cameraStateCallback = object : ICameraStateCallBack {
        override fun onCameraState(
            self: MultiCameraClient.Camera,
            code: ICameraStateCallBack.State,
            msg: String?,
        ) {
            Log.i(TAG, "Camera state: $code ${msg ?: ""}")
            val tail = msg?.let { " ($it)" } ?: ""
            onDiagnostic("UVC state: $code$tail")
            if (code == ICameraStateCallBack.State.OPENED) {
                mainHandler.postDelayed({
                    if (started.get() && camera === self && !firstFrameReported.get()) {
                        onDiagnostic(
                            "UVC opened, waiting for first frame ${REQUESTED_WIDTH}x${REQUESTED_HEIGHT}",
                        )
                    }
                }, FIRST_FRAME_TIMEOUT_MS)
            }
        }
    }

    private val previewDataCallback = object : IPreviewDataCallBack {
        override fun onPreviewData(data: ByteArray?, format: IPreviewDataCallBack.DataFormat) {
            if (data == null) return
            val isFirstFrame = firstFrameReported.compareAndSet(false, true)
            if (format != IPreviewDataCallBack.DataFormat.NV21) {
                if (isFirstFrame || frameProblemReported.compareAndSet(false, true)) {
                    onDiagnostic("UVC unsupported frame format=$format")
                }
                return
            }
            val frameSize = inferNv21FrameSize(data.size)
            if (frameSize == null) {
                if (isFirstFrame || frameProblemReported.compareAndSet(false, true)) {
                    onDiagnostic("UVC unknown NV21 frame bytes=${data.size}")
                }
                return
            }
            if (isFirstFrame) {
                val (rawWidth, rawHeight) = frameSize
                onDiagnostic(
                    "UVC first frame NV21 raw=${rawWidth}x${rawHeight} rot=${UVC_ROTATION_DEGREES} -> ${ANALYSIS_WIDTH}x${ANALYSIS_HEIGHT}",
                )
            }
            if (!frameBusy.compareAndSet(false, true)) return
            analyzerExecutor.execute {
                try {
                    val seg = segmenter ?: return@execute
                    val (rawWidth, rawHeight) = frameSize
                    val transform = outputTransform(
                        rawWidth = rawWidth,
                        rawHeight = rawHeight,
                        rotationDegrees = UVC_ROTATION_DEGREES,
                        outputWidth = ANALYSIS_WIDTH,
                        outputHeight = ANALYSIS_HEIGHT,
                    )
                    val needed = ANALYSIS_WIDTH * ANALYSIS_HEIGHT * 4
                    val buffer = obtainRgbaBuffer(needed)
                    nv21TransformToRgba(data, rawWidth, rawHeight, transform, buffer)
                    publishPreviewFrame(buffer, ANALYSIS_WIDTH, ANALYSIS_HEIGHT)
                    lastFrameSize = "${ANALYSIS_WIDTH}x${ANALYSIS_HEIGHT}"

                    OsmoAction4Intrinsics.ensureLoaded(context)
                    seg.intrinsicsResolver = null
                    seg.intrinsicsOverride = OsmoAction4Intrinsics.forTransformedSource(
                        rawWidth = rawWidth,
                        rawHeight = rawHeight,
                        rotationDegrees = transform.rotationDegrees,
                        cropLeft = transform.cropLeft,
                        cropTop = transform.cropTop,
                        cropWidth = transform.cropWidth,
                        cropHeight = transform.cropHeight,
                        outputWidth = ANALYSIS_WIDTH,
                        outputHeight = ANALYSIS_HEIGHT,
                    )

                    // YOLO 병렬 분기: SegFormer 추론 전, 같은 버퍼로부터 Bitmap 복제
                    onBitmap?.let { cb ->
                        runCatching { rgbaToBitmap(buffer, ANALYSIS_WIDTH, ANALYSIS_HEIGHT) }
                            .getOrNull()
                            ?.let(cb)
                    }

                    val started = SystemClock.elapsedRealtime()
                    val result = seg.segment(
                        rgba = buffer,
                        imageWidth = ANALYSIS_WIDTH,
                        imageHeight = ANALYSIS_HEIGHT,
                        rowStride = ANALYSIS_WIDTH * 4,
                        pixelStride = 4,
                        rotationDegrees = 0,
                    )
                    onResult(
                        result.copy(
                            pipelineTimeMs = SystemClock.elapsedRealtime() - started,
                        ),
                    )
                } catch (t: Throwable) {
                    onError(t)
                } finally {
                    frameBusy.set(false)
                }
            }
        }
    }

    private fun openCameraWhenSurfaceReady(
        cam: MultiCameraClient.Camera,
        request: CameraRequest,
    ) {
        mainHandler.post {
            previewView.setAspectRatio(REQUESTED_WIDTH, REQUESTED_HEIGHT)
            fun openNow() {
                if (!started.get() || camera !== cam) return
                onDiagnostic("UVC opening ${REQUESTED_WIDTH}x${REQUESTED_HEIGHT}")
                cam.openCamera(previewView, request)
            }

            if (previewView.isAvailable && previewView.surfaceTexture != null) {
                openNow()
                return@post
            }

            onDiagnostic("UVC waiting for preview surface")
            previewView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    previewView.surfaceTextureListener = null
                    openNow()
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) = Unit

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
    }

    private fun inferNv21FrameSize(byteCount: Int): Pair<Int, Int>? {
        return KNOWN_NV21_SIZES.firstOrNull { (width, height) ->
            byteCount == width * height * 3 / 2
        }
    }

    private fun outputTransform(
        rawWidth: Int,
        rawHeight: Int,
        rotationDegrees: Int,
        outputWidth: Int,
        outputHeight: Int,
    ): FrameTransform {
        val rotation = ((rotationDegrees % 360) + 360) % 360
        val rotatedWidth = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
        val rotatedHeight = if (rotation == 90 || rotation == 270) rawWidth else rawHeight
        val rawAspect = rotatedWidth.toFloat() / rotatedHeight.toFloat()
        val outputAspect = outputWidth.toFloat() / outputHeight.toFloat()
        val cropWidth: Float
        val cropHeight: Float
        if (rawAspect > outputAspect) {
            cropHeight = rotatedHeight.toFloat()
            cropWidth = cropHeight * outputAspect
        } else {
            cropWidth = rotatedWidth.toFloat()
            cropHeight = cropWidth / outputAspect
        }
        return FrameTransform(
            rotationDegrees = rotation,
            rotatedWidth = rotatedWidth,
            rotatedHeight = rotatedHeight,
            cropLeft = (rotatedWidth - cropWidth) / 2f,
            cropTop = (rotatedHeight - cropHeight) / 2f,
            cropWidth = cropWidth,
            cropHeight = cropHeight,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
        )
    }

    private fun obtainRgbaBuffer(needed: Int): ByteBuffer {
        var b = rgbaBuffer
        if (b == null || rgbaBufferCapacity < needed) {
            b = ByteBuffer.allocateDirect(needed)
            rgbaBuffer = b
            rgbaBufferCapacity = needed
        }
        b.clear()
        return b
    }

    private fun nv21TransformToRgba(
        nv21: ByteArray,
        rawWidth: Int,
        rawHeight: Int,
        transform: FrameTransform,
        out: ByteBuffer,
    ) {
        val frameSize = rawWidth * rawHeight
        out.clear()
        for (j in 0 until transform.outputHeight) {
            val rotatedY = (
                transform.cropTop +
                    (j + 0.5f) * transform.cropHeight / transform.outputHeight
                ).toInt().coerceIn(0, transform.rotatedHeight - 1)
            for (i in 0 until transform.outputWidth) {
                val rotatedX = (
                    transform.cropLeft +
                        (i + 0.5f) * transform.cropWidth / transform.outputWidth
                    ).toInt().coerceIn(0, transform.rotatedWidth - 1)
                val (sourceX, sourceY) = mapRotatedToRaw(
                    rotatedX = rotatedX,
                    rotatedY = rotatedY,
                    rawWidth = rawWidth,
                    rawHeight = rawHeight,
                    rotationDegrees = transform.rotationDegrees,
                )
                val uvRow = frameSize + (sourceY shr 1) * rawWidth
                val y = (nv21[sourceY * rawWidth + sourceX].toInt() and 0xFF) - 16
                val uvOffset = uvRow + (sourceX and 1.inv())
                val u = (nv21[uvOffset].toInt() and 0xFF) - 128
                val v = (nv21[uvOffset + 1].toInt() and 0xFF) - 128
                val yScaled = 1192 * y.coerceAtLeast(0)
                var r = (yScaled + 1634 * v) shr 10
                var g = (yScaled - 833 * v - 400 * u) shr 10
                var b = (yScaled + 2066 * u) shr 10
                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255
                out.put(r.toByte())
                out.put(g.toByte())
                out.put(b.toByte())
                out.put(0xFF.toByte())
            }
        }
        out.flip()
    }

    /**
     * SegFormer 입력으로 쓰는 RGBA8888 ByteBuffer 를 YOLO(TFLiteRunner) 입력용
     * ARGB_8888 Bitmap 으로 복제한다. 호출자가 사용 후 recycle 해야 한다.
     * buffer 의 position/limit 은 호출 전후로 변경되지 않도록 readonly slice 사용.
     */
    private fun rgbaToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        val pixels = buffer.asReadOnlyBuffer()
        pixels.rewind()
        val argb = IntArray(width * height)
        for (index in argb.indices) {
            val r = pixels.get().toInt() and 0xFF
            val g = pixels.get().toInt() and 0xFF
            val b = pixels.get().toInt() and 0xFF
            pixels.get() // alpha 스킵
            argb[index] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun publishPreviewFrame(buffer: ByteBuffer, width: Int, height: Int) {
        val target = framePreviewView ?: return
        val pixels = buffer.asReadOnlyBuffer()
        pixels.rewind()
        val argb = IntArray(width * height)
        for (index in argb.indices) {
            val r = pixels.get().toInt() and 0xFF
            val g = pixels.get().toInt() and 0xFF
            val b = pixels.get().toInt() and 0xFF
            pixels.get()
            argb[index] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
        mainHandler.post {
            if (!started.get()) {
                bitmap.recycle()
                return@post
            }
            val old = displayedPreviewBitmap
            displayedPreviewBitmap = bitmap
            target.setImageBitmap(bitmap)
            old?.recycle()
        }
    }

    private fun mapRotatedToRaw(
        rotatedX: Int,
        rotatedY: Int,
        rawWidth: Int,
        rawHeight: Int,
        rotationDegrees: Int,
    ): Pair<Int, Int> {
        return when (rotationDegrees) {
            90 -> rotatedY.coerceIn(0, rawWidth - 1) to
                (rawHeight - 1 - rotatedX).coerceIn(0, rawHeight - 1)
            180 -> (rawWidth - 1 - rotatedX).coerceIn(0, rawWidth - 1) to
                (rawHeight - 1 - rotatedY).coerceIn(0, rawHeight - 1)
            270 -> (rawWidth - 1 - rotatedY).coerceIn(0, rawWidth - 1) to
                rotatedX.coerceIn(0, rawHeight - 1)
            else -> rotatedX.coerceIn(0, rawWidth - 1) to
                rotatedY.coerceIn(0, rawHeight - 1)
        }
    }

    private data class FrameTransform(
        val rotationDegrees: Int,
        val rotatedWidth: Int,
        val rotatedHeight: Int,
        val cropLeft: Float,
        val cropTop: Float,
        val cropWidth: Float,
        val cropHeight: Float,
        val outputWidth: Int,
        val outputHeight: Int,
    )

    companion object {
        private const val TAG = "UvcCameraController"
        private const val REQUESTED_WIDTH = 640
        private const val REQUESTED_HEIGHT = 480
        private const val ANALYSIS_WIDTH = 480
        private const val ANALYSIS_HEIGHT = 640
        private const val UVC_ROTATION_DEGREES = 270
        private const val FIRST_FRAME_TIMEOUT_MS = 2_000L
        private const val OSMO_VID = 0x2CA3
        private const val OSMO_PID = 0x0023
        private const val ACTION_USB_PERMISSION =
            "com.ssafy.smartcane.segformer.slam.USB_PERMISSION"
        private val KNOWN_NV21_SIZES = arrayOf(
            640 to 480,
            1280 to 720,
            1920 to 1080,
            320 to 240,
            800 to 600,
            1024 to 768,
        )

    }
}
