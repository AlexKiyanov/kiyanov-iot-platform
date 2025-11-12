package com.github.alexkiyanov.iotplatform.dcs.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.alexkiyanov.iotplatform.avro.DeviceInfo;
import com.github.alexkiyanov.iotplatform.dcs.model.DeviceInfoEntity;
import com.github.alexkiyanov.iotplatform.dcs.repository.DeviceInfoRepository;
import com.github.alexkiyanov.iotplatform.dcs.service.DeviceInfoPublisher;
import com.github.alexkiyanov.iotplatform.dcs.service.PoisonMessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceIdListenerTest {

    @Mock
    private DeviceInfoRepository repository;

    @Mock
    private DeviceInfoPublisher publisher;

    @Mock
    private PoisonMessageHandler poisonMessageHandler;

    @Mock
    private Acknowledgment acknowledgment;

    private DeviceIdListener deviceIdListener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        deviceIdListener = new DeviceIdListener(repository, publisher, poisonMessageHandler, "device-id-topic", objectMapper);
    }

    @Test
    void onBatch_WhenEmptyList_ShouldNotProcess() {
        // Given
        List<DeviceInfo> emptyList = Collections.emptyList();

        // When
        deviceIdListener.onBatch(emptyList, "device-id-topic", 0, 100L, acknowledgment);

        // Then
        verify(repository, never()).upsertDeviceInfo(anyString(), anyString(), anyString(), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onBatch_WhenNullList_ShouldNotProcess() {
        // When
        deviceIdListener.onBatch(null, "device-id-topic", 0, 100L, acknowledgment);

        // Then
        verify(repository, never()).upsertDeviceInfo(anyString(), anyString(), anyString(), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onBatch_WhenValidDevice_ShouldUpsertAndPublish() {
        // Given
        long currentTime = System.currentTimeMillis();
        DeviceInfo avroDeviceInfo = DeviceInfo.newBuilder()
                .setDeviceId("device-1")
                .setDeviceType("sensor")
                .setManufacturer("TestCorp")
                .setModel("SensorX")
                .setFirmwareVersion("1.0.0")
                .setFirstSeen(currentTime)
                .setLastSeen(currentTime)
                .setStatus("active")
                .build();

        List<DeviceInfo> deviceInfos = Arrays.asList(avroDeviceInfo);

        // Mock upsert method
        doNothing().when(repository).upsertDeviceInfo(anyString(), anyString(), anyString(), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));

        // When
        deviceIdListener.onBatch(deviceInfos, "device-id-topic", 0, 100L, acknowledgment);

        // Then
        verify(repository).upsertDeviceInfo(anyString(), anyString(), anyString(), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(publisher).publishDeviceInfo(any(DeviceInfoEntity.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onBatch_WhenMultipleDevices_ShouldProcessAll() {
        // Given
        long currentTime = System.currentTimeMillis();
        DeviceInfo device1 = DeviceInfo.newBuilder()
                .setDeviceId("device-1")
                .setDeviceType("sensor")
                .setManufacturer("TestCorp")
                .setModel("SensorX")
                .setFirmwareVersion("1.0.0")
                .setFirstSeen(currentTime)
                .setLastSeen(currentTime)
                .setStatus("active")
                .build();

        DeviceInfo device2 = DeviceInfo.newBuilder()
                .setDeviceId("device-2")
                .setDeviceType("actuator")
                .setManufacturer("TestCorp")
                .setModel("ActuatorY")
                .setFirmwareVersion("1.5.0")
                .setFirstSeen(currentTime)
                .setLastSeen(currentTime)
                .setStatus("active")
                .build();

        List<DeviceInfo> deviceInfos = Arrays.asList(device1, device2);

        // Mock upsert method
        doNothing().when(repository).upsertDeviceInfo(anyString(), anyString(), anyString(), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));

        // When
        deviceIdListener.onBatch(deviceInfos, "device-id-topic", 0, 100L, acknowledgment);

        // Then
        verify(repository, times(2)).upsertDeviceInfo(anyString(), anyString(), anyString(), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(publisher, times(2)).publishDeviceInfo(any(DeviceInfoEntity.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onBatch_WhenNullDeviceInfo_ShouldSkip() {
        // Given
        DeviceInfo validDevice = DeviceInfo.newBuilder()
                .setDeviceId("device-1")
                .setDeviceType("sensor")
                .setManufacturer("TestCorp")
                .setModel("SensorX")
                .setFirmwareVersion("1.0.0")
                .setFirstSeen(System.currentTimeMillis())
                .setLastSeen(System.currentTimeMillis())
                .setStatus("active")
                .build();

        List<DeviceInfo> deviceInfos = Arrays.asList(null, validDevice, null);

        // Mock upsert method
        doNothing().when(repository).upsertDeviceInfo(anyString(), anyString(), anyString(), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));

        // When
        deviceIdListener.onBatch(deviceInfos, "device-id-topic", 0, 100L, acknowledgment);

        // Then
        verify(repository).upsertDeviceInfo(anyString(), anyString(), anyString(), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(publisher).publishDeviceInfo(any(DeviceInfoEntity.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onBatch_WhenDeviceIdIsEmpty_ShouldSkip() {
        // Given
        DeviceInfo deviceWithEmptyId = DeviceInfo.newBuilder()
                .setDeviceId("")  // Empty string
                .setDeviceType("sensor")
                .setManufacturer("TestCorp")
                .setModel("SensorX")
                .setFirmwareVersion("1.0.0")
                .setFirstSeen(System.currentTimeMillis())
                .setLastSeen(System.currentTimeMillis())
                .setStatus("active")
                .build();

        List<DeviceInfo> deviceInfos = Arrays.asList(deviceWithEmptyId);

        // When
        deviceIdListener.onBatch(deviceInfos, "device-id-topic", 0, 100L, acknowledgment);

        // Then
        verify(repository, never()).upsertDeviceInfo(anyString(), anyString(), anyString(), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), 
                anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(acknowledgment, never()).acknowledge();
    }
}