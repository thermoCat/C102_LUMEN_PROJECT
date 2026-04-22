package com.ssafy.trafficlightstandalone.integrated.model

enum class ModelConfig(
    val modelAsset: String,
    val labelsAsset: String,
    val inputSize: Int,
    val displayName: String,
) {
    YOLOV8N(
        modelAsset = "model_yolov8n.tflite",
        labelsAsset = "labels.txt",
        inputSize = 640,
        displayName = "YOLOv8n (경량)",
    ),
    YOLOV8S(
        modelAsset = "model_yolov8s.tflite",
        labelsAsset = "labels.txt",
        inputSize = 640,
        displayName = "YOLOv8s (표준)",
    ),
}
