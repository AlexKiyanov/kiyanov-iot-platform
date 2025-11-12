package com.github.alexkiyanov.iotplatform.dcs.integration;

import com.github.alexkiyanov.iotplatform.avro.DeviceInfo;
import com.github.alexkiyanov.iotplatform.dcs.consumer.DeviceIdListener;
import com.github.alexkiyanov.iotplatform.dcs.model.DeviceInfoEntity;
import com.github.alexkiyanov.iotplatform.dcs.repository.DeviceInfoRepository;
import com.github.alexkiyanov.iotplatform.dcs.service.DeviceInfoPublisher;
import com.github.alexkiyanov.iotplatform.dcs.service.PoisonMessageHandler;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
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

@ActiveProfiles("integration-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ItDcsPipelineTest extends AbstractBaseTest {

    @Autowired
    private DeviceInfoRepository deviceInfoRepository;

    @Autowired
    @Qualifier("avroKafkaTemplate")
    private KafkaTemplate<String, DeviceInfo> avroKafkaTemplate;

    @Autowired
    private DeviceIdListener deviceIdListener;

    @Autowired
    private DeviceInfoPublisher deviceInfoPublisher;

    @Autowired
    private PoisonMessageHandler poisonMessageHandler;

    @Test
    void givenSpringContext_whenBootstrapped_thenAllBeansAreAvailable() {
        // Проверяем основные бины
        assertThat(deviceIdListener).isNotNull();
        assertThat(deviceInfoPublisher).isNotNull();
        assertThat(deviceInfoRepository).isNotNull();
        assertThat(avroKafkaTemplate).isNotNull();
        assertThat(poisonMessageHandler).isNotNull();
    }

    @Test
    void givenAllContainers_whenSpringContextIsBootstrapped_thenAllContainersAreRunning() {
        assertThat(postgresShard1.isRunning()).isTrue();
        assertThat(postgresShard1Replica.isRunning()).isTrue();
        assertThat(postgresShard2.isRunning()).isTrue();
        assertThat(postgresShard2Replica.isRunning()).isTrue();
        assertThat(kafka.isRunning()).isTrue();
        assertThat(registry.isRunning()).isTrue();
    }

    @Test
    void givenPostgreSQLContainers_whenSpringContextIsBootstrapped_thenDatabaseConnectionsAreEstablished() {
        // Проверяем, что репозиторий доступен
        assertThat(deviceInfoRepository).isNotNull();

        // Проверяем, что можем выполнить простой запрос
        List<DeviceInfoEntity> devices = deviceInfoRepository.findAll();
        assertThat(devices).isNotNull();
    }

    @Test
    void givenKafkaContainer_whenSpringContextIsBootstrapped_thenKafkaTemplateIsAvailable() {
        // Проверяем, что KafkaTemplate доступен
        assertThat(avroKafkaTemplate).isNotNull();

        DeviceInfo testDeviceInfo = DeviceInfo.newBuilder()
                .setDeviceId("kafka-test-device")
                .setDeviceType("sensor")
                .setManufacturer("test-manufacturer")
                .setModel("test-model")
                .setFirmwareVersion("1.0.0")
                .setFirstSeen(System.currentTimeMillis())
                .setLastSeen(System.currentTimeMillis())
                .setStatus("active")
                .build();

        // Проверяем, что можем отправить Avro сообщение
        CompletableFuture<?> future = avroKafkaTemplate.send("device-id-topic", "test-key", testDeviceInfo);

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
    void givenDeviceInfo_whenSentToKafka_thenDeviceInfoIsSavedToDatabase() {
        // Создаем тестовое устройство
        DeviceInfo testDeviceInfo = DeviceInfo.newBuilder()
                .setDeviceId("test-device-1")
                .setDeviceType("temperature-sensor")
                .setManufacturer("TestCorp")
                .setModel("TC-100")
                .setFirmwareVersion("2.1.0")
                .setFirstSeen(System.currentTimeMillis())
                .setLastSeen(System.currentTimeMillis())
                .setStatus("active")
                .build();

        // Отправляем устройство в Kafka через Avro KafkaTemplate
        avroKafkaTemplate.send("device-id-topic", testDeviceInfo.getDeviceId(), testDeviceInfo);

        // Ждем обработки с проверкой всех полей
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<DeviceInfoEntity> savedDevices = deviceInfoRepository.findByDeviceId("test-device-1");
                    assertThat(savedDevices).hasSize(1);

                    DeviceInfoEntity savedDevice = savedDevices.getFirst();
                    assertThat(savedDevice.getDeviceId()).isEqualTo("test-device-1");
                    assertThat(savedDevice.getDeviceType()).isEqualTo("temperature-sensor");
                    assertThat(savedDevice.getManufacturer()).isEqualTo("TestCorp");
                    assertThat(savedDevice.getModel()).isEqualTo("TC-100");
                    assertThat(savedDevice.getFirmwareVersion()).isEqualTo("2.1.0");
                    assertThat(savedDevice.getStatus()).isEqualTo("active");
                });
    }

    @Test
    void givenDeviceInfo_whenProcessed_thenDeviceInfoIsPublishedToOutputTopic() {
        // Создаем тестовое устройство
        DeviceInfo testDeviceInfo = DeviceInfo.newBuilder()
                .setDeviceId("publish-test-device")
                .setDeviceType("humidity-sensor")
                .setManufacturer("HumidityCorp")
                .setModel("HC-200")
                .setFirmwareVersion("1.5.0")
                .setFirstSeen(System.currentTimeMillis())
                .setLastSeen(System.currentTimeMillis())
                .setStatus("active")
                .build();

        // Отправляем устройство в Kafka
        CompletableFuture<SendResult<String, DeviceInfo>> future =
                avroKafkaTemplate.send("device-id-topic", testDeviceInfo.getDeviceId(), testDeviceInfo);

        // Ждем успешной отправки
        assertThat(future).succeedsWithin(Duration.ofSeconds(30));

        // Ждем обработки устройства и сохранения в базу
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<DeviceInfoEntity> savedDevices = deviceInfoRepository.findByDeviceId("publish-test-device");
                    assertThat(savedDevices).hasSize(1);
                });

        // Проверяем, что устройство опубликовано в output topic
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<DeviceInfo> messages = consumeMessagesFromDeviceInfoTopic();

                    // Должно быть одно сообщение с нашим устройством
                    assertThat(messages).hasSize(1);
                    assertThat(messages.get(0).getDeviceId()).isEqualTo("publish-test-device");
                    assertThat(messages.get(0).getDeviceType()).isEqualTo("humidity-sensor");
                    assertThat(messages.get(0).getManufacturer()).isEqualTo("HumidityCorp");
                });
    }

    @Test
    void givenMultipleDevices_whenProcessed_thenAllDevicesAreSavedAndPublished() {
        final int DEVICE_COUNT = 10;
        
        // Создаем несколько устройств
        List<DeviceInfo> devices = IntStream.range(0, DEVICE_COUNT)
                .mapToObj(i -> DeviceInfo.newBuilder()
                        .setDeviceId("batch-device-" + i)
                        .setDeviceType("sensor-" + i)
                        .setManufacturer("Manufacturer-" + i)
                        .setModel("Model-" + i)
                        .setFirmwareVersion("1.0." + i)
                        .setFirstSeen(System.currentTimeMillis())
                        .setLastSeen(System.currentTimeMillis())
                        .setStatus("active")
                        .build())
                .toList();

        // Отправляем все устройства через Avro KafkaTemplate
        List<CompletableFuture<SendResult<String, DeviceInfo>>> futures = new ArrayList<>();
        for (DeviceInfo device : devices) {
            CompletableFuture<SendResult<String, DeviceInfo>> future =
                    avroKafkaTemplate.send("device-id-topic", device.getDeviceId(), device);
            futures.add(future);
        }

        // Ждем успешной отправки всех устройств
        for (CompletableFuture<SendResult<String, DeviceInfo>> future : futures) {
            assertThat(future).succeedsWithin(Duration.ofSeconds(30));
        }

        // Используем Awaitility для проверки, что все устройства сохранились в базу
        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    // Проверяем, что все устройства сохранились
                    for (int i = 0; i < DEVICE_COUNT; i++) {
                        List<DeviceInfoEntity> savedDevices = deviceInfoRepository.findByDeviceId("batch-device-" + i);
                        assertThat(savedDevices).hasSize(1);
                        assertThat(savedDevices.getFirst().getDeviceId()).isEqualTo("batch-device-" + i);
                    }
                });

        // Проверяем, что все устройства опубликованы в output topic
        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<DeviceInfo> messages = consumeMessagesFromDeviceInfoTopic();
                    
                    // Должно быть DEVICE_COUNT сообщений
                    // Фильтруем только сообщения из этого теста
                    List<DeviceInfo> testMessages = messages.stream()
                            .filter(msg -> msg.getDeviceId().startsWith("batch-device-"))
                            .toList();
                    
                    // Должно быть как минимум DEVICE_COUNT сообщений
                    assertThat(testMessages).hasSizeGreaterThanOrEqualTo(DEVICE_COUNT);
                    
                    // Проверяем, что все устройства присутствуют
                    for (int i = 0; i < DEVICE_COUNT; i++) {
                        String deviceId = "batch-device-" + i;
                        assertThat(testMessages).anyMatch(msg -> deviceId.equals(msg.getDeviceId()));
                    }
                });
    }

    @Test
    void givenValidDeviceInfo_whenProcessed_thenNoExceptionsThrown() {
        // Отправляем валидное устройство
        DeviceInfo validDeviceInfo = DeviceInfo.newBuilder()
                .setDeviceId("valid-device")
                .setDeviceType("pressure-sensor")
                .setManufacturer("PressureCorp")
                .setModel("PC-300")
                .setFirmwareVersion("3.0.0")
                .setFirstSeen(System.currentTimeMillis())
                .setLastSeen(System.currentTimeMillis())
                .setStatus("active")
                .build();

        // Отправляем устройство и ждем успешной отправки
        CompletableFuture<SendResult<String, DeviceInfo>> future =
                avroKafkaTemplate.send("device-id-topic", validDeviceInfo.getDeviceId(), validDeviceInfo);

        // Ждем успешной отправки
        assertThat(future).succeedsWithin(Duration.ofSeconds(30));

        // Используем Awaitility для проверки, что устройство сохранилось в базу
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<DeviceInfoEntity> savedDevices = deviceInfoRepository.findByDeviceId("valid-device");
                    assertThat(savedDevices).hasSize(1);
                    assertThat(savedDevices.getFirst().getDeviceId()).isEqualTo("valid-device");
                });
    }

    private List<DeviceInfo> consumeMessagesFromDeviceInfoTopic() {
        List<DeviceInfo> messages = new ArrayList<>();

        // Создаем consumer для чтения из device-info-topic с уникальной группой
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-device-info-consumer-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        consumerProps.put("specific.avro.reader", true);
        consumerProps.put("schema.registry.url", "http://localhost:" + registry.getMappedPort(8081));

        try (KafkaConsumer<String, DeviceInfo> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("device-info-topic"));

            // Читаем сообщения в течение 5 секунд
            long startTime = System.currentTimeMillis();
            long timeout = 5000; // 5 секунд

            while (System.currentTimeMillis() - startTime < timeout) {
                ConsumerRecords<String, DeviceInfo> records = consumer.poll(Duration.ofMillis(200));

                if (!records.isEmpty()) {
                    for (ConsumerRecord<String, DeviceInfo> r : records) {
                        DeviceInfo value = r.value();
                        if (value != null) {
                            messages.add(value);
                        }
                    }
                }

                // Если нашли сообщения, можем выйти раньше
                if (!messages.isEmpty()) {
                    // Продолжаем читать еще немного, чтобы убедиться что нет дублей
                    long additionalWait = System.currentTimeMillis();
                    while (System.currentTimeMillis() - additionalWait < 1000) {
                        ConsumerRecords<String, DeviceInfo> additionalRecords = consumer.poll(Duration.ofMillis(200));
                        for (ConsumerRecord<String, DeviceInfo> r : additionalRecords) {
                            DeviceInfo value = r.value();
                            if (value != null) {
                                messages.add(value);
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // Логируем ошибку, но не бросаем исключение
            System.err.println("Error consuming from device-info-topic: " + e.getMessage());
        }

        return messages;
    }
}
