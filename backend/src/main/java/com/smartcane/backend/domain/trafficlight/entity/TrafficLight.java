package com.smartcane.backend.domain.trafficlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "traffic_lights")
public class TrafficLight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "province_name", length = 50)
    private String provinceName;

    @Column(name = "city_district_name", length = 50)
    private String cityDistrictName;

    @Column(name = "road_type")
    private Short roadType;

    @Column(name = "road_route_number", length = 20)
    private String roadRouteNumber;

    @Column(name = "road_route_name", length = 100)
    private String roadRouteName;

    @Column(name = "road_route_direction")
    private Short roadRouteDirection;

    @Column(name = "road_name_address", length = 200)
    private String roadNameAddress;

    @Column(name = "lot_number_address", length = 200)
    private String lotNumberAddress;

    @Column(name = "latitude", precision = 12, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 12, scale = 8)
    private BigDecimal longitude;

    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "installation_method")
    private Short installationMethod;

    @Column(name = "road_shape")
    private Short roadShape;

    @Column(name = "is_main_road")
    private Boolean isMainRoad;

    @Column(name = "management_number", length = 50)
    private String managementNumber;

    @Column(name = "traffic_light_type")
    private Short trafficLightType;

    @Column(name = "light_color_type")
    private Short lightColorType;

    @Column(name = "lighting_method")
    private Short lightingMethod;

    @Column(name = "lighting_sequence", length = 100)
    private String lightingSequence;

    @Column(name = "lighting_duration")
    private Short lightingDuration;

    @Column(name = "light_source_type")
    private Short lightSourceType;

    @Column(name = "signal_control_method")
    private Short signalControlMethod;

    @Column(name = "signal_time_method")
    private Short signalTimeMethod;

    @Column(name = "is_flashing_operated")
    private Boolean isFlashingOperated;

    @Column(name = "flashing_start_time")
    private LocalTime flashingStartTime;

    @Column(name = "flashing_end_time")
    private LocalTime flashingEndTime;

    @Column(name = "has_pedestrian_actuated")
    private Boolean hasPedestrianActuated;

    @Column(name = "has_countdown_display")
    private Boolean hasCountdownDisplay;

    @Column(name = "has_visual_impaired_sound")
    private Boolean hasVisualImpairedSound;

    @Column(name = "road_sign_serial_number", length = 50)
    private String roadSignSerialNumber;

    @Column(name = "management_agency_name", length = 100)
    private String managementAgencyName;

    @Column(name = "management_agency_phone", length = 30)
    private String managementAgencyPhone;

    @Column(name = "data_reference_date")
    private LocalDate dataReferenceDate;

    @Column(name = "facing_direction", precision = 5, scale = 2)
    private BigDecimal facingDirection;

    public TrafficLight() {}

    public Long getId() { return id; }
    public String getProvinceName() { return provinceName; }
    public String getCityDistrictName() { return cityDistrictName; }
    public Short getRoadType() { return roadType; }
    public String getRoadRouteNumber() { return roadRouteNumber; }
    public String getRoadRouteName() { return roadRouteName; }
    public Short getRoadRouteDirection() { return roadRouteDirection; }
    public String getRoadNameAddress() { return roadNameAddress; }
    public String getLotNumberAddress() { return lotNumberAddress; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public Point getLocation() { return location; }
    public Short getInstallationMethod() { return installationMethod; }
    public Short getRoadShape() { return roadShape; }
    public Boolean getIsMainRoad() { return isMainRoad; }
    public String getManagementNumber() { return managementNumber; }
    public Short getTrafficLightType() { return trafficLightType; }
    public Short getLightColorType() { return lightColorType; }
    public Short getLightingMethod() { return lightingMethod; }
    public String getLightingSequence() { return lightingSequence; }
    public Short getLightingDuration() { return lightingDuration; }
    public Short getLightSourceType() { return lightSourceType; }
    public Short getSignalControlMethod() { return signalControlMethod; }
    public Short getSignalTimeMethod() { return signalTimeMethod; }
    public Boolean getIsFlashingOperated() { return isFlashingOperated; }
    public LocalTime getFlashingStartTime() { return flashingStartTime; }
    public LocalTime getFlashingEndTime() { return flashingEndTime; }
    public Boolean getHasPedestrianActuated() { return hasPedestrianActuated; }
    public Boolean getHasCountdownDisplay() { return hasCountdownDisplay; }
    public Boolean getHasVisualImpairedSound() { return hasVisualImpairedSound; }
    public String getRoadSignSerialNumber() { return roadSignSerialNumber; }
    public String getManagementAgencyName() { return managementAgencyName; }
    public String getManagementAgencyPhone() { return managementAgencyPhone; }
    public LocalDate getDataReferenceDate() { return dataReferenceDate; }
    public BigDecimal getFacingDirection() { return facingDirection; }

    public void setId(Long id) { this.id = id; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; }
    public void setCityDistrictName(String cityDistrictName) { this.cityDistrictName = cityDistrictName; }
    public void setRoadType(Short roadType) { this.roadType = roadType; }
    public void setRoadRouteNumber(String roadRouteNumber) { this.roadRouteNumber = roadRouteNumber; }
    public void setRoadRouteName(String roadRouteName) { this.roadRouteName = roadRouteName; }
    public void setRoadRouteDirection(Short roadRouteDirection) { this.roadRouteDirection = roadRouteDirection; }
    public void setRoadNameAddress(String roadNameAddress) { this.roadNameAddress = roadNameAddress; }
    public void setLotNumberAddress(String lotNumberAddress) { this.lotNumberAddress = lotNumberAddress; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public void setLocation(Point location) { this.location = location; }
    public void setInstallationMethod(Short installationMethod) { this.installationMethod = installationMethod; }
    public void setRoadShape(Short roadShape) { this.roadShape = roadShape; }
    public void setIsMainRoad(Boolean isMainRoad) { this.isMainRoad = isMainRoad; }
    public void setManagementNumber(String managementNumber) { this.managementNumber = managementNumber; }
    public void setTrafficLightType(Short trafficLightType) { this.trafficLightType = trafficLightType; }
    public void setLightColorType(Short lightColorType) { this.lightColorType = lightColorType; }
    public void setLightingMethod(Short lightingMethod) { this.lightingMethod = lightingMethod; }
    public void setLightingSequence(String lightingSequence) { this.lightingSequence = lightingSequence; }
    public void setLightingDuration(Short lightingDuration) { this.lightingDuration = lightingDuration; }
    public void setLightSourceType(Short lightSourceType) { this.lightSourceType = lightSourceType; }
    public void setSignalControlMethod(Short signalControlMethod) { this.signalControlMethod = signalControlMethod; }
    public void setSignalTimeMethod(Short signalTimeMethod) { this.signalTimeMethod = signalTimeMethod; }
    public void setIsFlashingOperated(Boolean isFlashingOperated) { this.isFlashingOperated = isFlashingOperated; }
    public void setFlashingStartTime(LocalTime flashingStartTime) { this.flashingStartTime = flashingStartTime; }
    public void setFlashingEndTime(LocalTime flashingEndTime) { this.flashingEndTime = flashingEndTime; }
    public void setHasPedestrianActuated(Boolean hasPedestrianActuated) { this.hasPedestrianActuated = hasPedestrianActuated; }
    public void setHasCountdownDisplay(Boolean hasCountdownDisplay) { this.hasCountdownDisplay = hasCountdownDisplay; }
    public void setHasVisualImpairedSound(Boolean hasVisualImpairedSound) { this.hasVisualImpairedSound = hasVisualImpairedSound; }
    public void setRoadSignSerialNumber(String roadSignSerialNumber) { this.roadSignSerialNumber = roadSignSerialNumber; }
    public void setManagementAgencyName(String managementAgencyName) { this.managementAgencyName = managementAgencyName; }
    public void setManagementAgencyPhone(String managementAgencyPhone) { this.managementAgencyPhone = managementAgencyPhone; }
    public void setDataReferenceDate(LocalDate dataReferenceDate) { this.dataReferenceDate = dataReferenceDate; }
    public void setFacingDirection(BigDecimal facingDirection) { this.facingDirection = facingDirection; }
}
