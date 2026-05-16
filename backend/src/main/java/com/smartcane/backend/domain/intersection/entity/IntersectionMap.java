package com.smartcane.backend.domain.intersection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "intersection_maps")
public class IntersectionMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stdg_cd", length = 20)
    private String stdgCd;

    @Column(name = "lclgv_nm", length = 100)
    private String lclgvNm;

    @Column(name = "crsrd_id", length = 20)
    private String crsrdId;

    @Column(name = "crsrd_nm", length = 100)
    private String crsrdNm;

    @Column(name = "map_ctpt_int_lat", precision = 14, scale = 10)
    private BigDecimal mapCtptIntLat;

    @Column(name = "map_ctpt_int_lot", precision = 14, scale = 10)
    private BigDecimal mapCtptIntLot;

    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "lane_wdth")
    private Integer laneWdth;

    @Column(name = "lmt_spd_type_nm", length = 100)
    private String lmtSpdTypeNm;

    @Column(name = "lmt_spd")
    private Integer lmtSpd;

    @Column(name = "crsrd_eng_nm", length = 100)
    private String crsrdEngNm;

    @Column(name = "reg_id", length = 50)
    private String regId;

    @Column(name = "reg_dt")
    private LocalDateTime regDt;

    @Column(name = "tot_dt")
    private LocalDateTime totDt;

    public IntersectionMap() {}

    public Long getId() { return id; }
    public String getStdgCd() { return stdgCd; }
    public String getLclgvNm() { return lclgvNm; }
    public String getCrsrdId() { return crsrdId; }
    public String getCrsrdNm() { return crsrdNm; }
    public BigDecimal getMapCtptIntLat() { return mapCtptIntLat; }
    public BigDecimal getMapCtptIntLot() { return mapCtptIntLot; }
    public Point getLocation() { return location; }
    public Integer getLaneWdth() { return laneWdth; }
    public String getLmtSpdTypeNm() { return lmtSpdTypeNm; }
    public Integer getLmtSpd() { return lmtSpd; }
    public String getCrsrdEngNm() { return crsrdEngNm; }
    public String getRegId() { return regId; }
    public LocalDateTime getRegDt() { return regDt; }
    public LocalDateTime getTotDt() { return totDt; }

    public void setId(Long id) { this.id = id; }
    public void setStdgCd(String stdgCd) { this.stdgCd = stdgCd; }
    public void setLclgvNm(String lclgvNm) { this.lclgvNm = lclgvNm; }
    public void setCrsrdId(String crsrdId) { this.crsrdId = crsrdId; }
    public void setCrsrdNm(String crsrdNm) { this.crsrdNm = crsrdNm; }
    public void setMapCtptIntLat(BigDecimal mapCtptIntLat) { this.mapCtptIntLat = mapCtptIntLat; }
    public void setMapCtptIntLot(BigDecimal mapCtptIntLot) { this.mapCtptIntLot = mapCtptIntLot; }
    public void setLocation(Point location) { this.location = location; }
    public void setLaneWdth(Integer laneWdth) { this.laneWdth = laneWdth; }
    public void setLmtSpdTypeNm(String lmtSpdTypeNm) { this.lmtSpdTypeNm = lmtSpdTypeNm; }
    public void setLmtSpd(Integer lmtSpd) { this.lmtSpd = lmtSpd; }
    public void setCrsrdEngNm(String crsrdEngNm) { this.crsrdEngNm = crsrdEngNm; }
    public void setRegId(String regId) { this.regId = regId; }
    public void setRegDt(LocalDateTime regDt) { this.regDt = regDt; }
    public void setTotDt(LocalDateTime totDt) { this.totDt = totDt; }
}
