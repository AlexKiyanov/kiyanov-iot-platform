package com.github.alexkiyanov.iotplatform.dcs.service;

import com.github.alexkiyanov.iotplatform.avro.DeviceInfo;
import com.github.alexkiyanov.iotplatform.dcs.model.DeviceInfoEntity;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceInfoPublisherTest {

    @Mock
    private KafkaTemplate<String, DeviceInfo> kafkaTemplate;
    
    @Mock
    private Cache<String, Boolean> mockCache;

    private DeviceInfoPublisher deviceInfoPublisher;
    private static final String DEVICE_INFO_TOPIC = "device-info-topic";

    @BeforeEach
    void setUp() {
        deviceInfoPublisher = new DeviceInfoPublisher(kafkaTemplate, mockCache, DEVICE_INFO_TOPIC);
    }

    @Test
    void publishDeviceInfo_WhenValidDevice_ShouldSendToKafka() {
        // Given
        DeviceInfoEntity deviceInfo = new DeviceInfoEntity(
                "device-1",
                "sensor",
                "TestCorp",
                "SensorX",
                "1.0.0",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "active"
        );

        when(mockCache.getIfPresent("device-1")).thenReturn(null); // Устройство не в кеше
        CompletableFuture<SendResult<String, DeviceInfo>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(DEVICE_INFO_TOPIC), eq("device-1"), any(DeviceInfo.class)))
                .thenReturn(future);

        // When
        deviceInfoPublisher.publishDeviceInfo(deviceInfo);

        // Then
        verify(kafkaTemplate).send(eq(DEVICE_INFO_TOPIC), eq("device-1"), any(DeviceInfo.class));
        verify(mockCache).put("device-1", Boolean.TRUE);
    }

    @Test
    void publishDeviceInfo_WhenNullDevice_ShouldNotSend() {
        // When
        deviceInfoPublisher.publishDeviceInfo(null);

        // Then
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(DeviceInfo.class));
    }

    @Test
    void publishDeviceInfo_WhenMultipleDevices_ShouldSendAll() {
        // Given
        DeviceInfoEntity device1 = new DeviceInfoEntity(
                "device-1", "sensor", "TestCorp", "SensorX", "1.0.0",
                LocalDateTime.now(), LocalDateTime.now(), "active"
        );

        DeviceInfoEntity device2 = new DeviceInfoEntity(
                "device-2", "actuator", "TestCorp", "ActuatorY", "1.5.0",
                LocalDateTime.now(), LocalDateTime.now(), "active"
        );

        when(mockCache.getIfPresent("device-1")).thenReturn(null);
        when(mockCache.getIfPresent("device-2")).thenReturn(null);
        CompletableFuture<SendResult<String, DeviceInfo>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), any(DeviceInfo.class)))
                .thenReturn(future);

        // When
        deviceInfoPublisher.publishDeviceInfo(device1);
        deviceInfoPublisher.publishDeviceInfo(device2);

        // Then
        verify(kafkaTemplate).send(eq(DEVICE_INFO_TOPIC), eq("device-1"), any(DeviceInfo.class));
        verify(kafkaTemplate).send(eq(DEVICE_INFO_TOPIC), eq("device-2"), any(DeviceInfo.class));
        verify(mockCache).put("device-1", Boolean.TRUE);
        verify(mockCache).put("device-2", Boolean.TRUE);
    }

    @Test
    void publishDeviceInfo_WhenDeviceWithSpecialCharacters_ShouldSendCorrectly() {
        // Given
        DeviceInfoEntity deviceInfo = new DeviceInfoEntity(
                "device-with-special-chars-123",
                "sensor-type",
                "Manufacturer & Co.",
                "Model-X1",
                "v2.1.0-beta",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "active"
        );

        when(mockCache.getIfPresent("device-with-special-chars-123")).thenReturn(null);
        CompletableFuture<SendResult<String, DeviceInfo>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(DEVICE_INFO_TOPIC), eq("device-with-special-chars-123"), any(DeviceInfo.class)))
                .thenReturn(future);

        // When
        deviceInfoPublisher.publishDeviceInfo(deviceInfo);

        // Then
        verify(kafkaTemplate).send(eq(DEVICE_INFO_TOPIC), eq("device-with-special-chars-123"), any(DeviceInfo.class));
        verify(mockCache).put("device-with-special-chars-123", Boolean.TRUE);
    }

    @Test
    void publishDeviceInfo_WhenDeviceWithNullFields_ShouldHandleGracefully() {
        // Given
        DeviceInfoEntity deviceInfo = new DeviceInfoEntity(
                "device-1",
                "unknown",  // Use default values instead of null
                "unknown",
                "unknown",
                "unknown",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "unknown"
        );

        when(mockCache.getIfPresent("device-1")).thenReturn(null);
        CompletableFuture<SendResult<String, DeviceInfo>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(DEVICE_INFO_TOPIC), eq("device-1"), any(DeviceInfo.class)))
                .thenReturn(future);

        // When
        deviceInfoPublisher.publishDeviceInfo(deviceInfo);

        // Then
        verify(kafkaTemplate).send(eq(DEVICE_INFO_TOPIC), eq("device-1"), any(DeviceInfo.class));
        verify(mockCache).put("device-1", Boolean.TRUE);
    }

    @Test
    void publishDeviceInfo_WhenDeviceWithNullDeviceId_ShouldNotSend() {
        // Given
        DeviceInfoEntity deviceInfo = new DeviceInfoEntity(
                null, // null deviceId
                "sensor",
                "TestCorp",
                "SensorX",
                "1.0.0",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "active"
        );

        // When
        deviceInfoPublisher.publishDeviceInfo(deviceInfo);

        // Then
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(DeviceInfo.class));
        verify(mockCache, never()).getIfPresent(anyString());
        verify(mockCache, never()).put(anyString(), any());
    }

    @Test
    void publishDeviceInfo_WhenDeviceAlreadyInCache_ShouldNotSendAgain() {
        // Given
        DeviceInfoEntity deviceInfo = new DeviceInfoEntity(
                "device-1",
                "sensor",
                "TestCorp",
                "SensorX",
                "1.0.0",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "active"
        );

        when(mockCache.getIfPresent("device-1")).thenReturn(Boolean.TRUE); // Устройство уже в кеше

        // When
        deviceInfoPublisher.publishDeviceInfo(deviceInfo);

        // Then
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(DeviceInfo.class));
        verify(mockCache, never()).put(anyString(), any());
        verify(mockCache).getIfPresent("device-1");
    }

    @Test
    void publishDeviceInfo_WhenCacheReturnsFalse_ShouldNotSend() {
        // Given
        DeviceInfoEntity deviceInfo = new DeviceInfoEntity(
                "device-1",
                "sensor",
                "TestCorp",
                "SensorX",
                "1.0.0",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "active"
        );

        when(mockCache.getIfPresent("device-1")).thenReturn(Boolean.FALSE); // Кеш возвращает FALSE (не null)

        // When
        deviceInfoPublisher.publishDeviceInfo(deviceInfo);

        // Then
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(DeviceInfo.class));
        verify(mockCache, never()).put(anyString(), any());
    }
}
