package com.smartcane.backend.domain.hazard.dto;

import com.smartcane.backend.domain.hazard.entity.HazardType;
import jakarta.validation.constraints.NotNull;

public class HazardReportRequest {

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;

    @NotNull
    private HazardType type;

    private Double confidence;
    private String imageBase64;
    private String description;

    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public HazardType getType() { return type; }
    public Double getConfidence() { return confidence; }
    public String getImageBase64() { return imageBase64; }
    public String getDescription() { return description; }

    public void setLat(Double lat) { this.lat = lat; }
    public void setLng(Double lng) { this.lng = lng; }
    public void setType(HazardType type) { this.type = type; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
    public void setDescription(String description) { this.description = description; }
}
