package com.github.alexkiyanov.iotplatform.dcs.integration;

import com.github.alexkiyanov.iotplatform.avro.DeviceInfo;
import com.github.alexkiyanov.iotplatform.avro.PoisonMessage;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("integration-test")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ItDltIntegrationTest extends AbstractBaseTest {

    @Autowired
    @Qualifier("avroKafkaTemplate")

    private KafkaTemplate<String, DeviceInfo> avroKafkaTemplate;
    @Test
    void givenPoisonMessages_whenRetriesExhausted_thenMessagesAppearInDLT() {
        DeviceInfo bad1 = DeviceInfo.newBuilder()
                .setDeviceId("")
                .setDeviceType("sensor")
                .setManufacturer("BadCorp")
                .setModel("B-1")
                .setFirmwareVersion("0.0.1")
                .setFirstSeen(System.currentTimeMillis())
                .setLastSeen(System.currentTimeMillis())
                .setStatus("inactive")
                .build();

        DeviceInfo bad2 = DeviceInfo.newBuilder()
                .setDeviceId("   ")
                .setDeviceType("sensor")
                .setManufacturer("BadCorp")
                .setModel("B-2")
                .setFirmwareVersion("0.0.1")
                .setFirstSeen(System.currentTimeMillis())
                .setLastSeen(System.currentTimeMillis())
                .setStatus("inactive")
                .build();

        CompletableFuture<SendResult<String, DeviceInfo>> f1 = avroKafkaTemplate.send("device-id-topic", "", bad1);
        CompletableFuture<SendResult<String, DeviceInfo>> f2 = avroKafkaTemplate.send("device-id-topic", "key-null", bad2);

        assertThat(f1).isNotNull();
        assertThat(f2).isNotNull();
        assertThat(f1).succeedsWithin(Duration.ofSeconds(10));
        assertThat(f2).succeedsWithin(Duration.ofSeconds(10));

        await().atMost(Duration.ofSeconds(25))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<PoisonMessage> dltMessages = consumeDlt();
                    assertThat(dltMessages.size()).isGreaterThanOrEqualTo(2);
                });
    }

    private List<PoisonMessage> consumeDlt() {
        List<PoisonMessage> messages = new ArrayList<>();

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlt-consumer-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        consumerProps.put("specific.avro.reader", true);
        consumerProps.put("schema.registry.url", "http://localhost:" + registry.getMappedPort(8081));

        try (KafkaConsumer<String, PoisonMessage> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("device-id-dlt"));

            long startTime = System.currentTimeMillis();
            long timeout = 8000;

            while (System.currentTimeMillis() - startTime < timeout) {
                ConsumerRecords<String, PoisonMessage> records = consumer.poll(Duration.ofMillis(250));
                if (!records.isEmpty()) {
                    for (ConsumerRecord<String, PoisonMessage> r : records) {
                        PoisonMessage value = r.value();
                        if (value != null) {
                            messages.add(value);
                        }
                    }
                }
                if (!messages.isEmpty()) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error consuming from DLT: " + e.getMessage());
        }

        return messages;
    }
}


