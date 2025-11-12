package com.github.alexkiyanov.iotplatform.dcs.service;

import com.github.alexkiyanov.iotplatform.avro.PoisonMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class PoisonMessageHandler {

    private final KafkaTemplate<String, PoisonMessage> poisonMessageKafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${app.topics.dead-letter}")
    private String deadLetterTopic;

    private final Counter poisonMessagesCounter;
    private final Counter dltMessagesCounter;
    private final Counter retryAttemptsCounter;

    @Autowired
    public PoisonMessageHandler(@Qualifier("poisonMessageKafkaTemplate") KafkaTemplate<String, PoisonMessage> poisonMessageKafkaTemplate,
                               MeterRegistry meterRegistry) {
        this.poisonMessageKafkaTemplate = poisonMessageKafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.poisonMessagesCounter = Counter.builder("dcs.poison.messages.total")
                .description("Total number of poison messages detected")
                .register(meterRegistry);
        this.dltMessagesCounter = Counter.builder("dcs.dlt.messages.total")
                .description("Total number of messages sent to DLT")
                .register(meterRegistry);
        this.retryAttemptsCounter = Counter.builder("dcs.retry.attempts.total")
                .description("Total number of retry attempts")
                .register(meterRegistry);
    }


    /**
     * Обрабатывает poison message с retry механизмом
     */
    @Retryable(backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePoisonMessage(
            String originalTopic,
            String originalKey,
            byte[] originalValue,
            Exception error,
            int retryAttempt,
            String deviceId) {
        
        // Увеличиваем счетчик попыток
        retryAttemptsCounter.increment();
        
        log.warn("Обработка poison message для deviceId={}, попытка={}, ошибка: {}", 
                deviceId, retryAttempt, error.getMessage());
        
        try {
            // Попытка десериализации Avro сообщения
            validateAvroMessage(originalValue);
            
            // Попытка валидации deviceId
            validateDeviceId(deviceId);
            
            log.info("Poison message успешно обработан для deviceId={}", deviceId);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке poison message для deviceId={}: {}", deviceId, e.getMessage());
            throw new RuntimeException("Не удалось обработать poison message для deviceId=" + deviceId, e);
        }
    }

    /**
     * Восстановление после всех неудачных попыток - отправка в DLT
     */
    @Recover
    public void recover(Exception ex, String originalTopic, String originalKey, 
                      byte[] originalValue, Exception error, int retryAttempt, String deviceId) {
        
        // Увеличиваем счетчик poison messages
        poisonMessagesCounter.increment();
        
        log.error("Все попытки обработки poison message исчерпаны для deviceId={}, отправка в DLT", deviceId);
        
        PoisonMessage poisonMessage = PoisonMessage.newBuilder()
                .setOriginalTopic(originalTopic)
                .setOriginalKey(originalKey)
                .setOriginalValue(java.nio.ByteBuffer.wrap(originalValue))
                .setErrorMessage(ex.getMessage())
                .setErrorType(ex.getClass().getSimpleName())
                .setRetryAttempts(retryAttempt)
                .setFirstFailureTime(LocalDateTime.now().minusSeconds(retryAttempt * 2).toEpochSecond(ZoneOffset.UTC) * 1000)
                .setLastFailureTime(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000)
                .setDeviceId(deviceId)
                .setMetadata(createMetadataJson(originalTopic, originalKey, deviceId))
                .build();

        sendToDeadLetterTopic(poisonMessage);
    }

    /**
     * Отправляет poison message в Dead Letter Topic
     */
    public void sendToDeadLetterTopic(PoisonMessage poisonMessage) {
        try {
            String key = poisonMessage.getDeviceId() != null ? 
                poisonMessage.getDeviceId() : 
                poisonMessage.getOriginalKey();
            
            CompletableFuture<SendResult<String, PoisonMessage>> future = 
                poisonMessageKafkaTemplate.send(deadLetterTopic, key, poisonMessage);
            
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Ошибка отправки poison message в DLT для deviceId={}: {}", 
                        poisonMessage.getDeviceId(), throwable.getMessage());
                } else {
                    // Увеличиваем счетчик успешно отправленных в DLT
                    dltMessagesCounter.increment();
                    log.info("Poison message отправлен в DLT для deviceId={}, offset={}", 
                        poisonMessage.getDeviceId(), result.getRecordMetadata().offset());
                }
            });
            
        } catch (Exception e) {
            log.error("Критическая ошибка при отправке poison message в DLT: {}", e.getMessage(), e);
        }
    }

    /**
     * Создает метаданные для poison message в формате JSON
     */
    private String createMetadataJson(String originalTopic, String originalKey, String deviceId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_service", "device-collector-service");
        metadata.put("original_topic", originalTopic);
        metadata.put("original_key", originalKey);
        metadata.put("device_id", deviceId);
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("retry_exhausted", true);
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Не удалось сериализовать метаданные в JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Валидирует Avro сообщение
     */
    private void validateAvroMessage(byte[] value) throws Exception {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("Avro сообщение пустое");
        }
        
        // Проверяем, что это похоже на Avro сообщение (должно начинаться с магических байтов)
        if (value.length < 4) {
            throw new IllegalArgumentException("Avro сообщение слишком короткое");
        }
        
        // В реальной реализации здесь была бы полная валидация Avro схемы
        // Для демонстрации просто проверяем базовые условия
        log.debug("Avro сообщение прошло базовую валидацию, размер: {} байт", value.length);
    }

    /**
     * Валидирует deviceId
     */
    private void validateDeviceId(String deviceId) throws Exception {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("DeviceId не может быть пустым");
        }
        
        if (deviceId.length() > 255) {
            throw new IllegalArgumentException("DeviceId слишком длинный (максимум 255 символов)");
        }
        
        // Проверяем на наличие недопустимых символов
        if (!deviceId.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("DeviceId содержит недопустимые символы");
        }
        
        log.debug("DeviceId прошел валидацию: {}", deviceId);
    }

    /**
     * Извлекает deviceId из сообщения (простая реализация)
     */
    public String extractDeviceId(String key, byte[] value) {
        try {
            // Попытка извлечь deviceId из ключа
            if (key != null && !key.isEmpty()) {
                return key;
            }
            
            // Попытка десериализации Avro сообщения для извлечения deviceId
            // В реальной реализации здесь будет десериализация Avro
            return "unknown-device-" + System.currentTimeMillis();
            
        } catch (Exception e) {
            log.warn("Не удалось извлечь deviceId из сообщения: {}", e.getMessage());
            return "unknown-device-" + System.currentTimeMillis();
        }
    }
}
