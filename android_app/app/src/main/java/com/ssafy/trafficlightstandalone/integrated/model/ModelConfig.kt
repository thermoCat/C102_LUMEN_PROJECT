package com.ssafy.trafficlightstandalone.integrated.model

enum class ModelConfig(
    val modelAsset: String,
    val labelsAsset: String,
    val inputSize: Int,
    val displayName: String,
) {
    MODEL_0000_YOLOV8N_260408(
        modelAsset = "model_0000_yolov8n_260408.tflite",
        labelsAsset = "labels_0000_yolov8n_260408.txt",
        inputSize = 640,
        displayName = "0000 YOLOv8n (260408)",
    ),
    MODEL_0015_YOLO11N(
        modelAsset = "model_0015_yolo11n.tflite",
        labelsAsset = "labels_0015_yolo11n.txt",
        inputSize = 640,
        displayName = "0015 YOLOv11n",
    ),
    MODEL_0016_YOLO11N_SMALL_OBJECT(
        modelAsset = "model_0016_yolo11_small_object.tflite",
        labelsAsset = "labels_0016_yolo11_small_object.txt",
        inputSize = 640,
        displayName = "0016 YOLOv11n Small Object",
    ),
}
