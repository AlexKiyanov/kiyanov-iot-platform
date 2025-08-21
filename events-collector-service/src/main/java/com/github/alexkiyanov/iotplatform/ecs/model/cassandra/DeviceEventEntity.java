package com.github.alexkiyanov.iotplatform.ecs.model.cassandra;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("device_events_by_device")
public class DeviceEventEntity {
    @PrimaryKey
    private DeviceEventKey key;

    private Long timestamp;
    private String type;
    private String payload;

    public DeviceEventEntity() {
    }

    public DeviceEventEntity(DeviceEventKey key, Long timestamp, String type, String payload) {
        this.key = key;
        this.timestamp = timestamp;
        this.type = type;
        this.payload = payload;
    }

    public DeviceEventKey getKey() {
        return key;
    }

    public void setKey(DeviceEventKey key) {
        this.key = key;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
