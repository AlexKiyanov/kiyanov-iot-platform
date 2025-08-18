package com.github.alexkiyanov.iotplatform.ecs.consumer;

import com.github.alexkiyanov.iotplatform.ecs.avro.DeviceEvent;
import com.github.alexkiyanov.iotplatform.ecs.model.cassandra.DeviceEventEntity;
import com.github.alexkiyanov.iotplatform.ecs.model.cassandra.DeviceEventKey;
import com.github.alexkiyanov.iotplatform.ecs.repository.DeviceEventRepository;
import com.github.alexkiyanov.iotplatform.ecs.service.DeviceIdPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeviceEventsListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceEventsListener.class);

    private final DeviceEventRepository repo;
    private final DeviceIdPublisher publisher;
    private final String inputTopic;

    public DeviceEventsListener(DeviceEventRepository repo,
                                DeviceIdPublisher publisher,
                                @Value("${spring.app.topics.input}") String inputTopic) {
        this.repo = repo;
        this.publisher = publisher;
        this.inputTopic = inputTopic;
    }

    @KafkaListener(topics = "#{'${spring.app.topics.input}'}", containerFactory = "kafkaBatchListenerFactory")
    public void onBatch(@Payload List<DeviceEvent> events, Acknowledgment ack) {
        if (events == null || events.isEmpty()) {
            return;
        }
        log.info("Received batch: {} messages from {}", events.size(), inputTopic);

        final List<DeviceEventEntity> entities = events.stream()
                .map(e ->
                        new DeviceEventEntity(
                                new DeviceEventKey(e.getDeviceId(), e.getEventId()),
                                e.getTimestamp(),
                                e.getType(),
                                e.getPayload()
                        ))
                .toList();

        repo.saveAll(entities);

        events.stream()
                .map(DeviceEvent::getDeviceId)
                .distinct()
                .forEach(publisher::publishIfNew);

        ack.acknowledge();
    }
}
