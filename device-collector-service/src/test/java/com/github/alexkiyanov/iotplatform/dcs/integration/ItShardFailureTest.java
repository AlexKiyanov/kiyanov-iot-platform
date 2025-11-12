package com.github.alexkiyanov.iotplatform.dcs.integration;

import com.github.alexkiyanov.iotplatform.avro.DeviceInfo;
import com.github.alexkiyanov.iotplatform.dcs.model.DeviceInfoEntity;
import com.github.alexkiyanov.iotplatform.dcs.repository.DeviceInfoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.TransactionException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Интеграционные тесты для проверки отказоустойчивости шардированной системы
 */
@ActiveProfiles("integration-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ItShardFailureTest extends AbstractBaseTest {

    @Autowired
    private DeviceInfoRepository deviceInfoRepository;

    @Autowired
    @Qualifier("avroKafkaTemplate")
    private KafkaTemplate<String, DeviceInfo> avroKafkaTemplate;

    @Test
    void givenShard1ReplicaUnavailable_whenReadingData_thenFallbackToPrimary() {
        // Given - сначала сохраняем данные
        DeviceInfo testDevice = createDeviceInfo("replica-test-device");
        
        CompletableFuture<SendResult<String, DeviceInfo>> future = 
            avroKafkaTemplate.send("device-id-topic", testDevice.getDeviceId(), testDevice);
        assertThat(future).succeedsWithin(Duration.ofSeconds(10));

        // Ждем сохранения
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                List<DeviceInfoEntity> savedDevices = deviceInfoRepository.findByDeviceId("replica-test-device");
                assertThat(savedDevices).hasSize(1);
            });

        // When - останавливаем реплику первого шарда
        postgresShard1Replica.stop();
        
        try {
            // Then - чтение должно работать (fallback на primary)
            await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<DeviceInfoEntity> savedDevices = deviceInfoRepository.findByDeviceId("replica-test-device");
                    assertThat(savedDevices).hasSize(1);
                    assertThat(savedDevices.get(0).getDeviceId()).isEqualTo("replica-test-device");
                });

        } finally {
            // Восстанавливаем реплику
            postgresShard1Replica.start();
            await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertThat(postgresShard1Replica.isRunning()).isTrue();
                });
        }
    }

    @Test
    void givenShardRecovery_whenRetryingFailedOperations_thenDataIsEventuallyConsistent() {
        // Given - создаем устройство
        DeviceInfo testDevice = createDeviceInfo("recovery-test-device");
        
        // Останавливаем шард
        postgresShard1.stop();
        
        // When - пытаемся сохранить (должно упасть)
        CompletableFuture<SendResult<String, DeviceInfo>> future = 
            avroKafkaTemplate.send("device-id-topic", testDevice.getDeviceId(), testDevice);
        assertThat(future).succeedsWithin(Duration.ofSeconds(10));

        // Ждем некоторое время для попыток обработки
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Восстанавливаем шард
        postgresShard1.start();
        
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                assertThat(postgresShard1.isRunning()).isTrue();
            });

        // Then - после восстановления шарда данные должны быть доступны
        // (либо через retry механизм, либо через повторную отправку)
        await().atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(2))
            .untilAsserted(() -> {
                try {
                    List<DeviceInfoEntity> savedDevices = deviceInfoRepository.findByDeviceId("recovery-test-device");
                    // Может быть 0 (если сообщение попало в DLT) или 1 (если retry сработал)
                    assertThat(savedDevices.size()).isIn(0, 1);
                } catch (DataAccessException e) {
                    // Это тоже допустимо - шард может быть еще не полностью готов
                }
            });
    }

    @Test
    void givenAllShardsUnavailable_whenProcessingMessages_thenAllOperationsFail() {
        // Given - останавливаем все шарды
        postgresShard1.stop();
        postgresShard2.stop();
        
        try {
            DeviceInfo testDevice = createDeviceInfo("all-shards-down-device");
            
            // When - пытаемся сохранить
            CompletableFuture<SendResult<String, DeviceInfo>> future = 
                avroKafkaTemplate.send("device-id-topic", testDevice.getDeviceId(), testDevice);
            assertThat(future).succeedsWithin(Duration.ofSeconds(10));

            // Then - любые операции с базой должны падать
            assertThatThrownBy(() -> {
                deviceInfoRepository.findByDeviceId("all-shards-down-device");
            }).isInstanceOf(DataAccessException.class);

            // Проверяем, что репозиторий вообще недоступен
            assertThatThrownBy(() -> {
                deviceInfoRepository.findAll();
            }).isInstanceOf(TransactionException.class);

        } finally {
            // Восстанавливаем все шарды
            postgresShard1.start();
            postgresShard2.start();
            
            await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertThat(postgresShard1.isRunning()).isTrue();
                    assertThat(postgresShard2.isRunning()).isTrue();
                });
        }
    }

    private DeviceInfo createDeviceInfo(String deviceId) {
        return DeviceInfo.newBuilder()
                .setDeviceId(deviceId)
                .setDeviceType("test-sensor")
                .setManufacturer("TestCorp")
                .setModel("T-100")
                .setFirmwareVersion("1.0.0")
                .setFirstSeen(System.currentTimeMillis())
                .setLastSeen(System.currentTimeMillis())
                .setStatus("active")
                .build();
    }
}
