package com.github.alexkiyanov.iotplatform.ecs.service;

import com.github.alexkiyanov.iotplatform.avro.DeviceInfo;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceIdPublisherTest {

    @Mock
    private KafkaTemplate<String, DeviceInfo> kafkaTemplate;

    @Mock
    private Cache<String, Boolean> cache;

    private DeviceIdPublisher deviceIdPublisher;
    private static final String DEVICE_ID_TOPIC = "device-id-topic";

    @BeforeEach
    void setUp() {
        deviceIdPublisher = new DeviceIdPublisher(kafkaTemplate, cache, DEVICE_ID_TOPIC);
    }

    @Test
    void publishIfNew_WhenDeviceIdIsNull_ShouldNotPublish() {
        // When
        deviceIdPublisher.publishIfNew(null);

        // Then
        verifyNoInteractions(cache);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publishIfNew_WhenDeviceIdIsNew_ShouldPublishAndCache() {
        // Given
        String deviceId = "device-123";
        when(cache.getIfPresent(deviceId)).thenReturn(null);
        
        CompletableFuture<SendResult<String, DeviceInfo>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(DEVICE_ID_TOPIC), eq(deviceId), any(DeviceInfo.class))).thenReturn(future);

        // When
        deviceIdPublisher.publishIfNew(deviceId);

        // Then
        verify(cache).getIfPresent(deviceId);
        verify(cache).put(deviceId, Boolean.TRUE);
        verify(kafkaTemplate).send(eq(DEVICE_ID_TOPIC), eq(deviceId), any(DeviceInfo.class));
    }

    @Test
    void publishIfNew_WhenDeviceIdAlreadyExists_ShouldNotPublish() {
        // Given
        String deviceId = "device-123";
        when(cache.getIfPresent(deviceId)).thenReturn(Boolean.TRUE);

        // When
        deviceIdPublisher.publishIfNew(deviceId);

        // Then
        verify(cache).getIfPresent(deviceId);
        verify(cache, never()).put(anyString(), any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publishIfNew_WhenDeviceIdIsEmptyString_ShouldPublishAndCache() {
        // Given
        String deviceId = "";
        when(cache.getIfPresent(deviceId)).thenReturn(null);
        
        CompletableFuture<SendResult<String, DeviceInfo>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(DEVICE_ID_TOPIC), eq(deviceId), any(DeviceInfo.class))).thenReturn(future);

        // When
        deviceIdPublisher.publishIfNew(deviceId);

        // Then
        verify(cache).getIfPresent(deviceId);
        verify(cache).put(deviceId, Boolean.TRUE);
        verify(kafkaTemplate).send(eq(DEVICE_ID_TOPIC), eq(deviceId), any(DeviceInfo.class));
    }

    @Test
    void publishIfNew_WhenMultipleNewDeviceIds_ShouldPublishAll() {
        // Given
        String deviceId1 = "device-1";
        String deviceId2 = "device-2";
        
        when(cache.getIfPresent(deviceId1)).thenReturn(null);
        when(cache.getIfPresent(deviceId2)).thenReturn(null);
        
        CompletableFuture<SendResult<String, DeviceInfo>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), any(DeviceInfo.class))).thenReturn(future);

        // When
        deviceIdPublisher.publishIfNew(deviceId1);
        deviceIdPublisher.publishIfNew(deviceId2);

        // Then
        verify(cache).getIfPresent(deviceId1);
        verify(cache).getIfPresent(deviceId2);
        verify(cache).put(deviceId1, Boolean.TRUE);
        verify(cache).put(deviceId2, Boolean.TRUE);
        verify(kafkaTemplate).send(eq(DEVICE_ID_TOPIC), eq(deviceId1), any(DeviceInfo.class));
        verify(kafkaTemplate).send(eq(DEVICE_ID_TOPIC), eq(deviceId2), any(DeviceInfo.class));
    }

    @Test
    void publishIfNew_WhenDeviceIdIsCachedAsFalse_ShouldNotPublish() {
        // Given
        String deviceId = "device-123";
        when(cache.getIfPresent(deviceId)).thenReturn(Boolean.FALSE);

        // When
        deviceIdPublisher.publishIfNew(deviceId);

        // Then
        verify(cache).getIfPresent(deviceId);
        verify(cache, never()).put(anyString(), any());
        verifyNoInteractions(kafkaTemplate);
    }
}
