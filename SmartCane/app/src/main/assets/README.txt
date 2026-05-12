TFLite 모델 배치 위치
=========================

이 폴더에 기본 모델과 라벨 파일을 넣으면 자동 감지가 동작합니다.

1) yolo11n_fine_tune.tflite
   - YOLOv11 TFLite 모델. 입력 [1, H, W, 3] (uint8 또는 float32), 출력 [1, 4+C, N]
   - 입력 H/W는 모델이 알려주는 값으로 자동 리사이즈됩니다.
   - float32 입력이면 0~1 정규화 (mean=0, std=255)로 자동 처리됩니다.
   - 기본 NMS IoU는 0.45, 기본 confidence는 클래스별 threshold를 사용합니다.

2) labels.txt
   - 한 줄에 한 라벨, 순서는 모델 출력 인덱스와 동일.
   - TFLite raw 라벨 그대로 적으면 됩니다 (콜론 허용).
     예시:
       pole
       bollard
       barricade
       movable_signage
       sidewalk:damaged
       braille_guide_blocks:damaged
       alley:damaged
       caution_zone:stairs
       caution_zone:manhole
       caution_zone:grating
       caution_zone:repair_zone
   - HazardType.fromTfliteLabel()이 콜론을 언더스코어로 바꿔 백엔드 enum으로 변환합니다.

모델/라벨 형식이나 파일명을 바꾸면 TFLiteRunner.kt의 기본값도 같이 수정해야 합니다.
