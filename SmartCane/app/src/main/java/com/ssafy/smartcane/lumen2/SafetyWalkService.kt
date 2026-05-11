package com.ssafy.smartcane.lumen2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.Image
import android.provider.Settings
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.SemanticLabel
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.ssafy.smartcane.SmartCaneApplication
import com.ssafy.smartcane.detection.HazardDetectionAnalyzer
import com.ssafy.smartcane.detection.TFLiteRunner
import com.ssafy.smartcane.lumen2.ar.ArFrameData
import com.ssafy.smartcane.lumen2.ar.CameraIntrinsicsData
import com.ssafy.smartcane.lumen2.ar.CameraImageConverter
import com.ssafy.smartcane.lumen2.assist.AssistEngine
import com.ssafy.smartcane.lumen2.assist.TrafficSceneEvidence
import com.ssafy.smartcane.lumen2.assist.TrafficSceneStatus
import com.ssafy.smartcane.lumen2.ble.ProximityVibrationController
import com.ssafy.smartcane.util.HazardType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * 화면 없이 ARCore depth + semantic 을 백그라운드에서 실행하는 ForegroundService.
 *
 * 동작 원리:
 *  1. EGL 1×1 off-screen pbuffer context 생성 → OpenGL 텍스처 획득
 *  2. ARCore Session 생성, 텍스처 연결, resume
 *  3. 백그라운드 HandlerThread 에서 session.update() 루프
 *  4. depth + semantic 데이터 → AssistEngine → ProximityVibrationController → BLE 진동
 *
 * foregroundServiceType="camera" 선언으로 Android 9+ 백그라운드 카메라 접근 허가.
 */
class SafetyWalkService : Service() {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var cameraTextureId = 0

    private var session: Session? = null
    private var depthSupported = false
    private var semanticsSupported = false
    private var lastTimestamp = 0L
    private var viewWidth = 1080
    private var viewHeight = 1920
    private var displayRotation = android.view.Surface.ROTATION_0

    private val bgThread = HandlerThread("safety-walk-bg")
    private var handler: Handler? = null
    @Volatile private var running = false

    private lateinit var assistEngine: AssistEngine
    private lateinit var proximityController: ProximityVibrationController
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    @Volatile private var lastCrosswalkSpokenAt = 0L
    private val CROSSWALK_TTS_COOLDOWN = 8000L

    // 실시간 GPS + 서버 위치 전송
    private var locationManager: LocationManager? = null
    private var locationTrackingJob: kotlinx.coroutines.Job? = null   // BleTestScreen 방식과 동일한 루프
    private var deviceId: String = ""   // onCreate() 에서 즉시 초기화 (by lazy 는 IO 스레드 접근 시 context 불안정 우려)
    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            // 위험 신고용 로컬 캐시만 갱신 — 서버 전송은 별도 루프가 담당
            com.ssafy.smartcane.util.LocationHelper.updateLiveLocation(loc.latitude, loc.longitude)
            // 교차로 노드 캐시 업데이트 (150m 이탈 시 재요청)
            hazardScope.launch {
                com.ssafy.smartcane.intersection.IntersectionDetector
                    .updateIfNeeded(loc.latitude, loc.longitude)
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    // TFLite 위험 감지 — ARCore bgThread 와 완전 분리된 전용 스레드
    private var tfliteRunner: TFLiteRunner? = null
    private var hazardAnalyzer: HazardDetectionAnalyzer? = null
    private val hazardScope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO + SupervisorJob())
    private val tfliteExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val tfliteBusy = java.util.concurrent.atomic.AtomicBoolean(false)

