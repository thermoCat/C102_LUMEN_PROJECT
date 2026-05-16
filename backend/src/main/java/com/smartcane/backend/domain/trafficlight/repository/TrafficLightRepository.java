package com.smartcane.backend.domain.trafficlight.repository;

import com.smartcane.backend.domain.trafficlight.entity.TrafficLight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrafficLightRepository extends JpaRepository<TrafficLight, Long> {

    List<TrafficLight> findByProvinceNameAndCityDistrictName(String provinceName, String cityDistrictName);

    @Query(value = """
            SELECT * FROM traffic_lights
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
    List<TrafficLight> findNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") double radiusMeters
    );
}
