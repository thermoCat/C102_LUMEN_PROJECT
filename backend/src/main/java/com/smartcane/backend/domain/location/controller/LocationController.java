package com.smartcane.backend.domain.location.controller;

import com.smartcane.backend.domain.location.dto.LocationUpdateRequest;
import com.smartcane.backend.domain.location.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Location", description = "실시간 보행자 위치")
@RestController
@RequestMapping("/api/location")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @Operation(summary = "앱 → 서버 위치 전송")
    @PostMapping
    public ResponseEntity<Void> update(@RequestBody @Valid LocationUpdateRequest req) {
        locationService.publish(req);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "map.html SSE 스트림 구독")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        return locationService.subscribe();
    }
}
