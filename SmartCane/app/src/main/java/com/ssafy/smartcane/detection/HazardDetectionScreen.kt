package com.ssafy.smartcane.detection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ssafy.smartcane.ui.theme.AppWhite
import java.util.concurrent.Executors

/**
 * [testMode] = true  : 신고/쿨다운 없음, 임계값 0.1f, 전체 클래스 bbox 표시 (모델 테스트용)
 * [testMode] = false : HazardDetectionAnalyzer 경유, 자동 신고 활성 (기존 동작)
 */
@Composable
fun HazardDetectionScreen(
    onClose: () -> Unit,
    modelFileName: String = "model_yolo26n.tflite",
    testMode: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    val runner = remember(modelFileName) {
        runCatching { TFLiteRunner(context, modelFileName) }
            .onFailure { Log.w("HazardDetect", "TFLiteRunner 초기화 실패: $modelFileName", it) }
            .getOrNull()
    }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    // 종료 순서: analyzer 중단 → 마지막 추론 완료 대기 → runner 닫기
    DisposableEffect(runner) {
        onDispose {
            analyzerExecutor.shutdown()
            runCatching { analyzerExecutor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            runCatching { runner?.close() }
        }
    }

    var lastDetection by remember { mutableStateOf<String?>(null) }
    var detections by remember { mutableStateOf<List<TFLiteRunner.Result>>(emptyList()) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1A1A2E)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp)
        ) {
            // 헤더
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "AI 위험 감지  |  $modelFileName",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppWhite,
                    modifier = Modifier.align(Alignment.Center)
                )
                Button(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("닫기") }
            }

            Spacer(Modifier.height(12.dp))

            // 카메라 미리보기 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp))
            ) {
                if (hasPermission && runner != null) {
                    // 화면 닫힐 때 카메라 명시적 해제
                    DisposableEffect(Unit) {
                        onDispose {
                            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val provider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().apply {
                                    setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                if (testMode) {
                                    // ── 테스트 모드: 신고 없음, 낮은 임계값, 전체 클래스 표시 ──
                                    analysis.setAnalyzer(analyzerExecutor) { proxy ->
                                        runCatching {
                                            val rotated = proxy.toRotatedBitmap()
                                            if (rotated != null) {
                                                val all = runner.detectAll(rotated, 0.1f)
                                                detections = all
                                                lastDetection = all.maxByOrNull { it.confidence }
                                                    ?.let { "${it.label} ${"%.0f".format(it.confidence * 100)}%" }
                                            }
                                        }
                                        proxy.close()
                                    }
                                } else {
                                    // ── 기존 모드: HazardDetectionAnalyzer 경유, 자동 신고 ──
                                    val analyzer = HazardDetectionAnalyzer(ctx, scope) { bitmap ->
                                        val all = runner.detectAll(bitmap)
                                        detections = all
                                        val best = all
                                            .filter { com.ssafy.smartcane.util.HazardType.fromTfliteLabel(it.label) != null }
                                            .maxByOrNull { it.confidence }
                                        if (best != null) {
                                            lastDetection = "${best.label}  ${(best.confidence * 100).toInt()}%"
                                            HazardDetectionAnalyzer.DetectionResult(best.label, best.confidence)
                                        } else {
                                            val anyBest = all.maxByOrNull { it.confidence }
                                            lastDetection = if (anyBest != null)
                                                "${anyBest.label} ${(anyBest.confidence*100).toInt()}% (비위험)"
                                            else "-"
                                            null
                                        }
                                    }
                                    analysis.setAnalyzer(analyzerExecutor) { proxy ->
                                        runCatching {
                                            val rotated = proxy.toRotatedBitmap()
                                            if (rotated != null) analyzer.analyzeBitmap(rotated)
                                        }
                                        proxy.close()
                                    }
                                }

                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        }
                    )
                } else {
                    Text(
                        text = if (!hasPermission) "카메라 권한 필요"
                        else "model.tflite / labels.txt 가 assets/ 에 없음",
                        color = Color(0xFFB0BEC5),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // bbox 오버레이
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    detections.forEach { det ->
                        val left   = det.x1 * size.width
                        val top    = det.y1 * size.height
                        val right  = det.x2 * size.width
                        val bottom = det.y2 * size.height
                        drawRect(
                            color = androidx.compose.ui.graphics.Color(0xFFFF4444),
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                "${det.label} ${"%.0f".format(det.confidence * 100)}%",
                                left + 8f,
                                (top - 10f).coerceAtLeast(20f),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.RED
                                    textSize = 36f
                                    isFakeBoldText = true
                                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "최근 감지: ${lastDetection ?: "-"}",
                color = Color(0xFFB0BEC5),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (testMode)
                    "테스트 모드 — 임계값 0.1, 신고/쿨다운 없음, 전체 클래스 표시"
                else
                    "임계값 ${HazardDetectionAnalyzer.THRESHOLD} 이상 + 같은 타입 ${HazardDetectionAnalyzer.COOLDOWN_MS / 1000}초 쿨다운으로 자동 신고합니다.",
                color = Color(if (testMode) 0xFF29B6F6 else 0xFF78909C),
                fontSize = 11.sp
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("← 뒤로가기 (로그 확인)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppWhite)
            }
        }
    }
}

/** ImageProxy YUV → 회전 적용된 RGB Bitmap */
private fun androidx.camera.core.ImageProxy.toRotatedBitmap(): Bitmap? {
    val src = runCatching { toBitmap() }.getOrNull() ?: return null
    val deg = imageInfo.rotationDegrees
    if (deg == 0) return src
    val matrix = Matrix().apply { postRotate(deg.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}
