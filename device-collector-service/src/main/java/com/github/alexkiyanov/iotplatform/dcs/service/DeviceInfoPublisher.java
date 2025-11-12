package com.github.alexkiyanov.iotplatform.dcs.service;

import com.github.alexkiyanov.iotplatform.avro.DeviceInfo;
import com.github.alexkiyanov.iotplatform.dcs.model.DeviceInfoEntity;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;

@Service
public class DeviceInfoPublisher {
    private static final Logger log = LoggerFactory.getLogger(DeviceInfoPublisher.class);

    private final KafkaTemplate<String, DeviceInfo> template;
    private final Cache<String, Boolean> cache;
    private final String outputTopic;

    public DeviceInfoPublisher(KafkaTemplate<String, DeviceInfo> avroKafkaTemplate,
                              Cache<String, Boolean> cache,
                              @Value("${app.topics.output}") String outputTopic) {
        this.template = avroKafkaTemplate;
        this.cache = cache;
        this.outputTopic = outputTopic;
    }

    public void publishDeviceInfo(DeviceInfoEntity deviceInfo) {
        if (deviceInfo == null || deviceInfo.getDeviceId() == null) {
            return;
        }

        String deviceId = deviceInfo.getDeviceId();
        final Boolean prev = cache.getIfPresent(deviceId);

        if (prev == null) {
            cache.put(deviceId, Boolean.TRUE);
            
            // Создаем Avro объект
            DeviceInfo avroDeviceInfo = DeviceInfo.newBuilder()
                    .setDeviceId(deviceInfo.getDeviceId())
                    .setDeviceType(deviceInfo.getDeviceType())
                    .setManufacturer(deviceInfo.getManufacturer())
                    .setModel(deviceInfo.getModel())
                    .setFirmwareVersion(deviceInfo.getFirmwareVersion())
                    .setFirstSeen(deviceInfo.getFirstSeen().toEpochSecond(ZoneOffset.UTC) * 1000)
                    .setLastSeen(deviceInfo.getLastSeen().toEpochSecond(ZoneOffset.UTC) * 1000)
                    .setStatus(deviceInfo.getStatus())
                    .build();

            log.debug("Publishing device info for deviceId={}", deviceId);
            template.send(outputTopic, deviceId, avroDeviceInfo);
        } else {
            log.trace("Device info for deviceId={} already published", deviceId);
        }
    }
}
