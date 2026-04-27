package com.smartcane.backend.domain.hazard.repository;

import com.smartcane.backend.domain.hazard.entity.Hazard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HazardRepository extends JpaRepository<Hazard, Long> {

    @Query(value = """
            SELECT * FROM hazards
            WHERE ST_Within(
                location::geometry,
                ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)
            )
            AND (:type IS NULL OR type = :type)
            AND (CAST(:start AS timestamp) IS NULL OR reported_at >= :start)
            AND (CAST(:end AS timestamp) IS NULL OR reported_at <= :end)
            ORDER BY reported_at DESC
            """, nativeQuery = true)
    List<Hazard> findInBbox(
            @Param("minLng") double minLng,
            @Param("minLat") double minLat,
            @Param("maxLng") double maxLng,
            @Param("maxLat") double maxLat,
            @Param("type") String type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
            SELECT * FROM hazards
            WHERE ST_DWithin(
                location::geography,
                ST_MakePoint(:lng, :lat)::geography,
                :radiusMeters
            )
            ORDER BY ST_Distance(location::geography, ST_MakePoint(:lng, :lat)::geography)
            """, nativeQuery = true)
    List<Hazard> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters
    );
}
