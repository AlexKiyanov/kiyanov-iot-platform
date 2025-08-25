package com.github.alexkiyanov.iotplatform.ecs.integration;

import com.github.alexkiyanov.iotplatform.avro.DeviceEvent;
import com.github.alexkiyanov.iotplatform.ecs.consumer.DeviceEventsListener;
import com.github.alexkiyanov.iotplatform.ecs.model.cassandra.DeviceEventEntity;
import com.github.alexkiyanov.iotplatform.ecs.repository.DeviceEventRepository;
import com.github.alexkiyanov.iotplatform.ecs.service.DeviceIdPublisher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ItEcsPipelineTest extends AbstractBaseTest {

    @Autowired
    private DeviceEventRepository deviceEventRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private DeviceEventsListener deviceEventsListener;

    @Autowired
    private DeviceIdPublisher deviceIdPublisher;

    @Test
    void givenSpringContext_whenBootstrapped_thenAllBeansAreAvailable() {
        // Проверяем основные бины
        assertThat(deviceEventsListener).isNotNull();
        assertThat(deviceIdPublisher).isNotNull();
        assertThat(deviceEventRepository).isNotNull();
        assertThat(kafkaTemplate).isNotNull();
    }

    @Test
    void givenAllContainers_whenSpringContextIsBootstrapped_thenAllContainersAreRunning() {
        assertThat(cassandra.isRunning()).isTrue();
        assertThat(kafka.isRunning()).isTrue();
        assertThat(registry.isRunning()).isTrue();
    }

    @Test
    void givenCassandraContainer_whenSpringContextIsBootstrapped_thenCassandraConnectionIsEstablished() {
        // Проверяем, что репозиторий доступен
        assertThat(deviceEventRepository).isNotNull();

        // Проверяем, что можем выполнить простой запрос
        List<DeviceEventEntity> events = deviceEventRepository.findAll();
        assertThat(events).isNotNull();
    }

    @Test
    void givenKafkaContainer_whenSpringContextIsBootstrapped_thenKafkaTemplateIsAvailable() {
        // Проверяем, что KafkaTemplate доступен
        assertThat(kafkaTemplate).isNotNull();

        DeviceEvent testEvent = DeviceEvent.newBuilder()
                .setEventId("kafka-test-event")
                .setDeviceId("kafka-test-device")
                .setTimestamp(System.currentTimeMillis())
                .setType("connectivity")
                .setPayload("test-connection")
                .build();

        // Проверяем, что можем отправить Avro сообщение
        CompletableFuture<?> future = kafkaTemplate.send("events", "test-key", testEvent);

        assertThat(future).isNotNull();
    }

    @Test
    void givenSchemaRegistryContainer_whenSpringContextIsBootstrapped_thenSchemaRegistryIsAccessible() {
        // Проверяем, что Schema Registry доступен
        String schemaRegistryUrl = "http://localhost:" + registry.getMappedPort(8081);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(
                schemaRegistryUrl + "/subjects", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenDeviceEvent_whenSentToKafka_thenEventIsSavedToCassandra() {
        // Создаем тестовое событие
        DeviceEvent testEvent = DeviceEvent.newBuilder()
                .setEventId("test-event-1")
                .setDeviceId("test-device-1")
                .setTimestamp(System.currentTimeMillis())
                .setType("temperature")
                .setPayload("25.5")
                .build();

        // Отправляем событие в Kafka через Avro KafkaTemplate
        kafkaTemplate.send("events", testEvent.getDeviceId(), testEvent);

        // Ждем обработки с проверкой всех полей
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<DeviceEventEntity> savedEvents = deviceEventRepository.findByDeviceId("test-device-1");
                    assertThat(savedEvents).hasSize(1);

                    DeviceEventEntity savedEvent = savedEvents.getFirst();
                    assertThat(savedEvent.getKey().getDeviceId()).isEqualTo("test-device-1");
                    assertThat(savedEvent.getKey().getEventId()).isEqualTo("test-event-1");
                    assertThat(savedEvent.getType()).isEqualTo("temperature");
                    assertThat(savedEvent.getPayload()).isEqualTo("25.5");
                });
    }

    @Test
    void givenMultipleEventsFromSameDevice_whenProcessed_thenDeviceIdPublishedOnlyOnce() {
        // Создаем несколько событий от одного устройства
        DeviceEvent event1 = DeviceEvent.newBuilder()
                .setEventId("event-1")
                .setDeviceId("device-1")
                .setTimestamp(System.currentTimeMillis())
                .setType("temperature")
                .setPayload("25.5")
                .build();

        DeviceEvent event2 = DeviceEvent.newBuilder()
                .setEventId("event-2")
                .setDeviceId("device-1")
                .setTimestamp(System.currentTimeMillis() + 1000)
                .setType("humidity")
                .setPayload("60.0")
                .build();

        // Отправляем события через Avro KafkaTemplate
        CompletableFuture<SendResult<String, Object>> future1 =
                kafkaTemplate.send("events", event1.getDeviceId(), event1);
        CompletableFuture<SendResult<String, Object>> future2 =
                kafkaTemplate.send("events", event2.getDeviceId(), event2);

        // Ждем успешной отправки обоих событий
        assertThat(future1).succeedsWithin(Duration.ofSeconds(10));
        assertThat(future2).succeedsWithin(Duration.ofSeconds(10));

        // Ждем обработки событий и сохранения в Cassandra
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<DeviceEventEntity> savedEvents = deviceEventRepository.findByDeviceId("device-1");
                    assertThat(savedEvents).hasSize(2);
                    assertThat(savedEvents).extracting("key.eventId")
                            .containsExactlyInAnyOrder("event-1", "event-2");
                });

        // Используем Awaitility для проверки, что в device-id-topic появилось только одно сообщение
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<String> messages = consumeMessagesFromDeviceIdTopic();

                    // Должно быть только одно сообщение "device-1" (уникальный deviceId)
                    // Проверяем, что deviceId опубликован только один раз благодаря кешу
                    assertThat(messages).hasSize(1);
                    assertThat(messages).contains("device-1");
                });
    }

    private List<String> consumeMessagesFromDeviceIdTopic() {
        List<String> messages = new ArrayList<>();

        // Создаем consumer для чтения из device-id-topic с уникальной группой
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-device-id-consumer-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("device-id-topic"));

            // Читаем сообщения в течение 5 секунд с более частым polling
            long startTime = System.currentTimeMillis();
            long timeout = 5000; // 5 секунд

            while (System.currentTimeMillis() - startTime < timeout) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));

                if (!records.isEmpty()) {
                    for (ConsumerRecord<String, String> r : records) {
                        String value = r.value();
                        if ("device-1".equals(value)) {
                            messages.add(value);
                        }
                    }
                }

                // Если нашли сообщения, можем выйти раньше
                if (!messages.isEmpty()) {
                    // Продолжаем читать еще немного, чтобы убедиться что нет дублей
                    long additionalWait = System.currentTimeMillis();
                    while (System.currentTimeMillis() - additionalWait < 1000) {
                        ConsumerRecords<String, String> additionalRecords = consumer.poll(Duration.ofMillis(200));
                        for (ConsumerRecord<String, String> r : additionalRecords) {
                            String value = r.value();
                            if ("device-1".equals(value)) {
                                messages.add(value);
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // Логируем ошибку, но не бросаем исключение
            System.err.println("Error consuming from device-id-topic: " + e.getMessage());
        }

        return messages;
    }

    @Test
    void givenBatchOfEvents_whenProcessed_thenAllEventsAreSaved() {
        final int BATCH_SIZE = 500;
        // Создаем batch событий
        List<DeviceEvent> events = IntStream.range(0, BATCH_SIZE)
                .mapToObj(i -> DeviceEvent.newBuilder()
                        .setEventId("batch-event-" + i)
                        .setDeviceId("batch-device-" + i)
                        .setTimestamp(System.currentTimeMillis() + i)
                        .setType("sensor")
                        .setPayload("value-" + i)
                        .build())
                .toList();

        // Отправляем batch через Avro KafkaTemplate и ждем успешной отправки
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        for (DeviceEvent event : events) {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send("events", event.getDeviceId(), event);
            futures.add(future);
        }

        // Ждем успешной отправки всех событий
        for (CompletableFuture<SendResult<String, Object>> future : futures) {
            assertThat(future).succeedsWithin(Duration.ofSeconds(10));
        }

        // Используем Awaitility для проверки, что все события сохранились в Cassandra
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    // Проверяем, что все события сохранились
                    for (int i = 0; i < BATCH_SIZE; i++) {
                        List<DeviceEventEntity> savedEvents = deviceEventRepository.findByDeviceId("batch-device-" + i);
                        assertThat(savedEvents).hasSize(1);
                        assertThat(savedEvents.getFirst().getKey().getEventId()).isEqualTo("batch-event-" + i);
                    }
                });
    }

    @Test
    void givenValidEvent_whenProcessed_thenNoExceptionsThrown() {
        // Отправляем валидное сообщение
        DeviceEvent validEvent = DeviceEvent.newBuilder()
                .setEventId("valid-event")
                .setDeviceId("valid-device")
                .setTimestamp(System.currentTimeMillis())
                .setType("test")
                .setPayload("valid-payload")
                .build();

        // Отправляем событие и ждем успешной отправки
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send("events", validEvent.getDeviceId(), validEvent);

        // Ждем успешной отправки
        assertThat(future).succeedsWithin(Duration.ofSeconds(10));

        // Используем Awaitility для проверки, что событие сохранилось в Cassandra
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<DeviceEventEntity> savedEvents = deviceEventRepository.findByDeviceId("valid-device");
                    assertThat(savedEvents).hasSize(1);
                    assertThat(savedEvents.getFirst().getKey().getEventId()).isEqualTo("valid-event");
                });
    }
}
