package com.smartcane.backend.domain.hazard.service;

import com.smartcane.backend.domain.hazard.dto.HazardReportRequest;
import com.smartcane.backend.domain.hazard.entity.Hazard;
import com.smartcane.backend.domain.hazard.entity.HazardType;
import com.smartcane.backend.domain.hazard.repository.HazardRepository;
import com.smartcane.backend.global.s3.S3ImageStorageService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class HazardService {

    private static final Logger log = LoggerFactory.getLogger(HazardService.class);
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final HazardRepository hazardRepository;
    private final S3ImageStorageService s3ImageStorageService;

    public HazardService(HazardRepository hazardRepository, S3ImageStorageService s3ImageStorageService) {
        this.hazardRepository = hazardRepository;
        this.s3ImageStorageService = s3ImageStorageService;
    }

    @Transactional
    public Hazard report(HazardReportRequest req) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(req.getLng(), req.getLat()));
        point.setSRID(4326);

        Hazard hazard = new Hazard(point, req.getType(), req.getConfidence(), null, req.getDescription());
        Hazard saved = hazardRepository.save(hazard);

        if (req.getImageBase64() != null && !req.getImageBase64().isBlank()) {
            CompletableFuture<String> future = s3ImageStorageService.uploadAsync(req.getImageBase64());
            future.thenAccept(url -> {
                if (url != null) {
                    try {
                        updateImageUrl(saved.getId(), url);
                    } catch (Exception e) {
                        log.warn("image_url 업데이트 실패 id={}", saved.getId(), e);
                    }
                }
            });
        }

        return saved;
    }

    @Transactional
    public void updateImageUrl(Long id, String url) {
        hazardRepository.findById(id).ifPresent(h -> {
            h.setImageUrl(url);
            hazardRepository.save(h);
        });
    }

    @Transactional(readOnly = true)
    public List<Hazard> findInBbox(double minLng, double minLat, double maxLng, double maxLat,
                                    HazardType type, String startDate, String endDate) {
        LocalDateTime start = parseStart(startDate);
        LocalDateTime end = parseEnd(endDate);
        String typeStr = type != null ? type.name() : null;
        return hazardRepository.findInBbox(minLng, minLat, maxLng, maxLat, typeStr, start, end);
    }

    @Transactional(readOnly = true)
    public List<Hazard> findNearby(double lat, double lng, double radiusMeters) {
        return hazardRepository.findNearby(lat, lng, radiusMeters);
    }

    private LocalDateTime parseStart(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s).atStartOfDay();
    }

    private LocalDateTime parseEnd(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s).atTime(LocalTime.MAX);
    }
}
