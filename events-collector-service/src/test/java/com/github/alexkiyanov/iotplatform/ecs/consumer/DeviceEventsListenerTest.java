package com.github.alexkiyanov.iotplatform.ecs.consumer;

import com.github.alexkiyanov.iotplatform.avro.DeviceEvent;
import com.github.alexkiyanov.iotplatform.ecs.model.cassandra.DeviceEventEntity;
import com.github.alexkiyanov.iotplatform.ecs.repository.DeviceEventRepository;
import com.github.alexkiyanov.iotplatform.ecs.service.DeviceIdPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeviceEventsListenerTest {

    @Mock
    private DeviceEventRepository deviceEventRepository;

    @Mock
    private DeviceIdPublisher deviceIdPublisher;

    @Mock
    private Acknowledgment acknowledgment;

    @Captor
    private ArgumentCaptor<List<DeviceEventEntity>> entitiesCaptor;

    private DeviceEventsListener deviceEventsListener;
    private static final String INPUT_TOPIC = "input-topic";

    @BeforeEach
    void setUp() {
        deviceEventsListener = new DeviceEventsListener(deviceEventRepository, deviceIdPublisher, INPUT_TOPIC);
    }

    @Test
    void onBatch_WhenEventsListIsNull_ShouldNotProcess() {
        // When
        deviceEventsListener.onBatch(null, acknowledgment);

        // Then
        verifyNoInteractions(deviceEventRepository);
        verifyNoInteractions(deviceIdPublisher);
        verifyNoInteractions(acknowledgment);
    }

    @Test
    void onBatch_WhenEventsListIsEmpty_ShouldNotProcess() {
        // When
        deviceEventsListener.onBatch(Collections.emptyList(), acknowledgment);

        // Then
        verifyNoInteractions(deviceEventRepository);
        verifyNoInteractions(deviceIdPublisher);
        verifyNoInteractions(acknowledgment);
    }

    @Test
    void onBatch_WhenSingleEvent_ShouldProcessCorrectly() {
        // Given
        DeviceEvent event = createDeviceEvent("device-1", "SENSOR", 1000L, "25.5");
        List<DeviceEvent> events = Collections.singletonList(event);

        // When
        deviceEventsListener.onBatch(events, acknowledgment);

        // Then
        verify(deviceEventRepository).saveAll(entitiesCaptor.capture());
        verify(deviceIdPublisher).publishIfNew("device-1");
        verify(acknowledgment).acknowledge();

        List<DeviceEventEntity> savedEntities = entitiesCaptor.getValue();
        assertThat(savedEntities).hasSize(1);
        
        DeviceEventEntity savedEntity = savedEntities.getFirst();
        assertThat(savedEntity.getKey().getEventId()).startsWith("device-1-1000-");
        assertThat(savedEntity.getKey().getDeviceId()).isEqualTo("device-1");
        assertThat(savedEntity.getTimestamp()).isEqualTo(1000L);
        assertThat(savedEntity.getType()).isEqualTo("SENSOR");
        assertThat(savedEntity.getPayload()).isEqualTo("25.5");
    }

    @Test
    void onBatch_WhenMultipleEvents_ShouldProcessAllCorrectly() {
        // Given
        DeviceEvent event1 = createDeviceEvent("device-1", "SENSOR", 1000L, "25.5");
        DeviceEvent event2 = createDeviceEvent("device-1", "SENSOR", 1001L, "60.0");
        DeviceEvent event3 = createDeviceEvent("device-2", "SENSOR", 1002L, "26.0");
        
        List<DeviceEvent> events = Arrays.asList(event1, event2, event3);

        // When
        deviceEventsListener.onBatch(events, acknowledgment);

        // Then
        verify(deviceEventRepository).saveAll(entitiesCaptor.capture());
        verify(deviceIdPublisher).publishIfNew("device-1");
        verify(deviceIdPublisher).publishIfNew("device-2");
        verify(acknowledgment).acknowledge();

        List<DeviceEventEntity> savedEntities = entitiesCaptor.getValue();
        assertThat(savedEntities).hasSize(3);
        
        // Verify first entity
        DeviceEventEntity savedEntity1 = savedEntities.getFirst();
        assertThat(savedEntity1.getKey().getEventId()).startsWith("device-1-1000-");
        assertThat(savedEntity1.getKey().getDeviceId()).isEqualTo("device-1");
        assertThat(savedEntity1.getTimestamp()).isEqualTo(1000L);
        assertThat(savedEntity1.getType()).isEqualTo("SENSOR");
        assertThat(savedEntity1.getPayload()).isEqualTo("25.5");
        
        // Verify second entity
        DeviceEventEntity savedEntity2 = savedEntities.get(1);
        assertThat(savedEntity2.getKey().getEventId()).startsWith("device-1-1001-");
        assertThat(savedEntity2.getKey().getDeviceId()).isEqualTo("device-1");
        assertThat(savedEntity2.getTimestamp()).isEqualTo(1001L);
        assertThat(savedEntity2.getType()).isEqualTo("SENSOR");
        assertThat(savedEntity2.getPayload()).isEqualTo("60.0");
        
        // Verify third entity
        DeviceEventEntity savedEntity3 = savedEntities.get(2);
        assertThat(savedEntity3.getKey().getEventId()).startsWith("device-2-1002-");
        assertThat(savedEntity3.getKey().getDeviceId()).isEqualTo("device-2");
        assertThat(savedEntity3.getTimestamp()).isEqualTo(1002L);
        assertThat(savedEntity3.getType()).isEqualTo("SENSOR");
        assertThat(savedEntity3.getPayload()).isEqualTo("26.0");
    }

    @Test
    void onBatch_WhenMultipleEventsFromSameDevice_ShouldPublishDeviceIdOnlyOnce() {
        // Given
        DeviceEvent event1 = createDeviceEvent("device-1", "SENSOR", 1000L, "25.5");
        DeviceEvent event2 = createDeviceEvent("device-1", "SENSOR", 1001L, "60.0");
        
        List<DeviceEvent> events = Arrays.asList(event1, event2);

        // When
        deviceEventsListener.onBatch(events, acknowledgment);

        // Then
        verify(deviceEventRepository).saveAll(anyList());
        verify(deviceIdPublisher, times(1)).publishIfNew("device-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onBatch_WhenMultipleEventsFromSameDeviceInLargeBatch_ShouldPublishDeviceIdOnlyOnce() {
        // Given
        DeviceEvent event1 = createDeviceEvent("device-1", "SENSOR", 1000L, "25.5");
        DeviceEvent event2 = createDeviceEvent("device-1", "SENSOR", 1001L, "60.0");
        DeviceEvent event3 = createDeviceEvent("device-1", "SENSOR", 1002L, "1013.25");
        DeviceEvent event4 = createDeviceEvent("device-2", "SENSOR", 1003L, "26.0");
        DeviceEvent event5 = createDeviceEvent("device-2", "SENSOR", 1004L, "65.0");
        
        List<DeviceEvent> events = Arrays.asList(event1, event2, event3, event4, event5);

        // When
        deviceEventsListener.onBatch(events, acknowledgment);

        // Then
        verify(deviceEventRepository).saveAll(anyList());
        verify(deviceIdPublisher, times(1)).publishIfNew("device-1");
        verify(deviceIdPublisher, times(1)).publishIfNew("device-2");
        verify(deviceIdPublisher, times(2)).publishIfNew(anyString()); // Всего 2 уникальных устройства
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onBatch_WhenEventsWithNullValues_ShouldProcessCorrectly() {
        // Given
        DeviceEvent event = createDeviceEvent("device-1", null, 1000L, null);
        List<DeviceEvent> events = Collections.singletonList(event);

        // When
        deviceEventsListener.onBatch(events, acknowledgment);

        // Then
        verify(deviceEventRepository).saveAll(entitiesCaptor.capture());
        verify(deviceIdPublisher).publishIfNew("device-1");
        verify(acknowledgment).acknowledge();

        List<DeviceEventEntity> savedEntities = entitiesCaptor.getValue();
        assertThat(savedEntities).hasSize(1);
        
        DeviceEventEntity savedEntity = savedEntities.getFirst();
        assertThat(savedEntity.getType()).isNull();
        assertThat(savedEntity.getPayload()).isNull();
    }

    @Test
    void onBatch_WhenEventsWithEmptyStrings_ShouldProcessCorrectly() {
        // Given
        DeviceEvent event = createDeviceEvent("device-1", "", 1000L, "");
        List<DeviceEvent> events = Collections.singletonList(event);

        // When
        deviceEventsListener.onBatch(events, acknowledgment);

        // Then
        verify(deviceEventRepository).saveAll(entitiesCaptor.capture());
        verify(deviceIdPublisher).publishIfNew("device-1");
        verify(acknowledgment).acknowledge();

        List<DeviceEventEntity> savedEntities = entitiesCaptor.getValue();
        assertThat(savedEntities).hasSize(1);
        
        DeviceEventEntity savedEntity = savedEntities.getFirst();
        assertThat(savedEntity.getType()).isEmpty();
        assertThat(savedEntity.getPayload()).isEmpty();
    }

    @Test
    void onBatch_WhenEventsWithZeroTimestamp_ShouldProcessCorrectly() {
        // Given
        DeviceEvent event = createDeviceEvent("device-1", "SENSOR", 0L, "25.5");
        List<DeviceEvent> events = Collections.singletonList(event);

        // When
        deviceEventsListener.onBatch(events, acknowledgment);

        // Then
        verify(deviceEventRepository).saveAll(entitiesCaptor.capture());
        verify(deviceIdPublisher).publishIfNew("device-1");
        verify(acknowledgment).acknowledge();

        List<DeviceEventEntity> savedEntities = entitiesCaptor.getValue();
        assertThat(savedEntities).hasSize(1);
        
        DeviceEventEntity savedEntity = savedEntities.getFirst();
        assertThat(savedEntity.getTimestamp()).isZero();
    }

    private DeviceEvent createDeviceEvent(String deviceId, String deviceType, Long createdAt, String meta) {
        DeviceEvent event = new DeviceEvent();
        event.setDeviceId(deviceId);
        event.setDeviceType(deviceType);
        event.setCreatedAt(createdAt);
        event.setMeta(meta);
        return event;
    }
}
