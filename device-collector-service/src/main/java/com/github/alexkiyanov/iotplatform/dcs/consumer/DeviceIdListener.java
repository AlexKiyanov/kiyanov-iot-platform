package com.github.alexkiyanov.iotplatform.dcs.consumer;

import com.github.alexkiyanov.iotplatform.dcs.model.DeviceInfoEntity;
import com.github.alexkiyanov.iotplatform.dcs.repository.DeviceInfoRepository;
import com.github.alexkiyanov.iotplatform.dcs.service.DeviceInfoPublisher;
import com.github.alexkiyanov.iotplatform.dcs.service.PoisonMessageHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeviceIdListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceIdListener.class);

    private final DeviceInfoRepository repository;
    private final DeviceInfoPublisher publisher;
    private final PoisonMessageHandler poisonMessageHandler;
    private final String inputTopic;
    private final ObjectMapper objectMapper;

    public DeviceIdListener(DeviceInfoRepository repository,
                           DeviceInfoPublisher publisher,
                           PoisonMessageHandler poisonMessageHandler,
                           @Value("${app.topics.input}") String inputTopic,
                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.publisher = publisher;
        this.poisonMessageHandler = poisonMessageHandler;
        this.inputTopic = inputTopic;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "#{'${app.topics.input}'}", containerFactory = "kafkaListenerContainerFactory")
    public void onBatch(@Payload List<com.github.alexkiyanov.iotplatform.avro.DeviceInfo> deviceInfos, 
                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                       @Header(KafkaHeaders.OFFSET) long offset,
                       Acknowledgment ack) {
        if (deviceInfos == null || deviceInfos.isEmpty()) {
            return;
        }
        log.info("Received batch: {} device infos from {}", deviceInfos.size(), inputTopic);

        List<com.github.alexkiyanov.iotplatform.avro.DeviceInfo> successfulMessages = new ArrayList<>();
        List<com.github.alexkiyanov.iotplatform.avro.DeviceInfo> poisonMessages = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        
        // Разделяем сообщения на успешные и poison
        for (com.github.alexkiyanov.iotplatform.avro.DeviceInfo avroDeviceInfo : deviceInfos) {
            if (avroDeviceInfo == null
                    || avroDeviceInfo.getDeviceId() == null
                    || avroDeviceInfo.getDeviceId().trim().isEmpty()) {
                poisonMessages.add(avroDeviceInfo);
                continue;
            }

            try {
                processDeviceInfo(avroDeviceInfo, now);
                successfulMessages.add(avroDeviceInfo);
                log.debug("Successfully processed device info for deviceId={}", avroDeviceInfo.getDeviceId());
                
            } catch (Exception e) {
                log.error("Failed to process device info for deviceId={}: {}", avroDeviceInfo.getDeviceId(), e.getMessage());
                poisonMessages.add(avroDeviceInfo);
            }
        }

        // Обрабатываем poison messages отдельно
        if (!poisonMessages.isEmpty()) {
            log.warn("Found {} poison messages in batch, processing separately", poisonMessages.size());
            processPoisonMessages(poisonMessages, topic, partition, offset);
        }

        // Подтверждаем только успешно обработанные сообщения
        if (!successfulMessages.isEmpty()) {
            ack.acknowledge();
            log.info("Acknowledged {} successful messages", successfulMessages.size());
        }
    }

    /**
     * Обрабатывает одно сообщение с информацией об устройстве
     */
    private void processDeviceInfo(com.github.alexkiyanov.iotplatform.avro.DeviceInfo avroDeviceInfo, LocalDateTime now) 
            throws JsonProcessingException {
        String deviceId = avroDeviceInfo.getDeviceId();
        
        // Создаем метаданные из Avro объекта
        Map<String, Object> meta = createMetaFromAvro(avroDeviceInfo);
        String metaJson = objectMapper.writeValueAsString(meta);
        
        // Конвертируем timestamp в LocalDateTime
        LocalDateTime firstSeen = convertTimestampToLocalDateTime(avroDeviceInfo.getFirstSeen());
        LocalDateTime lastSeen = convertTimestampToLocalDateTime(avroDeviceInfo.getLastSeen());
        
        // Выполняем upsert на уровне базы данных
        repository.upsertDeviceInfo(
            deviceId,
            avroDeviceInfo.getDeviceType(),
            avroDeviceInfo.getManufacturer(),
            avroDeviceInfo.getModel(),
            avroDeviceInfo.getFirmwareVersion(),
            firstSeen,
            lastSeen,
            avroDeviceInfo.getStatus(),
            metaJson,
            now,
            now
        );
        
        // Создаем Entity для публикации
        DeviceInfoEntity deviceEntity = new DeviceInfoEntity(
            deviceId,
            avroDeviceInfo.getDeviceType(),
            avroDeviceInfo.getManufacturer(),
            avroDeviceInfo.getModel(),
            avroDeviceInfo.getFirmwareVersion(),
            firstSeen,
            lastSeen,
            avroDeviceInfo.getStatus(),
            meta
        );
        
        publisher.publishDeviceInfo(deviceEntity);
    }
    
    /**
     * Конвертирует timestamp в LocalDateTime
     */
    private LocalDateTime convertTimestampToLocalDateTime(long timestamp) {
        return LocalDateTime.ofEpochSecond(timestamp / 1000, (int)((timestamp % 1000) * 1_000_000), ZoneOffset.UTC);
    }

    /**
     * Обрабатывает poison messages с retry механизмом
     */
    private void processPoisonMessages(List<com.github.alexkiyanov.iotplatform.avro.DeviceInfo> poisonMessages, 
                                     String topic, int partition, long offset) {
        for (com.github.alexkiyanov.iotplatform.avro.DeviceInfo poisonMessage : poisonMessages) {
            try {
                String deviceId = poisonMessage != null && poisonMessage.getDeviceId() != null ? 
                    poisonMessage.getDeviceId() : 
                    poisonMessageHandler.extractDeviceId(null, null);
                
                // Создаем ключ для DLT
                String key = deviceId != null ? deviceId : "unknown-device-" + System.currentTimeMillis();
                
                // Симулируем получение значения (в реальности это будет сериализованное Avro сообщение)
                byte[] value = new byte[0]; // В реальности это будет сериализованное Avro сообщение
                
                // Определяем тип ошибки
                RuntimeException error;
                if (poisonMessage == null) {
                    error = new RuntimeException("Null message received");
                } else if (poisonMessage.getDeviceId() == null || poisonMessage.getDeviceId().trim().isEmpty()) {
                    error = new RuntimeException("Empty or null deviceId");
                } else {
                    error = new RuntimeException("Invalid message format");
                }
                
                // Обрабатываем poison message с retry
                poisonMessageHandler.handlePoisonMessage(topic, key, value, error, 3, deviceId);
                
            } catch (Exception e) {
                log.error("Failed to process poison message: {}", e.getMessage(), e);
            }
        }
    }
    
    private Map<String, Object> createMetaFromAvro(com.github.alexkiyanov.iotplatform.avro.DeviceInfo avroDeviceInfo) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "device-collector-service");
        meta.put("processed_at", System.currentTimeMillis());
        meta.put("avro_schema_version", "1.0");
        return meta;
    }
}
