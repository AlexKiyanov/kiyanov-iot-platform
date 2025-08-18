package com.github.alexkiyanov.iotplatform.ecs.model.cassandra;

import lombok.Getter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;

@PrimaryKeyClass
@Getter
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
}
