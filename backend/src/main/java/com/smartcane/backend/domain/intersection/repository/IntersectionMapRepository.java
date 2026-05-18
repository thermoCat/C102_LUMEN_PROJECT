package com.smartcane.backend.domain.intersection.repository;

import com.smartcane.backend.domain.intersection.entity.IntersectionMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntersectionMapRepository extends JpaRepository<IntersectionMap, Long> {

    Optional<IntersectionMap> findByCrsrdId(String crsrdId);

    List<IntersectionMap> findByStdgCd(String stdgCd);

    @Query(value = """
            SELECT * FROM intersection_maps
            WHERE ST_DWithin(
                location,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                :radiusMeters
            )
            ORDER BY ST_Distance(
                location,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            )
            """, nativeQuery = true)
    List<IntersectionMap> findNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") double radiusMeters
    );
}
