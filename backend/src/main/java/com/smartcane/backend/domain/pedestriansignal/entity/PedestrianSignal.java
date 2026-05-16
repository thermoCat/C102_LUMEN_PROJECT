package com.smartcane.backend.domain.pedestriansignal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedestrian_signals")
public class PedestrianSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tot_dt")
    private LocalDateTime totDt;

    @Column(name = "stdg_cd", length = 20)
    private String stdgCd;

    @Column(name = "lclgv_nm", length = 100)
    private String lclgvNm;

    @Column(name = "crsrd_id", length = 20)
    private String crsrdId;

    @Column(name = "reg_id", length = 50)
    private String regId;

    @Column(name = "reg_dt")
    private LocalDate regDt;

    @Column(name = "nt_pdsg_rmnd_cs")
    private Integer ntPdsgRmndCs;

    @Column(name = "nt_pdsg_stts_nm", length = 100)
    private String ntPdsgSttsNm;

    @Column(name = "et_pdsg_rmnd_cs")
    private Integer etPdsgRmndCs;

    @Column(name = "et_pdsg_stts_nm", length = 100)
    private String etPdsgSttsNm;

    @Column(name = "st_pdsg_rmnd_cs")
    private Integer stPdsgRmndCs;

    @Column(name = "st_pdsg_stts_nm", length = 100)
    private String stPdsgSttsNm;

    @Column(name = "wt_pdsg_rmnd_cs")
    private Integer wtPdsgRmndCs;

    @Column(name = "wt_pdsg_stts_nm", length = 100)
    private String wtPdsgSttsNm;

    public PedestrianSignal() {}

    public Long getId() { return id; }
    public LocalDateTime getTotDt() { return totDt; }
    public String getStdgCd() { return stdgCd; }
    public String getLclgvNm() { return lclgvNm; }
    public String getCrsrdId() { return crsrdId; }
    public String getRegId() { return regId; }
    public LocalDate getRegDt() { return regDt; }
    public Integer getNtPdsgRmndCs() { return ntPdsgRmndCs; }
    public String getNtPdsgSttsNm() { return ntPdsgSttsNm; }
    public Integer getEtPdsgRmndCs() { return etPdsgRmndCs; }
    public String getEtPdsgSttsNm() { return etPdsgSttsNm; }
    public Integer getStPdsgRmndCs() { return stPdsgRmndCs; }
    public String getStPdsgSttsNm() { return stPdsgSttsNm; }
    public Integer getWtPdsgRmndCs() { return wtPdsgRmndCs; }
    public String getWtPdsgSttsNm() { return wtPdsgSttsNm; }

    public void setId(Long id) { this.id = id; }
    public void setTotDt(LocalDateTime totDt) { this.totDt = totDt; }
    public void setStdgCd(String stdgCd) { this.stdgCd = stdgCd; }
    public void setLclgvNm(String lclgvNm) { this.lclgvNm = lclgvNm; }
    public void setCrsrdId(String crsrdId) { this.crsrdId = crsrdId; }
    public void setRegId(String regId) { this.regId = regId; }
    public void setRegDt(LocalDate regDt) { this.regDt = regDt; }
    public void setNtPdsgRmndCs(Integer ntPdsgRmndCs) { this.ntPdsgRmndCs = ntPdsgRmndCs; }
    public void setNtPdsgSttsNm(String ntPdsgSttsNm) { this.ntPdsgSttsNm = ntPdsgSttsNm; }
    public void setEtPdsgRmndCs(Integer etPdsgRmndCs) { this.etPdsgRmndCs = etPdsgRmndCs; }
    public void setEtPdsgSttsNm(String etPdsgSttsNm) { this.etPdsgSttsNm = etPdsgSttsNm; }
    public void setStPdsgRmndCs(Integer stPdsgRmndCs) { this.stPdsgRmndCs = stPdsgRmndCs; }
    public void setStPdsgSttsNm(String stPdsgSttsNm) { this.stPdsgSttsNm = stPdsgSttsNm; }
    public void setWtPdsgRmndCs(Integer wtPdsgRmndCs) { this.wtPdsgRmndCs = wtPdsgRmndCs; }
    public void setWtPdsgSttsNm(String wtPdsgSttsNm) { this.wtPdsgSttsNm = wtPdsgSttsNm; }
}
