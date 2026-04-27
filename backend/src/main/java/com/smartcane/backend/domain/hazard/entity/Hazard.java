package com.smartcane.backend.domain.hazard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(name = "hazards")
public class Hazard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location", columnDefinition = "geography(Point,4326)", nullable = false)
    private Point location;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private HazardType type;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @PrePersist
    public void prePersist() {
        if (reportedAt == null) {
            reportedAt = LocalDateTime.now();
        }
    }

    public Hazard() {}

    public Hazard(Point location, HazardType type, Double confidence, String imageUrl, String description) {
        this.location = location;
        this.type = type;
        this.confidence = confidence;
        this.imageUrl = imageUrl;
        this.description = description;
    }

    public Long getId() { return id; }
    public Point getLocation() { return location; }
    public HazardType getType() { return type; }
    public Double getConfidence() { return confidence; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public LocalDateTime getReportedAt() { return reportedAt; }

    public void setId(Long id) { this.id = id; }
    public void setLocation(Point location) { this.location = location; }
    public void setType(HazardType type) { this.type = type; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setDescription(String description) { this.description = description; }
    public void setReportedAt(LocalDateTime reportedAt) { this.reportedAt = reportedAt; }
}
