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

    @Size(max = 255)
    @NotNull
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "min")
    private BigDecimal min;

    @Column(name = "max")
    private BigDecimal max;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getMin() {
        return min;
    }

    public void setMin(BigDecimal min) {
        this.min = min;
    }

    public BigDecimal getMax() {
        return max;
    }

    public void setMax(BigDecimal max) {
        this.max = max;
    }

    public boolean isCompliant(BigDecimal numericValue) {
        if (numericValue == null) {
            return false;
        }
        if (min == null && max == null) {
            return true;
        }
        if (min != null && numericValue.compareTo(min) < 0) {
            return false;
        }
        if (max != null && numericValue.compareTo(max) > 0) {
            return false;
        }
        return true;
    }
}
