package com.smartcane.backend.domain.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LocationUpdateRequest {

    @NotBlank
    private String deviceId;

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;

    public String getDeviceId() { return deviceId; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }

    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setLat(Double lat) { this.lat = lat; }
    public void setLng(Double lng) { this.lng = lng; }
}
