package com.github.alexkiyanov.iotplatform.service;

import com.github.alexkiyanov.iotplatform.avro.DeviceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProducerService {

    private final KafkaTemplate<String, DeviceEvent> kafkaTemplate;
    
    @Value("${app.topics.input}")
    private String topic;
    
    private final List<String> deviceIds = List.of(
        "device-001", "device-002", "device-003", "device-004", "device-005",
        "device-006", "device-007", "device-008", "device-009", "device-010"
    );
    
    private final Random random = new Random();
    
    private static final String[] EVENT_TYPES = {
        "TEMPERATURE_READING",
        "HUMIDITY_READING", 
        "PRESSURE_READING",
        "MOTION_DETECTED",
        "DOOR_OPENED",
        "WINDOW_OPENED",
        "LIGHT_ON",
        "LIGHT_OFF",
        "BATTERY_LOW",
        "CONNECTION_LOST"
    };
    
    private static final String[] PAYLOADS = {
        "{\"value\": 23.5, \"unit\": \"celsius\"}",
        "{\"value\": 65.2, \"unit\": \"percent\"}",
        "{\"value\": 1013.25, \"unit\": \"hPa\"}",
        "{\"motion\": true, \"confidence\": 0.95}",
        "{\"door\": \"front\", \"status\": \"open\"}",
        "{\"window\": \"living_room\", \"status\": \"open\"}",
        "{\"light\": \"ceiling\", \"status\": \"on\", \"brightness\": 80}",
        "{\"light\": \"ceiling\", \"status\": \"off\"}",
        "{\"battery\": 15, \"unit\": \"percent\"}",
        "{\"connection\": \"wifi\", \"status\": \"disconnected\"}"
    };

    @Scheduled(fixedRate = 1000) // 1000ms = 1 секунда
    public void produceEvent() {
        try {
            DeviceEvent event = createRandomEvent();
            kafkaTemplate.send(topic, event.getDeviceId(), event);
            log.info("Отправлено событие: deviceId={}, eventId={}, type={}", 
                    event.getDeviceId(), event.getEventId(), event.getType());
        } catch (Exception e) {
            log.error("Ошибка при отправке события: {}", e.getMessage(), e);
        }
    }
    
    private DeviceEvent createRandomEvent() {
        String deviceId = deviceIds.get(random.nextInt(deviceIds.size()));
        String eventType = EVENT_TYPES[random.nextInt(EVENT_TYPES.length)];
        String payload = PAYLOADS[random.nextInt(PAYLOADS.length)];
        
        return DeviceEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setDeviceId(deviceId)
                .setTimestamp(Instant.now().toEpochMilli())
                .setType(eventType)
                .setPayload(payload)
                .build();
    }
}
