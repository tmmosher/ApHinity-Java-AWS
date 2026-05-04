package com.aphinity.client_analytics_core.api.core.entities.dashboard;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Entity
@Table(name = "measurement_bounds")
public class MeasurementBound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 255)
    @NotNull
    @Column(name = "measurement_name", nullable = false)
    private String measurementName;

    @Column(name = "critical_range_min")
    private BigDecimal criticalRangeMin;

    @Column(name = "critical_range_max")
    private BigDecimal criticalRangeMax;

    @Column(name = "utility_range_min")
    private BigDecimal utilityRangeMin;

    @Column(name = "utility_range_max")
    private BigDecimal utilityRangeMax;

    @Column(name = "potable_range_min")
    private BigDecimal potableRangeMin;

    @Column(name = "potable_range_max")
    private BigDecimal potableRangeMax;

    @Column(name = "towers_range_min")
    private BigDecimal towersRangeMin;

    @Column(name = "towers_range_max")
    private BigDecimal towersRangeMax;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMeasurementName() {
        return measurementName;
    }

    public void setMeasurementName(String measurementName) {
        this.measurementName = measurementName;
    }

    public BigDecimal getCriticalRangeMin() {
        return criticalRangeMin;
    }

    public void setCriticalRangeMin(BigDecimal criticalRangeMin) {
        this.criticalRangeMin = criticalRangeMin;
    }

    public BigDecimal getCriticalRangeMax() {
        return criticalRangeMax;
    }

    public void setCriticalRangeMax(BigDecimal criticalRangeMax) {
        this.criticalRangeMax = criticalRangeMax;
    }

    public BigDecimal getUtilityRangeMin() {
        return utilityRangeMin;
    }

    public void setUtilityRangeMin(BigDecimal utilityRangeMin) {
        this.utilityRangeMin = utilityRangeMin;
    }

    public BigDecimal getUtilityRangeMax() {
        return utilityRangeMax;
    }

    public void setUtilityRangeMax(BigDecimal utilityRangeMax) {
        this.utilityRangeMax = utilityRangeMax;
    }

    public BigDecimal getPotableRangeMin() {
        return potableRangeMin;
    }

    public void setPotableRangeMin(BigDecimal potableRangeMin) {
        this.potableRangeMin = potableRangeMin;
    }

    public BigDecimal getPotableRangeMax() {
        return potableRangeMax;
    }

    public void setPotableRangeMax(BigDecimal potableRangeMax) {
        this.potableRangeMax = potableRangeMax;
    }

    public BigDecimal getTowersRangeMin() {
        return towersRangeMin;
    }

    public void setTowersRangeMin(BigDecimal towersRangeMin) {
        this.towersRangeMin = towersRangeMin;
    }

    public BigDecimal getTowersRangeMax() {
        return towersRangeMax;
    }

    public void setTowersRangeMax(BigDecimal towersRangeMax) {
        this.towersRangeMax = towersRangeMax;
    }

}