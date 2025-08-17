package com.github.alexkiyanov.iotplatform.ecs;

import com.github.alexkiyanov.iotplatform.ecs.avro.DeviceEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AvroClassTest {

    @Test
    public void testDeviceEventCreation() {
        DeviceEvent event = DeviceEvent.newBuilder()
                .setEventId("test-event-1")
                .setDeviceId("device-001")
                .setTimestamp(System.currentTimeMillis())
                .setType("temperature")
                .setPayload("25.5")
                .build();

        assertEquals("test-event-1", event.getEventId());
        assertEquals("device-001", event.getDeviceId());
        assertEquals("temperature", event.getType());
        assertEquals("25.5", event.getPayload());
        assertTrue(event.getTimestamp() > 0);
        assertNotNull(event);
    }

    @Test
    public void testDeviceEventSchema() {
        assertNotNull(DeviceEvent.getClassSchema());
        assertNotNull(DeviceEvent.SCHEMA$);
        assertEquals("DeviceEvent", DeviceEvent.SCHEMA$.getName());
        assertEquals("com.github.alexkiyanov.iotplatform.ecs.avro", DeviceEvent.SCHEMA$.getNamespace());
    }
}
