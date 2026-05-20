package com.smartcane.backend.domain.trafficlight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartcane.backend.domain.trafficlight.entity.TrafficLight;

import java.math.BigDecimal;
import java.util.List;

public class TrafficLightGeoJsonResponse {

    private final String type = "FeatureCollection";
    private int totalCount;
    private List<Feature> features;

    public TrafficLightGeoJsonResponse(List<Feature> features) {
        this.features = features;
        this.totalCount = features.size();
    }

    public String getType() { return type; }
    public int getTotalCount() { return totalCount; }
    public List<Feature> getFeatures() { return features; }

    public static TrafficLightGeoJsonResponse from(List<TrafficLight> trafficLights) {
        List<Feature> features = trafficLights.stream().map(Feature::from).toList();
        return new TrafficLightGeoJsonResponse(features);
    }

    public static class Feature {
        private final String type = "Feature";
        private Geometry geometry;
        private Properties properties;

        public Feature(Geometry geometry, Properties properties) {
            this.geometry = geometry;
            this.properties = properties;
        }

        public String getType() { return type; }
        public Geometry getGeometry() { return geometry; }
        public Properties getProperties() { return properties; }

        public static Feature from(TrafficLight t) {
            double[] coords = {
                t.getLongitude().doubleValue(),
                t.getLatitude().doubleValue()
            };
            Properties props = new Properties(
                t.getId(),
                t.getManagementNumber(),
                t.getRoadNameAddress(),
                t.getRoadRouteName(),
                t.getTrafficLightType(),
                t.getFacingDirection(),
                t.getLightingDuration(),
                t.getLightingSequence()
            );
            return new Feature(new Geometry(coords), props);
        }
    }

    public static class Geometry {
        private final String type = "Point";
        private double[] coordinates;

        public Geometry(double[] coordinates) { this.coordinates = coordinates; }

        public String getType() { return type; }
        public double[] getCoordinates() { return coordinates; }
    }

    public static class Properties {
        private Long id;
        @JsonProperty("managementNumber")
        private String managementNumber;
        @JsonProperty("roadNameAddress")
        private String roadNameAddress;
        @JsonProperty("roadRouteName")
        private String roadRouteName;
        @JsonProperty("trafficLightType")
        private Short trafficLightType;
        @JsonProperty("facingDirection")
        private BigDecimal facingDirection;
        @JsonProperty("lightingDuration")
        private Short lightingDuration;
        @JsonProperty("lightingSequence")
        private String lightingSequence;

        public Properties(Long id, String managementNumber, String roadNameAddress,
                          String roadRouteName, Short trafficLightType, BigDecimal facingDirection,
                          Short lightingDuration, String lightingSequence) {
            this.id = id;
            this.managementNumber = managementNumber;
            this.roadNameAddress = roadNameAddress;
            this.roadRouteName = roadRouteName;
            this.trafficLightType = trafficLightType;
            this.facingDirection = facingDirection;
            this.lightingDuration = lightingDuration;
            this.lightingSequence = lightingSequence;
        }

        public Long getId() { return id; }
        public String getManagementNumber() { return managementNumber; }
        public String getRoadNameAddress() { return roadNameAddress; }
        public String getRoadRouteName() { return roadRouteName; }
        public Short getTrafficLightType() { return trafficLightType; }
        public BigDecimal getFacingDirection() { return facingDirection; }
        public Short getLightingDuration() { return lightingDuration; }
        public String getLightingSequence() { return lightingSequence; }
    }
}
