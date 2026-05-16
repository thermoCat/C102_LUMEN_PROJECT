package com.smartcane.backend.domain.trafficlight.service;

import com.smartcane.backend.domain.trafficlight.entity.TrafficLight;
import com.smartcane.backend.domain.trafficlight.repository.TrafficLightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TrafficLightService {

    private final TrafficLightRepository trafficLightRepository;

    public TrafficLightService(TrafficLightRepository trafficLightRepository) {
        this.trafficLightRepository = trafficLightRepository;
    }

    @Transactional(readOnly = true)
    public List<TrafficLight> findNearby(double lat, double lng, double radiusMeters) {
        return trafficLightRepository.findNearby(lat, lng, radiusMeters);
    }

    @Transactional(readOnly = true)
    public List<TrafficLight> findInBbox(double minLng, double minLat, double maxLng, double maxLat) {
        return trafficLightRepository.findInBbox(minLng, minLat, maxLng, maxLat);
    }

    @Transactional(readOnly = true)
    public List<TrafficLight> findNearest2(double lat, double lng) {
        return trafficLightRepository.findNearest2(lat, lng, 10.0);
    }
}
