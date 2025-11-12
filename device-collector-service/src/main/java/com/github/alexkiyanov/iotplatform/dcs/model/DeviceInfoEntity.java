package com.github.alexkiyanov.iotplatform.dcs.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "device_info")
public class DeviceInfoEntity {
    @Id
    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "model")
    private String model;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "first_seen")
    private LocalDateTime firstSeen;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "status")
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private Map<String, Object> meta;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DeviceInfoEntity() {}

    public DeviceInfoEntity(String deviceId, String deviceType, String manufacturer, 
                     String model, String firmwareVersion, LocalDateTime firstSeen, 
                     LocalDateTime lastSeen, String status) {
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.manufacturer = manufacturer;
        this.model = model;
        this.firmwareVersion = firmwareVersion;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public DeviceInfoEntity(String deviceId, String deviceType, String manufacturer, 
                     String model, String firmwareVersion, LocalDateTime firstSeen, 
                     LocalDateTime lastSeen, String status, Map<String, Object> meta) {
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.manufacturer = manufacturer;
        this.model = model;
        this.firmwareVersion = firmwareVersion;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.status = status;
        this.meta = meta;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceInfoEntity that = (DeviceInfoEntity) o;
        return Objects.equals(deviceId, that.deviceId) &&
                Objects.equals(deviceType, that.deviceType) &&
                Objects.equals(manufacturer, that.manufacturer) &&
                Objects.equals(model, that.model) &&
                Objects.equals(firmwareVersion, that.firmwareVersion) &&
                Objects.equals(firstSeen, that.firstSeen) &&
                Objects.equals(lastSeen, that.lastSeen) &&
                Objects.equals(status, that.status) &&
                Objects.equals(meta, that.meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, deviceType, manufacturer, model, firmwareVersion, firstSeen, lastSeen, status, meta);
    }

    @Override
    public String toString() {
        return "DeviceInfoEntity{" +
                "deviceId='" + deviceId + '\'' +
                ", deviceType='" + deviceType + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", firmwareVersion='" + firmwareVersion + '\'' +
                ", firstSeen=" + firstSeen +
                ", lastSeen=" + lastSeen +
                ", status='" + status + '\'' +
                ", meta=" + meta +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
