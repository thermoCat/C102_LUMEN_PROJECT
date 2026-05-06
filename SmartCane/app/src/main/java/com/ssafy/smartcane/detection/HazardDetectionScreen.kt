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

@Composable
fun HazardDetectionScreen(onClose: () -> Unit) {
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

    val runner = remember {
        runCatching { TFLiteRunner(context) }
            .onFailure { Log.w("HazardDetect", "TFLiteRunner 초기화 실패 (모델 미배치)", it) }
            .getOrNull()
    }
    DisposableEffect(runner) {
        onDispose { runner?.close() }
    }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    var lastDetection by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1A1A2E)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp)
        ) {
            // 헤더
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "AI 위험 감지",
                    fontSize = 18.sp,
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

                                val analyzer = HazardDetectionAnalyzer(ctx, scope) { bitmap ->
                                    runner.classify(bitmap)?.let { res ->
                                        lastDetection = "${res.label}  ${(res.confidence * 100).toInt()}%"
                                        HazardDetectionAnalyzer.DetectionResult(res.label, res.confidence)
                                    }
                                }
                                analysis.setAnalyzer(analyzerExecutor) { proxy ->
                                    val rotated = proxy.toRotatedBitmap()
                                    if (rotated != null) {
                                        analyzer.analyzeBitmap(rotated)
                                    }
                                    proxy.close()
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
                text = "임계값 ${HazardDetectionAnalyzer.THRESHOLD} 이상 + 같은 타입 ${HazardDetectionAnalyzer.COOLDOWN_MS / 1000}초 쿨다운으로 자동 신고합니다.",
                color = Color(0xFF78909C),
                fontSize = 11.sp
            )
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
