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
}
