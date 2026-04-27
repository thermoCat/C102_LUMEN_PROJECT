package com.smartcane.backend.domain.hazard.controller;

import com.smartcane.backend.domain.hazard.dto.GeoJsonResponse;
import com.smartcane.backend.domain.hazard.dto.HazardReportRequest;
import com.smartcane.backend.domain.hazard.entity.Hazard;
import com.smartcane.backend.domain.hazard.entity.HazardType;
import com.smartcane.backend.domain.hazard.service.HazardService;
import com.smartcane.backend.global.s3.S3ImageStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Tag(name = "Hazard", description = "위험구간 수집 및 공공 API")
@RestController
@RequestMapping("/api/hazards")
public class HazardController {

    private static final Logger log = LoggerFactory.getLogger(HazardController.class);
    private final HazardService hazardService;
    private final S3ImageStorageService s3ImageStorageService;

    public HazardController(HazardService hazardService, S3ImageStorageService s3ImageStorageService) {
        this.hazardService = hazardService;
        this.s3ImageStorageService = s3ImageStorageService;
    }

    @Operation(summary = "위험구간 신고", description = "앱 온디바이스 AI 탐지 결과 업로드")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 완료"),
            @ApiResponse(responseCode = "400", description = "잘못된 type 값 또는 필수 파라미터 누락"),
            @ApiResponse(responseCode = "503", description = "저장 실패 — 앱이 재전송")
    })
    @PostMapping
    public ResponseEntity<?> report(@Valid @RequestBody HazardReportRequest request) {
        try {
            Hazard saved = hazardService.report(request);
            return ResponseEntity.ok(Map.of("id", saved.getId(), "message", "저장 완료"));
        } catch (Exception e) {
            log.error("hazard 저장 실패", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "저장 실패"));
        }
    }

    @Operation(summary = "bbox 위험 데이터 조회 (공공 API)",
            description = "GeoJSON FeatureCollection 반환. superApp")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "GeoJSON 반환")
    })
    @GetMapping
    public GeoJsonResponse listByBbox(
            @RequestParam double minLng,
            @RequestParam double minLat,
            @RequestParam double maxLng,
            @RequestParam double maxLat,
            @RequestParam(required = false) HazardType type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        List<Hazard> hazards = hazardService.findInBbox(minLng, minLat, maxLng, maxLat, type, startDate, endDate);
        return GeoJsonResponse.from(hazards);
    }

    @Operation(summary = "현재 위치 반경 조회 (일단 임시로 놔두자)", description = "앱 실시간 경고용. 거리 가까운 순 정렬.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "GeoJSON 반환")
    })
    @GetMapping("/nearby")
    public GeoJsonResponse nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false, defaultValue = "100") double radius
    ) {
        List<Hazard> hazards = hazardService.findNearby(lat, lng, radius);
        return GeoJsonResponse.from(hazards);
    }

    @Operation(summary = "위험구간 이미지 조회", description = "S3 Presigned URL(10분 유효)로 리다이렉트합니다.")
    @GetMapping("/image")
    public ResponseEntity<?> image(@RequestParam String s3Url) {
        try {
            String presigned = s3ImageStorageService.generatePresignedUrl(s3Url);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(presigned))
                    .build();
        } catch (Exception e) {
            log.error("presigned URL 생성 실패", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadEnum(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "잘못된 파라미터: " + e.getMessage()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "요청 본문 파싱 실패 (type 값 확인)"));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "잘못된 쿼리 파라미터"));
    }
}