    companion object {
        private const val TAG = "SafetyWalkService"
        private const val CHANNEL_ID = "safety_walk_channel"
        private const val NOTIFICATION_ID = 1001
        private const val FRAME_INTERVAL_MS = 100L

        /** SafetyScreen 토글 상태 — 서비스 생명주기와 독립적으로 유지 */
        @Volatile var userEnabled = false
            private set

        fun start(context: Context) {
            userEnabled = true
            val intent = Intent(context, SafetyWalkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            userEnabled = false
            context.stopService(Intent(context, SafetyWalkService::class.java))
        }

        /** Activity 카메라 사용 중 서비스만 종료 (토글 상태 유지) */
        fun stopForActivity(context: Context) {
            context.stopService(Intent(context, SafetyWalkService::class.java))
        }

        /** Activity 종료 후 토글이 켜져 있으면 서비스 재시작 */
        fun restartIfEnabled(context: Context) {
            if (userEnabled) start(context)
        }
    }

    // ── 라이프사이클 ──────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun initDisplaySize() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            viewWidth  = bounds.width()
            viewHeight = bounds.height()
        } else {
            val dm = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            viewWidth  = dm.widthPixels
            viewHeight = dm.heightPixels
        }
        displayRotation = wm.defaultDisplay.rotation
        // 세로 모드 보정 (가로 > 세로이면 swap)
        if (viewWidth > viewHeight) {
            val tmp = viewWidth; viewWidth = viewHeight; viewHeight = tmp
        }
        Log.d(TAG, "화면 크기: ${viewWidth}x${viewHeight} rotation=$displayRotation")
    }

    override fun onCreate() {
        super.onCreate()
        initDisplaySize()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val ble = (application as SmartCaneApplication).bleNusManager
        proximityController = ProximityVibrationController(send = { cmd -> ble.sendCommand(cmd) })
        assistEngine = AssistEngine()

        tts = android.speech.tts.TextToSpeech(this) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale.KOREAN
                tts?.setSpeechRate(1.0f)
                ttsReady = true
            }
        }

        // 실시간 GPS 구독 — getLastKnownLocation 의 오래된 캐시 문제 해결
        startGpsUpdates()

        // TFLite 초기화 (model.tflite + labels.txt)
        tfliteRunner = runCatching { TFLiteRunner(this, "model_yolo26n.tflite") }
            .onFailure {
                Log.w(TAG, "TFLiteRunner 초기화 실패 — 위험 감지 비활성", it)
                com.ssafy.smartcane.util.AppLogger.error(TAG, "TFLiteRunner 초기화 실패: ${it.message}")
            }
            .getOrNull()
        if (tfliteRunner != null) {
            com.ssafy.smartcane.util.AppLogger.log(TAG, "TFLiteRunner 초기화 성공 ✅")
        }
        tfliteRunner?.let { runner ->
            hazardAnalyzer = HazardDetectionAnalyzer(this, hazardScope) { bitmap ->
                com.ssafy.smartcane.util.AppLogger.log(TAG, "detect 람다 호출 bitmap=${bitmap.width}x${bitmap.height}")
                val all = runner.detectAll(bitmap, 0.3f)

                // 횡단보도 감지 시 교차로 여부 판단 + TTS 안내
                val crosswalk = all.firstOrNull { it.label == "crosswalk" && it.confidence >= 0.35f }
                if (crosswalk != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastCrosswalkSpokenAt > CROSSWALK_TTS_COOLDOWN) {
                        val loc = com.ssafy.smartcane.util.LocationHelper.getLastKnownLocation(this@SafetyWalkService)
                        if (loc != null) {
                            val nodeCount = com.ssafy.smartcane.intersection.IntersectionDetector
                                .nearbyNodeCount(loc.first, loc.second)
                            val speech = when {
                                nodeCount >= 3 -> "교차로 횡단보도입니다."
                                nodeCount == 2 -> "삼거리 횡단보도입니다."
                                else           -> "횡단보도가 감지됩니다."
                            }
                            com.ssafy.smartcane.util.AppLogger.log(TAG,
                                "횡단보도 감지 (${(crosswalk.confidence*100).toInt()}%) → $speech (노드 ${nodeCount}개)")
                            if (ttsReady) {
                                tts?.speak(speech, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "crosswalk-${now}")
                                lastCrosswalkSpokenAt = now
                            }
                        }
                    }
                }

                // 위험 클래스 필터 후 최고 confidence 선택
                val result = all
                    .filter { HazardType.fromTfliteLabel(it.label) != null }
                    .maxByOrNull { it.confidence }
                if (result == null) {
                    if (all.isNotEmpty()) {
                        val top = all.maxByOrNull { it.confidence }!!
                        com.ssafy.smartcane.util.AppLogger.log(TAG, "위험 클래스 없음 (최고: ${top.label} ${(top.confidence*100).toInt()}%)")
                    } else {
                        com.ssafy.smartcane.util.AppLogger.log(TAG, "감지 없음 (0.3 미달)")
                    }
                } else {
                    com.ssafy.smartcane.util.AppLogger.log(TAG, "위험 감지: ${result.label} ${(result.confidence*100).toInt()}%")
                }
                result?.let { res ->
                    HazardDetectionAnalyzer.DetectionResult(res.label, res.confidence)
                }
            }
            Log.d(TAG, "TFLite 위험 감지 활성화")
        }

        bgThread.start()
        handler = Handler(bgThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            handler?.post { initAndRun() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { stopGpsUpdates() }   // GPS 정리 + 위치추적 종료 (hazardScope 취소 전)
        hazardScope.cancel()
        runCatching { proximityController.reset() }

        tts?.stop(); tts?.shutdown(); tts = null

        // TFLiteRunner.close() 는 추론 완료 후 executor 스레드에서 호출 (크래시 방지)
        tfliteExecutor.execute { runCatching { tfliteRunner?.close() } }
        tfliteExecutor.shutdown()

        // ARCore + EGL 정리는 bgThread 에서
        handler?.post {
            runCatching { session?.pause(); session?.close() }
            session = null
            cleanupEGL()
        }
        bgThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── GPS 실시간 업데이트 ─────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startGpsUpdates() {
        runCatching {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            locationManager = lm
            val providers = lm.getProviders(true)
            if (providers.isEmpty()) {
                com.ssafy.smartcane.util.AppLogger.error(TAG, "GPS provider 없음")
                return
            }
            // GPS 업데이트 간격: 1초/1m (BleTestScreen 과 동일)
            providers.forEach { provider ->
                lm.requestLocationUpdates(provider, 1_000L, 1f, gpsListener,
                    android.os.Looper.getMainLooper())
            }
            // 즉시 사용할 수 있도록 캐시 위치로 초기화
            val cached = com.ssafy.smartcane.util.LocationHelper.getLastKnownLocation(this)
            if (cached != null) {
                com.ssafy.smartcane.util.LocationHelper.updateLiveLocation(cached.first, cached.second)
                com.ssafy.smartcane.util.AppLogger.log(TAG, "GPS 초기위치: ${cached.first}, ${cached.second}")
            }
            com.ssafy.smartcane.util.AppLogger.log(TAG, "GPS 실시간 구독 시작 (${providers.size}개 provider)")

            // BleTestScreen 과 동일한 방식: 3초마다 마지막 위치를 서버로 전송
            // GPS 콜백 유무와 무관하게 루프가 지속되므로 정지 상태에서도 추적됨
            locationTrackingJob = hazardScope.launch {
                while (isActive) {
                    val loc = com.ssafy.smartcane.util.LocationHelper.getLastKnownLocation(this@SafetyWalkService)
                    if (loc != null) {
                        runCatching {
                            com.ssafy.smartcane.network.LocationApiService.sendLocation(
                                deviceId = deviceId,
                                lat = loc.first,
                                lng = loc.second
                            )
                        }
                    }
                    kotlinx.coroutines.delay(3_000L)
                }
            }
        }.onFailure { e ->
            com.ssafy.smartcane.util.AppLogger.error(TAG, "GPS 구독 실패: ${e.message}")
        }
    }

    private fun stopGpsUpdates() {
        locationTrackingJob?.cancel()
        locationTrackingJob = null
        runCatching { locationManager?.removeUpdates(gpsListener) }
        com.ssafy.smartcane.util.LocationHelper.clearLiveLocation()
        // 서버에 추적 종료 알림 — hazardScope 취소 전에 별도 스코프로 전송
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                com.ssafy.smartcane.network.LocationApiService.sendStop(deviceId)
            }
        }
    }

    // ── 초기화 & 루프 ─────────────────────────────────────────────────────

    private fun initAndRun() {
        if (!setupEGL()) {
            Log.e(TAG, "EGL 초기화 실패")
            stopSelf(); return
        }
        if (!createARCoreSession()) {
            Log.e(TAG, "ARCore 세션 생성 실패")
            stopSelf(); return
        }
        Log.d(TAG, "백그라운드 ARCore 시작 depth=$depthSupported semantics=$semanticsSupported")
        runFrameLoop()
    }

    private fun runFrameLoop() {
        var cameraFailCount = 0
        while (running) {
            try {
                val activeSession = session ?: break
                val frame = activeSession.update()

                if (frame.timestamp == 0L || frame.timestamp == lastTimestamp) {
                    Thread.sleep(16); continue
                }
                lastTimestamp = frame.timestamp
                cameraFailCount = 0

                val arData = readFrameData(frame) ?: continue
                val decision = assistEngine.update(arData, TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList()))
                proximityController.update(decision)

                // TFLite 위험 감지
                // 이미지 취득(bgThread) → 변환+추론(tfliteExecutor 분리) → bgThread 블로킹 없음
                if (hazardAnalyzer != null && tfliteBusy.compareAndSet(false, true)) {
                    val cameraImageResult = runCatching { frame.acquireCameraImage() }
                    val cameraImage = cameraImageResult.getOrNull()
                    if (cameraImage != null) {
                        tfliteExecutor.execute {
                            try {
                                val raw = runCatching {
                                    cameraImage.use { CameraImageConverter.toBitmap(it, null) }
                                }.getOrNull()
                                if (raw == null) {
                                    com.ssafy.smartcane.util.AppLogger.error(TAG, "bitmap 변환 실패")
                                } else {
                                    // 중심 픽셀 RGB 확인: R≈G≈B 이면 그레이스케일 변환 버그 의심
                                    val cx = raw.width / 2; val cy = raw.height / 2
                                    val cp = raw.getPixel(cx, cy)
                                    val pr = (cp shr 16) and 0xFF
                                    val pg = (cp shr 8)  and 0xFF
                                    val pb = cp and 0xFF
                                    val isGray = kotlin.math.abs(pr - pg) < 8 && kotlin.math.abs(pg - pb) < 8
                                    val rotated = rotateBitmapForModel(raw)
                                    com.ssafy.smartcane.util.AppLogger.log(TAG,
                                        "img ${raw.width}x${raw.height}→${rotated.width}x${rotated.height} " +
                                        "rgb($pr,$pg,$pb)${if (isGray) " ⚠️GRAY" else ""}")
                                    hazardAnalyzer?.analyzeBitmap(rotated)
                                }
                            } finally {
                                tfliteBusy.set(false)
                            }
                        }
                    } else {
                        val errName = cameraImageResult.exceptionOrNull()?.javaClass?.simpleName ?: "null"
                        com.ssafy.smartcane.util.AppLogger.log(TAG, "cameraImage 획득 실패: $errName")
                        tfliteBusy.set(false)
                    }
                }

                Thread.sleep(FRAME_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            } catch (e: com.google.ar.core.exceptions.CameraNotAvailableException) {
                // SafetyWalkActivity 가 카메라를 점유 중 → 양보하고 대기
                cameraFailCount++
                val waitMs = (1000L * cameraFailCount).coerceAtMost(5000L)
                Log.d(TAG, "카메라 사용 불가 (Activity 점유 중), ${waitMs}ms 대기")
                Thread.sleep(waitMs)
            } catch (e: Exception) {
                Log.w(TAG, "프레임 처리 오류: ${e.javaClass.simpleName}")
                Thread.sleep(500)
            }
        }
        Log.d(TAG, "프레임 루프 종료")
    }

    // ── EGL 오프스크린 컨텍스트 ──────────────────────────────────────────

    private fun setupEGL(): Boolean {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false
        if (!EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)) return false

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            || numConfigs[0] == 0) return false

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) return false

        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbufferAttribs, 0)
        if (surface == EGL14.EGL_NO_SURFACE) return false

        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return false

        // ARCore 카메라용 OpenGL 외부 텍스처 생성
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        cameraTextureId = textures[0]

        eglDisplay = display
        eglContext = context
        eglSurface = surface
        return true
    }

    private fun cleanupEGL() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    // ── ARCore 세션 ───────────────────────────────────────────────────────

    private fun createARCoreSession(): Boolean {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
            Log.w(TAG, "ARCore 미설치: $availability")
            return false
        }
        return runCatching {
            val s = Session(this)

            // CPU 이미지 접근(acquireCameraImage)을 지원하는 CameraConfig 선택.
            // 기본 config 는 일부 기기에서 CPU 이미지 size = 0 으로 설정돼 있어 항상 실패함.
            runCatching {
                val filter = CameraConfigFilter(s)
                val configs = s.getSupportedCameraConfigs(filter)
                val cpuCapable = configs.filter { it.imageSize.width > 0 && it.imageSize.height > 0 }
                val chosen = cpuCapable.minByOrNull { it.imageSize.width.toLong() * it.imageSize.height } // 가장 작은 해상도 우선
                if (chosen != null) {
                    s.cameraConfig = chosen
                    com.ssafy.smartcane.util.AppLogger.log(TAG, "CameraConfig: ${chosen.imageSize.width}x${chosen.imageSize.height}")
                } else {
                    com.ssafy.smartcane.util.AppLogger.error(TAG, "CPU 이미지 지원 CameraConfig 없음 — acquireCameraImage 불가")
                }
            }.onFailure { e ->
                com.ssafy.smartcane.util.AppLogger.error(TAG, "CameraConfig 설정 실패: ${e.javaClass.simpleName}")
            }

            val config = Config(s).apply {
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    depthMode = Config.DepthMode.AUTOMATIC
                    depthSupported = true
                }
                if (s.isSemanticModeSupported(Config.SemanticMode.ENABLED)) {
                    semanticMode = Config.SemanticMode.ENABLED
                    semanticsSupported = true
                }
            }
            s.configure(config)
            s.setCameraTextureName(cameraTextureId)
            s.setDisplayGeometry(displayRotation, viewWidth, viewHeight)
            s.resume()
            session = s
            true
        }.getOrElse { e ->
            Log.e(TAG, "세션 생성 실패", e)
            false
        }
    }

    // ── 프레임 데이터 읽기 ────────────────────────────────────────────────

    /**
     * ARCore 카메라 이미지(가로 방향)를 모델 입력에 맞게 회전.
     * 폰을 세로로 들면 센서 → 화면 기준 90° CW 보정 필요.
     */
    private fun rotateBitmapForModel(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val degrees = when (displayRotation) {
            android.view.Surface.ROTATION_0   -> 90f
            android.view.Surface.ROTATION_90  -> 0f
            android.view.Surface.ROTATION_180 -> 270f
            android.view.Surface.ROTATION_270 -> 180f
            else -> 90f
        }
        if (degrees == 0f) return src
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        return android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun readFrameData(frame: Frame): ArFrameData? {
        val intrinsics = readIntrinsics(frame)
        // viewWidth / viewHeight 는 initDisplaySize() 가 세팅한 실제 화면 크기 사용
        // → Activity 와 동일한 좌표계로 AssistEngine 계산

        val displayUvCoords = runCatching {
            val input = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            val output = FloatArray(8)
            frame.transformCoordinates2d(
                com.google.ar.core.Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, input,
                com.google.ar.core.Coordinates2d.TEXTURE_NORMALIZED, output
            )
            output
        }.getOrDefault(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))

        val depth = if (depthSupported) readDepth(frame) else null
        val semantics = if (semanticsSupported) readSemantics(frame) else null

        return ArFrameData(
            cameraBitmap = null,
            timestampNanos = frame.timestamp,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            displayUvCoords = displayUvCoords,
            depthWidth = depth?.first ?: 0,
            depthHeight = depth?.second ?: 0,
            depthMillimeters = depth?.third,
            semanticWidth = semantics?.first ?: 0,
            semanticHeight = semantics?.second ?: 0,
            semanticLabels = semantics?.third,
            semanticFractions = readSemanticFractions(frame),
            poseTranslation = frame.camera.pose.translation.copyOf(),
            poseQuaternion = frame.camera.pose.rotationQuaternion.copyOf(),
            intrinsics = intrinsics,
            tracking = frame.camera.trackingState == TrackingState.TRACKING,
            depthSupported = depthSupported,
            semanticsSupported = semanticsSupported
        )
    }

    private fun readIntrinsics(frame: Frame): CameraIntrinsicsData {
        return runCatching {
            val intr = frame.camera.imageIntrinsics
            val focal = FloatArray(2)
            val principal = FloatArray(2)
            val dims = IntArray(2)
            intr.getFocalLength(focal, 0)
            intr.getPrincipalPoint(principal, 0)
            intr.getImageDimensions(dims, 0)
            CameraIntrinsicsData(dims[0], dims[1], focal[0], focal[1], principal[0], principal[1])
        }.getOrDefault(
            CameraIntrinsicsData(viewWidth, viewHeight, viewWidth.toFloat(), viewWidth.toFloat(), viewWidth / 2f, viewHeight / 2f)
        )
    }

    private fun readDepth(frame: Frame): Triple<Int, Int, ShortArray>? {
        val image = runCatching { frame.acquireDepthImage16Bits() }.getOrNull() ?: return null
        return image.useImage {
            val plane = planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride.coerceAtLeast(2)
            val data = ShortArray(width * height)
            var offset = 0
            for (row in 0 until height) {
                val rowStart = row * rowStride
                for (col in 0 until width) {
                    val idx = rowStart + col * pixelStride
                    val low = buffer.get(idx).toInt() and 0xff
                    val high = buffer.get(idx + 1).toInt() and 0xff
                    data[offset++] = ((high shl 8) or low).toShort()
                }
            }
            Triple(width, height, data)
        }
    }

    private fun readSemantics(frame: Frame): Triple<Int, Int, ByteArray>? {
        val image = runCatching { frame.acquireSemanticImage() }.getOrNull() ?: return null
        return image.useImage {
            val plane = planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val data = ByteArray(width * height)
            var offset = 0
            for (row in 0 until height) {
                val rowStart = row * rowStride
                for (col in 0 until width) data[offset++] = buffer.get(rowStart + col)
            }
            Triple(width, height, data)
        }
    }

    private fun readSemanticFractions(frame: Frame): FloatArray? = runCatching {
        FloatArray(12).also {
            it[0] = frame.getSemanticLabelFraction(SemanticLabel.UNLABELED)
            it[1] = frame.getSemanticLabelFraction(SemanticLabel.SKY)
            it[2] = frame.getSemanticLabelFraction(SemanticLabel.BUILDING)
            it[3] = frame.getSemanticLabelFraction(SemanticLabel.TREE)
            it[4] = frame.getSemanticLabelFraction(SemanticLabel.ROAD)
            it[5] = frame.getSemanticLabelFraction(SemanticLabel.SIDEWALK)
            it[6] = frame.getSemanticLabelFraction(SemanticLabel.TERRAIN)
            it[7] = frame.getSemanticLabelFraction(SemanticLabel.STRUCTURE)
            it[8] = frame.getSemanticLabelFraction(SemanticLabel.OBJECT)
            it[9] = frame.getSemanticLabelFraction(SemanticLabel.VEHICLE)
            it[10] = frame.getSemanticLabelFraction(SemanticLabel.PERSON)
            it[11] = frame.getSemanticLabelFraction(SemanticLabel.WATER)
        }
    }.getOrNull()

    private inline fun <T> android.media.Image.useImage(block: android.media.Image.() -> T): T {
        return try { block() } finally { close() }
    }

    // ── 알림 ─────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "안전 보행",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "안전 보행 모드 실행 중"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("안전 보행 모드 실행 중")
        .setContentText("장애물 감지 및 손목 진동 알림이 활성화되었습니다.")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, Class.forName("com.ssafy.smartcane.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
}
