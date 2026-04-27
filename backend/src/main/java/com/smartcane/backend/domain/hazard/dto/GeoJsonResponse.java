package com.smartcane.backend.domain.hazard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartcane.backend.domain.hazard.entity.Hazard;

import java.time.LocalDateTime;
import java.util.List;

public class GeoJsonResponse {

    private final String type = "FeatureCollection";
    private int totalCount;
    private List<Feature> features;

    public GeoJsonResponse(List<Feature> features) {
        this.features = features;
        this.totalCount = features.size();
    }

    public String getType() { return type; }
    public int getTotalCount() { return totalCount; }
    public List<Feature> getFeatures() { return features; }

    public static GeoJsonResponse from(List<Hazard> hazards) {
        List<Feature> features = hazards.stream().map(Feature::from).toList();
        return new GeoJsonResponse(features);
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

        public static Feature from(Hazard h) {
            double[] coords = { h.getLocation().getX(), h.getLocation().getY() };
            Geometry geom = new Geometry(coords);
            Properties props = new Properties(
                    h.getId(),
                    h.getType() != null ? h.getType().name() : null,
                    h.getConfidence(),
                    h.getDescription(),
                    h.getImageUrl(),
                    h.getReportedAt()
            );
            return new Feature(geom, props);
        }
    }

    public static class Geometry {
        private final String type = "Point";
        private double[] coordinates;

        public Geometry(double[] coordinates) {
            this.coordinates = coordinates;
        }

        public String getType() { return type; }
        public double[] getCoordinates() { return coordinates; }
    }

    public static class Properties {
        private Long id;
        private String type;
        private Double confidence;
        private String description;
        @JsonProperty("imageUrl")
        private String imageUrl;
        @JsonProperty("reportedAt")
        private LocalDateTime reportedAt;

        public Properties(Long id, String type, Double confidence, String description, String imageUrl, LocalDateTime reportedAt) {
            this.id = id;
            this.type = type;
            this.confidence = confidence;
            this.description = description;
            this.imageUrl = imageUrl;
            this.reportedAt = reportedAt;
        }

        public Long getId() { return id; }
        public String getType() { return type; }
        public Double getConfidence() { return confidence; }
        public String getDescription() { return description; }
        public String getImageUrl() { return imageUrl; }
        public LocalDateTime getReportedAt() { return reportedAt; }
    }
}
