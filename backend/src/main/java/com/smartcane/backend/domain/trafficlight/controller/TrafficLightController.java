package com.smartcane.backend.domain.trafficlight.controller;

import com.smartcane.backend.domain.trafficlight.dto.TrafficLightGeoJsonResponse;
import com.smartcane.backend.domain.trafficlight.entity.TrafficLight;
import com.smartcane.backend.domain.trafficlight.service.TrafficLightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "TrafficLight", description = "신호등 위치 조회 API")
@RestController
@RequestMapping("/traffic-lights")
public class TrafficLightController {

    private final TrafficLightService trafficLightService;

    public TrafficLightController(TrafficLightService trafficLightService) {
        this.trafficLightService = trafficLightService;
    }

    @Operation(summary = "현재 위치 반경 내 신호등 조회", description = "위도/경도/반경(m) 기준으로 주변 신호등 GeoJSON 반환")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "GeoJSON 반환")
    })
    @GetMapping("/nearby")
    public TrafficLightGeoJsonResponse nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false, defaultValue = "100") double radius
    ) {
        List<TrafficLight> results = trafficLightService.findNearby(lat, lng, radius);
        return TrafficLightGeoJsonResponse.from(results);
    }

    @Operation(summary = "현재 위치 최근접 신호등 2개 조회 (최대 10m)",
            description = "횡단보도 디텍션 후 호출. 반경 10m 이내에서 가장 가까운 신호등 최대 2개 반환 (교차로 꼭짓점 대응)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "GeoJSON 반환 (0~2개)")
    })
    @GetMapping("/nearest")
    public TrafficLightGeoJsonResponse nearest(
            @RequestParam double lat,
            @RequestParam double lng
    ) {
        List<TrafficLight> results = trafficLightService.findNearest2(lat, lng);
        return TrafficLightGeoJsonResponse.from(results);
    }

    @Operation(summary = "지도 영역(bbox) 내 신호등 조회", description = "지도 뷰포트 기준 신호등 GeoJSON 반환")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "GeoJSON 반환")
    })
    @GetMapping
    public TrafficLightGeoJsonResponse listByBbox(
            @RequestParam double minLng,
            @RequestParam double minLat,
            @RequestParam double maxLng,
            @RequestParam double maxLat
    ) {
        List<TrafficLight> results = trafficLightService.findInBbox(minLng, minLat, maxLng, maxLat);
        return TrafficLightGeoJsonResponse.from(results);
    }
}
