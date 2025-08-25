package com.github.alexkiyanov.iotplatform.ecs.service;

import com.github.benmanes.caffeine.cache.Cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DeviceIdPublisher {
    private static final Logger log = LoggerFactory.getLogger(DeviceIdPublisher.class);

    private final KafkaTemplate<String, String> template;
    private final Cache<String, Boolean> cache;
    private final String deviceIdTopic;

    public DeviceIdPublisher(KafkaTemplate<String, String> stringKafkaTemplate,
                             Cache<String, Boolean> cache,
                             @Value("${app.topics.deviceId}") String deviceIdTopic) {
        this.template = stringKafkaTemplate;
        this.cache = cache;
        this.deviceIdTopic = deviceIdTopic;
    }

    public void publishIfNew(String deviceId) {
        if (deviceId == null) {
            return;
        }
        
        final Boolean prev = cache.getIfPresent(deviceId);

        if (prev == null) {
            cache.put(deviceId, Boolean.TRUE);
            log.debug("Publishing new deviceId={}", deviceId);
            template.send(deviceIdTopic, deviceId);
        } else {
            log.trace("deviceId={} already published", deviceId);
        }
    }
}
