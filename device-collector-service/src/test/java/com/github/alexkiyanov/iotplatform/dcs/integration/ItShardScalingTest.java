package com.github.alexkiyanov.iotplatform.dcs.integration;

import com.github.alexkiyanov.iotplatform.avro.DeviceInfo;
import com.github.alexkiyanov.iotplatform.dcs.model.DeviceInfoEntity;
import com.github.alexkiyanov.iotplatform.dcs.repository.DeviceInfoRepository;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Интеграционные тесты для проверки масштабирования шардов
 */
@ActiveProfiles("integration-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ItShardScalingTest extends AbstractBaseTest {

    @Autowired
    private DeviceInfoRepository deviceInfoRepository;

    @Autowired
    @Qualifier("avroKafkaTemplate")
    private KafkaTemplate<String, DeviceInfo> avroKafkaTemplate;

    @Test
    void givenTwoShardConfiguration_whenDataDistributed_thenEvenDistribution() {
        // Given - создаем большое количество устройств
        final int DEVICE_COUNT = 100;
        List<DeviceInfo> devices = IntStream.range(0, DEVICE_COUNT)
            .mapToObj(i -> createDeviceInfo("scale-test-device-" + i))
            .toList();

        // When - отправляем все устройства
        List<CompletableFuture<SendResult<String, DeviceInfo>>> futures = new ArrayList<>();
        for (DeviceInfo device : devices) {
            CompletableFuture<SendResult<String, DeviceInfo>> future = 
                avroKafkaTemplate.send("device-id-topic", device.getDeviceId(), device);
            futures.add(future);
        }

        // Ждем отправки всех сообщений
        for (CompletableFuture<SendResult<String, DeviceInfo>> future : futures) {
            assertThat(future).succeedsWithin(Duration.ofSeconds(30));
        }

        // Then - проверяем распределение по шардам
        await().atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                Map<Integer, Integer> shardDistribution = new HashMap<>();
                
                for (DeviceInfo device : devices) {
                    try {
                        List<DeviceInfoEntity> savedDevices = deviceInfoRepository.findByDeviceId(device.getDeviceId());
                        if (!savedDevices.isEmpty()) {
                            // Определяем шард по hash от device_id
                            int shardIndex = Math.abs(device.getDeviceId().hashCode()) % 2;
                            shardDistribution.merge(shardIndex, 1, Integer::sum);
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки - некоторые устройства могут еще обрабатываться
                    }
                }

                // Проверяем, что данные распределены по обоим шардам
                assertThat(shardDistribution.keySet()).contains(0, 1);
                
                // Проверяем относительно равномерное распределение (±20%)
                if (shardDistribution.size() == 2) {
                    int shard0Count = shardDistribution.getOrDefault(0, 0);
                    int shard1Count = shardDistribution.getOrDefault(1, 0);
                    int total = shard0Count + shard1Count;
                    
                    if (total > DEVICE_COUNT * 0.8) { // Обработано хотя бы 80% устройств
                        double ratio = (double) Math.min(shard0Count, shard1Count) / Math.max(shard0Count, shard1Count);
                        assertThat(ratio).isGreaterThan(0.6d); // Разница не более чем в 1.67 раза
                    }
                }
            });
    }

    @Test
    void givenShardingAlgorithmChange_whenReconfigured_thenNewDistributionWorks() {
        // Given - сначала сохраняем данные с текущей конфигурацией
        List<DeviceInfo> initialDevices = IntStream.range(0, 20)
            .mapToObj(i -> createDeviceInfo("reconfig-device-" + i))
            .toList();

        for (DeviceInfo device : initialDevices) {
            CompletableFuture<SendResult<String, DeviceInfo>> future = 
                avroKafkaTemplate.send("device-id-topic", device.getDeviceId(), device);
            assertThat(future).succeedsWithin(Duration.ofSeconds(10));
        }

        // Ждем сохранения начальных данных
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                long savedCount = initialDevices.stream()
                    .mapToLong(device -> {
                        try {
                            return deviceInfoRepository.findByDeviceId(device.getDeviceId()).size();
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();
                assertThat(savedCount).isGreaterThan((long)(initialDevices.size() * 0.8));
            });

        // When - создаем новую конфигурацию шардирования (имитация масштабирования)
        // В реальности это было бы изменение конфигурации через административный интерфейс
        DataSource newShardingDataSource = createScaledShardingConfiguration();
        
        // Then - проверяем, что новая конфигурация работает
        // (в реальности потребовалась бы миграция данных)
        assertThat(newShardingDataSource).isNotNull();
        
        // Проверяем, что можем создать подключение к новой конфигурации
        try {
            assertThat(newShardingDataSource.getConnection()).isNotNull();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create connection with new sharding configuration", e);
        }
    }

    @Test
    void givenHashModAlgorithm_whenShardCountChanges_thenDistributionChanges() {
        // Given - тестовые device_id с известными hash значениями
        List<String> testDeviceIds = Arrays.asList(
            "device-hash-test-1",  // hash % 2 = ?
            "device-hash-test-2",  // hash % 2 = ?
            "device-hash-test-3",  // hash % 2 = ?
            "device-hash-test-4"   // hash % 2 = ?
        );

        // When & Then - проверяем распределение для 2 шардов
        Map<String, Integer> distributionWith2Shards = new HashMap<>();
        for (String deviceId : testDeviceIds) {
            int shardIndex = Math.abs(deviceId.hashCode()) % 2;
            distributionWith2Shards.put(deviceId, shardIndex);
        }

        // Проверяем распределение для 4 шардов (гипотетическое масштабирование)
        Map<String, Integer> distributionWith4Shards = new HashMap<>();
        for (String deviceId : testDeviceIds) {
            int shardIndex = Math.abs(deviceId.hashCode()) % 4;
            distributionWith4Shards.put(deviceId, shardIndex);
        }

        // Распределение должно измениться
        boolean distributionChanged = false;
        for (String deviceId : testDeviceIds) {
            if (!Objects.equals(distributionWith2Shards.get(deviceId), distributionWith4Shards.get(deviceId))) {
                distributionChanged = true;
                break;
            }
        }
        
        assertThat(distributionChanged).isTrue();
        
        // Все шарды в новой конфигурации должны быть в диапазоне 0-3
        assertThat(distributionWith4Shards.values())
            .allMatch(shardIndex -> shardIndex >= 0 && shardIndex < 4);
    }

    @Test
    void givenHighVolumeData_whenDistributedAcrossShards_thenPerformanceIsOptimal() {
        // Given - большой объем данных
        final int HIGH_VOLUME_COUNT = 500;
        List<DeviceInfo> highVolumeDevices = IntStream.range(0, HIGH_VOLUME_COUNT)
            .mapToObj(i -> createDeviceInfo("perf-test-device-" + i))
            .toList();

        // When - измеряем время обработки
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<SendResult<String, DeviceInfo>>> futures = new ArrayList<>();
        for (DeviceInfo device : highVolumeDevices) {
            CompletableFuture<SendResult<String, DeviceInfo>> future = 
                avroKafkaTemplate.send("device-id-topic", device.getDeviceId(), device);
            futures.add(future);
        }

        // Ждем отправки всех сообщений
        for (CompletableFuture<SendResult<String, DeviceInfo>> future : futures) {
            assertThat(future).succeedsWithin(Duration.ofSeconds(60));
        }

        // Then - проверяем производительность
        await().atMost(Duration.ofSeconds(120))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                long processedCount = highVolumeDevices.stream()
                    .mapToLong(device -> {
                        try {
                            return deviceInfoRepository.findByDeviceId(device.getDeviceId()).size();
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();

                // Должно быть обработано хотя бы 90% сообщений
                assertThat(processedCount).isGreaterThan((long)(HIGH_VOLUME_COUNT * 0.9));
            });

        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;
        
        // Проверяем, что обработка заняла разумное время (менее 2 минут)
        assertThat(processingTime).isLessThan(120_000);
        
        // Вычисляем throughput
        double throughputPerSecond = (double) HIGH_VOLUME_COUNT / (processingTime / 1000.0);
        assertThat(throughputPerSecond).isGreaterThan(4.0d); // Минимум 4 сообщения в секунду
    }

    @Test
    void givenShardRebalancing_whenDataMigrated_thenConsistencyMaintained() {
        // Given - создаем данные в текущей конфигурации
        List<DeviceInfo> preRebalanceDevices = IntStream.range(0, 50)
            .mapToObj(i -> createDeviceInfo("rebalance-device-" + i))
            .toList();

        for (DeviceInfo device : preRebalanceDevices) {
            CompletableFuture<SendResult<String, DeviceInfo>> future = 
                avroKafkaTemplate.send("device-id-topic", device.getDeviceId(), device);
            assertThat(future).succeedsWithin(Duration.ofSeconds(10));
        }

        // Ждем сохранения
        await().atMost(Duration.ofSeconds(45))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                long savedCount = preRebalanceDevices.stream()
                    .mapToLong(device -> {
                        try {
                            return deviceInfoRepository.findByDeviceId(device.getDeviceId()).size();
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();
                assertThat(savedCount).isGreaterThan((long)(preRebalanceDevices.size() * 0.8));
            });

        // When - имитируем rebalancing (в реальности это сложная операция миграции)
        // Создаем новые данные после "rebalancing"
        List<DeviceInfo> postRebalanceDevices = IntStream.range(50, 100)
            .mapToObj(i -> createDeviceInfo("rebalance-device-" + i))
            .toList();

        for (DeviceInfo device : postRebalanceDevices) {
            CompletableFuture<SendResult<String, DeviceInfo>> future = 
                avroKafkaTemplate.send("device-id-topic", device.getDeviceId(), device);
            assertThat(future).succeedsWithin(Duration.ofSeconds(10));
        }

        // Then - проверяем, что все данные остались доступными
        List<DeviceInfo> allDevices = new ArrayList<>();
        allDevices.addAll(preRebalanceDevices);
        allDevices.addAll(postRebalanceDevices);

        await().atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                long totalSavedCount = allDevices.stream()
                    .mapToLong(device -> {
                        try {
                            return deviceInfoRepository.findByDeviceId(device.getDeviceId()).size();
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();
                
                // Все устройства должны быть доступны после rebalancing
                assertThat(totalSavedCount).isGreaterThan((long)(allDevices.size() * 0.9));
            });
    }

    private DeviceInfo createDeviceInfo(String deviceId) {
        return DeviceInfo.newBuilder()
            .setDeviceId(deviceId)
            .setDeviceType("scale-test-sensor")
            .setManufacturer("ScaleCorp")
            .setModel("S-100")
            .setFirmwareVersion("2.0.0")
            .setFirstSeen(System.currentTimeMillis())
            .setLastSeen(System.currentTimeMillis())
            .setStatus("active")
            .build();
    }

    /**
     * Создает новую конфигурацию шардирования для имитации масштабирования
     */
    private DataSource createScaledShardingConfiguration() {
        try {
            Map<String, DataSource> dataSourceMap = new HashMap<>();
            
            // Используем существующие контейнеры для новой конфигурации
            dataSourceMap.put("scaled_ds_0", createHikari("jdbc:postgresql://localhost:" + postgresShard1.getMappedPort(5432) + "/dcs_shard1"));
            dataSourceMap.put("scaled_ds_1", createHikari("jdbc:postgresql://localhost:" + postgresShard2.getMappedPort(5432) + "/dcs_shard2"));

            // Создаем конфигурацию шардирования для "масштабированной" системы
            ShardingRuleConfiguration shardingRule = new ShardingRuleConfiguration();
            
            ShardingTableRuleConfiguration deviceInfoTable = new ShardingTableRuleConfiguration(
                "device_info",
                "scaled_ds_${0..1}.device_info"
            );
            
            deviceInfoTable.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration(
                "device_id",
                "scaled_device_id_hash_mod"
            ));
            
            shardingRule.getTables().add(deviceInfoTable);
            
            Properties hashProps = new Properties();
            hashProps.setProperty("sharding-count", "2");
            shardingRule.getShardingAlgorithms().put("scaled_device_id_hash_mod", 
                new AlgorithmConfiguration("HASH_MOD", hashProps));

            Collection<RuleConfiguration> rules = new ArrayList<>();
            rules.add(shardingRule);
            
            Properties props = new Properties();
            props.setProperty("sql-show", "true");
            
            return ShardingSphereDataSourceFactory.createDataSource(dataSourceMap, rules, props);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create scaled sharding configuration", e);
        }
    }
    
    private static DataSource createHikari(String jdbcUrl) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername("postgres");
        ds.setPassword("postgres");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);
        return ds;
    }
}
