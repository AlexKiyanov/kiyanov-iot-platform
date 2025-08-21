package com.github.alexkiyanov.iotplatform.ecs.model.cassandra;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.util.Objects;

@PrimaryKeyClass
public class DeviceEventKey implements Serializable {
    @PrimaryKeyColumn(name = "device_id", type = PrimaryKeyType.PARTITIONED)
    private String deviceId;

    @PrimaryKeyColumn(name = "event_id", ordinal = 0, type = PrimaryKeyType.CLUSTERED)
    private String eventId;

    public DeviceEventKey() {}

    public DeviceEventKey(String deviceId, String eventId) {
        this.deviceId = deviceId;
        this.eventId = eventId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceEventKey that = (DeviceEventKey) o;
        return Objects.equals(deviceId, that.deviceId) && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, eventId);
    }
}
