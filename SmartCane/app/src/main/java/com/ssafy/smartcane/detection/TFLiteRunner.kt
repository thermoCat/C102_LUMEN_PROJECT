package com.ssafy.smartcane.detection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val DEFAULT_TFLITE_MODEL_FILE_NAME = "yolo11n_fine_tune.tflite"
private const val DEFAULT_LABELS_ASSET_FILE_NAME = "labels.txt"

class TFLiteRunner(
    context: Context,
    modelFileName: String = DEFAULT_TFLITE_MODEL_FILE_NAME,
    labelsFileName: String = DEFAULT_LABELS_ASSET_FILE_NAME
) : AutoCloseable {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputH: Int
    private val inputW: Int
    private val inputDType: DataType
    private val outputShape: IntArray

    init {
        val afd = context.assets.openFd(modelFileName)
        val model = afd.createInputStream().channel.map(
            FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
        )
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })

        labels = context.assets.open(labelsFileName).use { stream ->
            BufferedReader(InputStreamReader(stream)).readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        val inputTensor = interpreter.getInputTensor(0)
        val inShape = inputTensor.shape()
        inputH = inShape[1]
        inputW = inShape[2]
        inputDType = inputTensor.dataType()

        val outputTensor = interpreter.getOutputTensor(0)
        outputShape = outputTensor.shape()
    }

    /**
     * 가장 높은 confidence 탐지 1개 반환 (HazardDetectionAnalyzer 호환).
     * 기본값은 클래스별 threshold와 전역 최소 threshold를 함께 적용.
     */
    fun classify(bitmap: Bitmap, minConfidence: Float = MIN_CONFIDENCE_THRESHOLD): Result? =
        detectAll(bitmap, minConfidence).maxByOrNull { it.confidence }

    /**
     * confidence가 클래스별 threshold 이상인 모든 탐지 반환.
     * [minConfidence]는 모든 클래스에 적용할 전역 하한값이며, 클래스별 threshold보다 낮으면 클래스별 값이 우선한다.
     *
     * 지원 출력 형식:
     *  A) [1, N, 6]          — [x1,y1,x2,y2,conf,class_id]  (구형 단일 클래스 conf)
     *  B) [1, N, 4+C]        — [cx,cy,w,h, c0,c1,...,cC]    (YOLOv8/v11 비전치)
     *  C) [1, 4+C, N]        — 전치(transposed) YOLOv8/v11   ← 해당 모델
     *
     * 형식 C 감지 기준: stride(마지막 차원) > numDetections(앞 차원) * 10
     */
    fun detectAll(bitmap: Bitmap, minConfidence: Float = MIN_CONFIDENCE_THRESHOLD): List<Result> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        val inputBuffer = bitmapToByteBuffer(resized)
        // 시연 안정성: scale로 새 비트맵이 생성된 경우 즉시 recycle (저사양 기기 OOM 방지)
        if (resized !== bitmap && !resized.isRecycled) resized.recycle()

        val outputSize = outputShape.fold(1) { acc, dim -> acc * dim }
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val flat = FloatArray(outputSize) { outputBuffer.float }

        val dimA = if (outputShape.size >= 2) outputShape[outputShape.size - 2] else return emptyList()
        val dimB = if (outputShape.size >= 1) outputShape[outputShape.size - 1] else return emptyList()

        // 형식 C 판별: 전치 여부 — dimB(앵커 수, ~8400) >> dimA(채널 수, ~26)
        val isTransposed = dimB > dimA * 10

        val numAnchors  = if (isTransposed) dimB else dimA
        val numChannels = if (isTransposed) dimA else dimB

        com.ssafy.smartcane.util.AppLogger.log("TFLite",
            "shape=[${outputShape.joinToString()}] transposed=$isTransposed anchors=$numAnchors channels=$numChannels")

        // 채널 수로 형식 구분
        val isLegacy = numChannels == 6  // 형식 A: conf + class_id
        val hasObjScore = !isLegacy && numChannels == 5 + labels.size  // YOLOv5: obj + classes
        val classStart = when {
            isLegacy    -> 4   // 사용 안 함 (별도 처리)
            hasObjScore -> 5
            else        -> 4   // YOLOv8/v11: bbox(4) + classes
        }

        val results = mutableListOf<Result>()

        for (i in 0 until numAnchors) {
            // bbox 좌표 읽기
            val cx: Float; val cy: Float; val bw: Float; val bh: Float
            if (isTransposed) {
                cx = flat[0 * numAnchors + i]
                cy = flat[1 * numAnchors + i]
                bw = flat[2 * numAnchors + i]
                bh = flat[3 * numAnchors + i]
            } else {
                val base = i * numChannels
                cx = flat[base + 0]; cy = flat[base + 1]
                bw = flat[base + 2]; bh = flat[base + 3]
            }

            val conf: Float
            val classId: Int

            if (isLegacy) {
                // 형식 A: flat[4]=conf, flat[5]=class_id
                conf = if (isTransposed) flat[4 * numAnchors + i]
                       else flat[i * numChannels + 4]
                classId = (if (isTransposed) flat[5 * numAnchors + i]
                           else flat[i * numChannels + 5]).toInt()
            } else {
                // 형식 B/C: argmax over class scores
                val objScore = if (hasObjScore) {
                    if (isTransposed) flat[4 * numAnchors + i]
                    else flat[i * numChannels + 4]
                } else 1f

                // labels.txt 수가 모델 클래스 수보다 많으면 범위 초과 방지
                val numClasses = minOf(labels.size, numChannels - classStart)
                if (numClasses <= 0) continue

                var maxScore = 0f; var maxIdx = 0
                for (c in 0 until numClasses) {
                    val score = if (isTransposed) flat[(classStart + c) * numAnchors + i]
                                else flat[i * numChannels + classStart + c]
                    if (score > maxScore) { maxScore = score; maxIdx = c }
                }
                conf    = objScore * maxScore
                classId = maxIdx
            }

            val label = labels.getOrNull(classId) ?: continue
            val threshold = maxOf(minConfidence, thresholdFor(label))
            if (conf < threshold) continue

            // 좌표 형식 자동 감지: cx,cy,w,h vs x1,y1,x2,y2
            // YOLOv11(전치)는 항상 cx,cy,w,h; 레거시는 둘 다 가능
            val isCxCyWH = isTransposed || bw < cx || bh < cy
            val x1: Float; val y1: Float; val x2: Float; val y2: Float
            if (isCxCyWH) {
                x1 = (cx - bw / 2f).coerceIn(0f, 1f)
                y1 = (cy - bh / 2f).coerceIn(0f, 1f)
                x2 = (cx + bw / 2f).coerceIn(0f, 1f)
                y2 = (cy + bh / 2f).coerceIn(0f, 1f)
            } else {
                x1 = cx; y1 = cy; x2 = bw; y2 = bh
            }

            results += Result(label, conf, x1, y1, x2, y2)
        }

        // NMS: 겹치는 박스 제거 (IoU > 0.45이면 낮은 confidence 제거)
        val nmsResult = nms(results)
        com.ssafy.smartcane.util.AppLogger.log("TFLite",
            "raw=${results.size} → NMS후=${nmsResult.size} (threshold=class-specific, min=$minConfidence)")
        return nmsResult
    }

    private fun thresholdFor(label: String): Float {
        return CLASS_CONFIDENCE_THRESHOLDS[label.trim()] ?: CONFIDENCE_THRESHOLD
    }

    /** Non-Maximum Suppression */
    private fun nms(detections: List<Result>, iouThreshold: Float = 0.45f): List<Result> {
        val sorted = detections.sortedByDescending { it.confidence }
        val kept = mutableListOf<Result>()
        for (det in sorted) {
            if (kept.none { iou(it, det) > iouThreshold }) {
                kept += det
            }
        }
        return kept
    }

    private fun iou(a: Result, b: Result): Float {
        val ix1 = maxOf(a.x1, b.x1); val iy1 = maxOf(a.y1, b.y1)
        val ix2 = minOf(a.x2, b.x2); val iy2 = minOf(a.y2, b.y2)
        val inter = maxOf(0f, ix2 - ix1) * maxOf(0f, iy2 - iy1)
        val union = (a.x2-a.x1)*(a.y2-a.y1) + (b.x2-b.x1)*(b.y2-b.y1) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val isFloat = inputDType == DataType.FLOAT32
        val bytesPerPixel = if (isFloat) 4 else 1
        val buffer = ByteBuffer.allocateDirect(inputH * inputW * 3 * bytesPerPixel).apply {
            order(ByteOrder.nativeOrder())
        }
        val pixels = IntArray(inputH * inputW)
        bitmap.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (isFloat) {
                buffer.putFloat(r / 255f)
                buffer.putFloat(g / 255f)
                buffer.putFloat(b / 255f)
            } else {
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            }
        }
        buffer.rewind()
        return buffer
    }

    override fun close() { interpreter.close() }

    data class Result(
        val label: String,
        val confidence: Float,
        /** 정규화 좌표 0~1 (모델 입력 해상도 기준) */
        val x1: Float = 0f, val y1: Float = 0f,
        val x2: Float = 0f, val y2: Float = 0f
    )

    companion object {
        const val DEFAULT_MODEL_FILE_NAME = DEFAULT_TFLITE_MODEL_FILE_NAME
        const val DEFAULT_LABELS_FILE_NAME = DEFAULT_LABELS_ASSET_FILE_NAME
        const val CONFIDENCE_THRESHOLD = 0.175f
        const val MIN_CONFIDENCE_THRESHOLD = 0.10f

        private val CLASS_CONFIDENCE_THRESHOLDS = mapOf(
            "sidewalk:damaged" to 0.10f,
            "alley:damaged" to 0.10f,
            "braille_guide_blocks:damaged" to 0.10f,
            "caution_zone:repair_zone" to 0.10f,
            "caution_zone:stairs" to 0.12f,
            "caution_zone:manhole" to 0.30f,
            "caution_zone:grating" to 0.30f,
            "crosswalk" to 0.10f,
            "green_light" to 0.30f,
            "red_light" to 0.30f,
            "pedestrian_traffic_light" to 0.30f
        )
    }
}
