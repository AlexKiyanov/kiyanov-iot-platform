package com.github.alexkiyanov.iotplatform.dcs.service;

import com.github.alexkiyanov.iotplatform.avro.PoisonMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PoisonMessageHandlerTest {

    @Mock
    private KafkaTemplate<String, PoisonMessage> poisonMessageKafkaTemplate;
    
    @Mock
    private MeterRegistry meterRegistry;
    
    @Mock
    private Counter poisonMessagesCounter;
    
    @Mock
    private Counter dltMessagesCounter;
    
    @Mock
    private Counter retryAttemptsCounter;

    private PoisonMessageHandler poisonMessageHandler;

    @BeforeEach
    void setUp() {
        poisonMessageHandler = new PoisonMessageHandler(poisonMessageKafkaTemplate, meterRegistry);
        
        // Устанавливаем значения через ReflectionTestUtils
        ReflectionTestUtils.setField(poisonMessageHandler, "deadLetterTopic", "device-id-dlt");
        ReflectionTestUtils.setField(poisonMessageHandler, "poisonMessagesCounter", poisonMessagesCounter);
        ReflectionTestUtils.setField(poisonMessageHandler, "dltMessagesCounter", dltMessagesCounter);
        ReflectionTestUtils.setField(poisonMessageHandler, "retryAttemptsCounter", retryAttemptsCounter);
    }
    
    private PoisonMessage createValidPoisonMessage(String deviceId) {
        return PoisonMessage.newBuilder()
                .setOriginalTopic("test-topic")
                .setOriginalKey("test-key")
                .setOriginalValue(java.nio.ByteBuffer.wrap("test".getBytes()))
                .setErrorMessage("Test error")
                .setErrorType("TestException")
                .setRetryAttempts(1)
                .setFirstFailureTime(System.currentTimeMillis())
                .setLastFailureTime(System.currentTimeMillis())
                .setDeviceId(deviceId)
                .setMetadata("{}")
                .build();
    }

    @Test
    void handlePoisonMessage_WhenValidData_ShouldProcessSuccessfully() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = "valid-avro-data".getBytes(); // Достаточно длинное для валидации
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = "device-1";

        // When & Then - метод должен выполниться без исключений
        assertDoesNotThrow(() -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
        
        // Проверяем, что счетчик попыток увеличился
        verify(retryAttemptsCounter).increment();
    }

    @Test
    void handlePoisonMessage_WhenInvalidDeviceId_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = "valid-avro-data".getBytes();
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = ""; // Пустой deviceId

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
    }

    @Test
    void handlePoisonMessage_WhenInvalidAvroData_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = "x".getBytes(); // Слишком короткое для Avro
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = "device-1";

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
    }

    @Test
    void handlePoisonMessage_WhenDeviceIdWithInvalidChars_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = "valid-avro-data".getBytes();
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = "device@invalid"; // Недопустимые символы

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
    }

    @Test
    void sendToDeadLetterTopic_ShouldSendCorrectMessage() {
        // Given
        PoisonMessage poisonMessage = PoisonMessage.newBuilder()
                .setOriginalTopic("device-id-topic")
                .setOriginalKey("device-1")
                .setOriginalValue(java.nio.ByteBuffer.wrap("test-value".getBytes()))
                .setErrorMessage("Test error")
                .setErrorType("RuntimeException")
                .setRetryAttempts(3)
                .setDeviceId("device-1")
                .setFirstFailureTime(System.currentTimeMillis())
                .setLastFailureTime(System.currentTimeMillis())
                .setMetadata("{}")
                .build();

        when(poisonMessageKafkaTemplate.send(anyString(), anyString(), any(PoisonMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        poisonMessageHandler.sendToDeadLetterTopic(poisonMessage);

        // Then
        ArgumentCaptor<PoisonMessage> messageCaptor = ArgumentCaptor.forClass(PoisonMessage.class);
        verify(poisonMessageKafkaTemplate).send(eq("device-id-dlt"), eq("device-1"), messageCaptor.capture());
        
        PoisonMessage capturedMessage = messageCaptor.getValue();
        assertEquals("device-id-topic", capturedMessage.getOriginalTopic());
        assertEquals("device-1", capturedMessage.getOriginalKey());
        assertEquals("Test error", capturedMessage.getErrorMessage());
        assertEquals("RuntimeException", capturedMessage.getErrorType());
        assertEquals(3, capturedMessage.getRetryAttempts());
        assertEquals("device-1", capturedMessage.getDeviceId());
    }

    @Test
    void sendToDeadLetterTopic_WhenKafkaFails_ShouldLogError() {
        // Given
        PoisonMessage poisonMessage = createValidPoisonMessage("device-1");

        CompletableFuture<org.springframework.kafka.support.SendResult<String, PoisonMessage>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka error"));
        
        when(poisonMessageKafkaTemplate.send(anyString(), anyString(), any(PoisonMessage.class)))
                .thenReturn(failedFuture);

        // When & Then
        assertDoesNotThrow(() -> {
            poisonMessageHandler.sendToDeadLetterTopic(poisonMessage);
        });
        
        verify(poisonMessageKafkaTemplate).send(eq("device-id-dlt"), eq("device-1"), eq(poisonMessage));
    }

    @Test
    void sendToDeadLetterTopic_WhenDeviceIdIsNull_ShouldUseOriginalKey() {
        // Given
        PoisonMessage poisonMessage = PoisonMessage.newBuilder()
                .setOriginalTopic("test-topic")
                .setOriginalKey("original-key")
                .setOriginalValue(java.nio.ByteBuffer.wrap("test".getBytes()))
                .setErrorMessage("Test error")
                .setErrorType("TestException")
                .setRetryAttempts(1)
                .setFirstFailureTime(System.currentTimeMillis())
                .setLastFailureTime(System.currentTimeMillis())
                .setDeviceId(null)
                .setMetadata("{}")
                .build();

        when(poisonMessageKafkaTemplate.send(anyString(), anyString(), any(PoisonMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        poisonMessageHandler.sendToDeadLetterTopic(poisonMessage);

        // Then
        verify(poisonMessageKafkaTemplate).send(eq("device-id-dlt"), eq("original-key"), eq(poisonMessage));
    }

    @Test
    void sendToDeadLetterTopic_WhenBothDeviceIdAndKeyAreNull_ShouldUseNullKey() {
        // Given
        PoisonMessage poisonMessage = PoisonMessage.newBuilder()
                .setOriginalTopic("test-topic")
                .setOriginalKey("")
                .setOriginalValue(java.nio.ByteBuffer.wrap("test".getBytes()))
                .setErrorMessage("Test error")
                .setErrorType("TestException")
                .setRetryAttempts(1)
                .setFirstFailureTime(System.currentTimeMillis())
                .setLastFailureTime(System.currentTimeMillis())
                .setDeviceId(null)
                .setMetadata("{}")
                .build();

        when(poisonMessageKafkaTemplate.send(anyString(), anyString(), any(PoisonMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        poisonMessageHandler.sendToDeadLetterTopic(poisonMessage);

        // Then
        verify(poisonMessageKafkaTemplate).send(eq("device-id-dlt"), eq(""), eq(poisonMessage));
    }

    @Test
    void sendToDeadLetterTopic_WhenExceptionThrown_ShouldCatchAndLog() {
        // Given
        PoisonMessage poisonMessage = createValidPoisonMessage("device-1");

        when(poisonMessageKafkaTemplate.send(anyString(), anyString(), any(PoisonMessage.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        assertDoesNotThrow(() -> {
            poisonMessageHandler.sendToDeadLetterTopic(poisonMessage);
        });
        
        verify(poisonMessageKafkaTemplate).send(eq("device-id-dlt"), eq("device-1"), eq(poisonMessage));
    }

    @Test
    void extractDeviceId_WhenKeyIsNotNull_ShouldReturnKey() {
        // Given
        String key = "device-123";
        byte[] value = "test-value".getBytes();

        // When
        String deviceId = poisonMessageHandler.extractDeviceId(key, value);

        // Then
        assertEquals(key, deviceId);
    }

    @Test
    void extractDeviceId_WhenKeyIsNull_ShouldReturnGeneratedId() {
        // Given
        String key = null;
        byte[] value = "test-value".getBytes();

        // When
        String deviceId = poisonMessageHandler.extractDeviceId(key, value);

        // Then
        assertNotNull(deviceId);
        assertTrue(deviceId.startsWith("unknown-device-"));
    }

    @Test
    void extractDeviceId_WhenKeyIsEmpty_ShouldReturnGeneratedId() {
        // Given
        String key = "";
        byte[] value = "test-value".getBytes();

        // When
        String deviceId = poisonMessageHandler.extractDeviceId(key, value);

        // Then
        assertNotNull(deviceId);
        assertTrue(deviceId.startsWith("unknown-device-"));
    }

    @Test
    void extractDeviceId_WhenValueIsNull_ShouldReturnGeneratedId() {
        // Given
        String key = null;
        byte[] value = null;

        // When
        String deviceId = poisonMessageHandler.extractDeviceId(key, value);

        // Then
        assertNotNull(deviceId);
        assertTrue(deviceId.startsWith("unknown-device-"));
    }

    @Test
    void extractDeviceId_WhenValueIsEmpty_ShouldReturnGeneratedId() {
        // Given
        String key = null;
        byte[] value = new byte[0];

        // When
        String deviceId = poisonMessageHandler.extractDeviceId(key, value);

        // Then
        assertNotNull(deviceId);
        assertTrue(deviceId.startsWith("unknown-device-"));
    }

    @Test
    void validateDeviceId_WhenTooLong_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = "valid-avro-data".getBytes();
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = "a".repeat(256); // Превышает лимит в 255 символов

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
    }

    @Test
    void validateDeviceId_WhenContainsSpaces_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = "valid-avro-data".getBytes();
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = "device with spaces";

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
    }

    @Test
    void validateDeviceId_WhenContainsSpecialChars_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = "valid-avro-data".getBytes();
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = "device!@#$%^&*()";

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
    }

    @Test
    void validateAvroMessage_WhenTooShort_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = new byte[2]; // Слишком короткое сообщение
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = "device-1";

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
    }

    @Test
    void validateAvroMessage_WhenEmpty_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = new byte[0]; // Пустое сообщение
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = "device-1";

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
    }

    @Test
    void recover_WhenAllRetriesExhausted_ShouldSendToDeadLetterTopic() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = "test-value".getBytes();
        Exception error = new RuntimeException("All retries exhausted");
        int retryAttempt = 3;
        String deviceId = "device-1";

        when(poisonMessageKafkaTemplate.send(anyString(), anyString(), any(PoisonMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When - вызываем recover напрямую (это делается Spring Retry после исчерпания попыток)
        poisonMessageHandler.recover(error, originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);

        // Then
        ArgumentCaptor<PoisonMessage> messageCaptor = ArgumentCaptor.forClass(PoisonMessage.class);
        verify(poisonMessageKafkaTemplate).send(eq("device-id-dlt"), eq("device-1"), messageCaptor.capture());
        
        PoisonMessage capturedMessage = messageCaptor.getValue();
        assertEquals("device-id-topic", capturedMessage.getOriginalTopic());
        assertEquals("device-1", capturedMessage.getOriginalKey());
        assertEquals("All retries exhausted", capturedMessage.getErrorMessage());
        assertEquals(3, capturedMessage.getRetryAttempts());
        assertEquals("device-1", capturedMessage.getDeviceId());
        assertNotNull(capturedMessage.getFirstFailureTime());
        assertNotNull(capturedMessage.getMetadata());
        
        // Проверяем, что счетчик poison messages увеличился
        verify(poisonMessagesCounter).increment();
    }

    @Test
    void sendToDeadLetterTopic_WhenSuccessful_ShouldIncrementCounter() {
        // Given
        PoisonMessage poisonMessage = createValidPoisonMessage("device-1");
        
        org.springframework.kafka.support.SendResult<String, PoisonMessage> sendResult = 
            mock(org.springframework.kafka.support.SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata recordMetadata = 
            mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(recordMetadata.offset()).thenReturn(123L);

        CompletableFuture<org.springframework.kafka.support.SendResult<String, PoisonMessage>> successFuture = 
            CompletableFuture.completedFuture(sendResult);
        
        when(poisonMessageKafkaTemplate.send(anyString(), anyString(), any(PoisonMessage.class)))
                .thenReturn(successFuture);

        // When
        poisonMessageHandler.sendToDeadLetterTopic(poisonMessage);
        
        // Ждем завершения асинхронной операции
        try {
            Thread.sleep(100); // Небольшая пауза для завершения асинхронного callback
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        verify(poisonMessageKafkaTemplate).send(eq("device-id-dlt"), eq("device-1"), eq(poisonMessage));
        verify(dltMessagesCounter).increment();
    }

    @Test
    void recover_WhenMetadataCreated_ShouldContainCorrectFields() {
        // Given
        String originalTopic = "test-topic";
        String originalKey = "test-key";
        byte[] originalValue = "test-value".getBytes();
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 2;
        String deviceId = "test-device";

        when(poisonMessageKafkaTemplate.send(anyString(), anyString(), any(PoisonMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        poisonMessageHandler.recover(error, originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);

        // Then
        ArgumentCaptor<PoisonMessage> messageCaptor = ArgumentCaptor.forClass(PoisonMessage.class);
        verify(poisonMessageKafkaTemplate).send(eq("device-id-dlt"), eq("test-device"), messageCaptor.capture());
        
        PoisonMessage capturedMessage = messageCaptor.getValue();
        String metadataJson = capturedMessage.getMetadata();
        
        assertNotNull(metadataJson);
        assertTrue(metadataJson.contains("test-topic"));
        assertTrue(metadataJson.contains("test-key"));
        assertTrue(metadataJson.contains("device-collector-service"));
        assertTrue(metadataJson.contains("retry_exhausted"));
    }

    @Test
    void handlePoisonMessage_WhenNullAvroValue_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = null; // null значение
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = "device-1";

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
        
        // Проверяем, что счетчик попыток все равно увеличился
        verify(retryAttemptsCounter).increment();
    }

    @Test
    void handlePoisonMessage_WhenNullDeviceId_ShouldThrowException() {
        // Given
        String originalTopic = "device-id-topic";
        String originalKey = "device-1";
        byte[] originalValue = "valid-avro-data".getBytes();
        Exception error = new RuntimeException("Test error");
        int retryAttempt = 1;
        String deviceId = null; // null deviceId

        // When & Then - метод должен бросить исключение
        assertThrows(RuntimeException.class, () -> {
            poisonMessageHandler.handlePoisonMessage(originalTopic, originalKey, originalValue, error, retryAttempt, deviceId);
        });
        
        // Проверяем, что счетчик попыток все равно увеличился
        verify(retryAttemptsCounter).increment();
    }
}
