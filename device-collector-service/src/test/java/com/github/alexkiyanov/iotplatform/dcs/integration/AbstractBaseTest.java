package com.github.alexkiyanov.iotplatform.dcs.integration;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.flywaydb.core.Flyway;

public abstract class AbstractBaseTest {

    private static final Network NETWORK = Network.newNetwork();

    // PostgreSQL шарды с репликами
    @Container
    static final PostgreSQLContainer<?> postgresShard1;

    @Container
    static final PostgreSQLContainer<?> postgresShard1Replica;

    @Container
    static final PostgreSQLContainer<?> postgresShard2;

    @Container
    static final PostgreSQLContainer<?> postgresShard2Replica;

    @Container
    static final KafkaContainer kafka;

    @Container
    static final GenericContainer<?> registry;


    static {
        // PostgreSQL шард 1 (основной)
        postgresShard1 = new PostgreSQLContainer<>("postgres:17")
                .withNetwork(NETWORK)
                .withNetworkAliases("postgres-shard1")
                .withDatabaseName("dcs_shard1")
                .withUsername("postgres")
                .withPassword("postgres")
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 1))
                .withReuse(true);

        // PostgreSQL шард 1 (реплика)
        postgresShard1Replica = new PostgreSQLContainer<>("postgres:17")
                .withNetwork(NETWORK)
                .withNetworkAliases("postgres-shard1-replica")
                .withDatabaseName("dcs_shard1_replica")
                .withUsername("postgres")
                .withPassword("postgres")
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 1))
                .withReuse(true);

        // PostgreSQL шард 2 (основной)
        postgresShard2 = new PostgreSQLContainer<>("postgres:17")
                .withNetwork(NETWORK)
                .withNetworkAliases("postgres-shard2")
                .withDatabaseName("dcs_shard2")
                .withUsername("postgres")
                .withPassword("postgres")
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 1))
                .withReuse(true);

        // PostgreSQL шард 2 (реплика)
        postgresShard2Replica = new PostgreSQLContainer<>("postgres:17")
                .withNetwork(NETWORK)
                .withNetworkAliases("postgres-shard2-replica")
                .withDatabaseName("dcs_shard2_replica")
                .withUsername("postgres")
                .withPassword("postgres")
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 1))
                .withReuse(true);

        DockerImageName kafkaImage = DockerImageName.parse("apache/kafka-native:3.8.0");
        kafka = new KafkaContainer(kafkaImage)
                .withNetwork(NETWORK)
                .withNetworkAliases("kafka")
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1))
                .withReuse(true);

        registry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:8.0.0"))
                .withNetwork(NETWORK)
                .withNetworkAliases("schema-registry")
                .withExposedPorts(8081)
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9093")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_TIMEOUT_MS", "120000")
                .withEnv("SCHEMA_REGISTRY_DEBUG", "true")
                .dependsOn(kafka)
                .waitingFor(
                        Wait.forLogMessage(".*Server started, listening for requests.*", 1)
                                .withStartupTimeout(Duration.ofMinutes(3))
                )
                .withReuse(false);

        postgresShard1.start();
        postgresShard1Replica.start();
        postgresShard2.start();
        postgresShard2Replica.start();

        // Flyway миграции для всех БД (инициализация схем)
        migrateWithFlyway("jdbc:postgresql://localhost:" + postgresShard1.getMappedPort(5432) + "/dcs_shard1", "postgres", "postgres");
        migrateWithFlyway("jdbc:postgresql://localhost:" + postgresShard1Replica.getMappedPort(5432) + "/dcs_shard1_replica", "postgres", "postgres");
        migrateWithFlyway("jdbc:postgresql://localhost:" + postgresShard2.getMappedPort(5432) + "/dcs_shard2", "postgres", "postgres");
        migrateWithFlyway("jdbc:postgresql://localhost:" + postgresShard2Replica.getMappedPort(5432) + "/dcs_shard2_replica", "postgres", "postgres");

        kafka.start();
        waitForKafkaReady(kafka.getBootstrapServers(), Duration.ofSeconds(60));
        createKafkaTopics();
        registry.start();
    }

    private static void migrateWithFlyway(String url, String user, String password) {
        Flyway.configure()
                .locations("classpath:db/migration")
                .dataSource(url, user, password)
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    private static void createKafkaTopics() {
        Map<String, Object> config = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(config)) {
            List<NewTopic> topics = List.of(
                    new NewTopic("device-id-topic", 1, (short) 1),
                    new NewTopic("device-info-topic", 1, (short) 1),
                    new NewTopic("device-id-dlt", 1, (short) 1)
            );
            admin.createTopics(topics).all().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Kafka topics", e);
        }
    }

    private static void waitForKafkaReady(String bootstrap, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        RuntimeException lastError = null;
        while (System.currentTimeMillis() < deadline) {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            try (AdminClient admin = AdminClient.create(props)) {
                admin.listTopics().names().get(5, java.util.concurrent.TimeUnit.SECONDS);
                return;
            } catch (Exception e) {
                lastError = new RuntimeException(e);
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        if (lastError != null) throw lastError;
    }


    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        // Настройка для использования ShardingSphere JDBC драйвера напрямую
        // Эти свойства будут переопределены в TestShardingSphereConfig
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + postgresShard1.getMappedPort(5432) + "/dcs_shard1");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Активируем профиль интеграционных тестов
        registry.add("spring.profiles.active", () -> "integration-test");
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.sql.init.enabled", () -> false);
        // Отключаем dynamic-datasource автоконфигурацию
        registry.add("spring.autoconfigure.exclude", () -> "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration");
        // Отключаем проверку health DB в тестах (мешает инициализации)
        registry.add("management.health.db.enabled", () -> false);

        // Kafka properties
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.hostname", kafka::getHost);
        registry.add("spring.kafka.consumer.group-id", () -> "test-dcs-consumer-group");
        registry.add("spring.kafka.consumer.enable-auto-commit", () -> false);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.key-deserializer", () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer", () -> "io.confluent.kafka.serializers.KafkaAvroDeserializer");
        registry.add("spring.kafka.consumer.properties.specific.avro.reader", () -> true);
        registry.add("spring.kafka.consumer.properties.schema.registry.url", () -> "http://localhost:" + AbstractBaseTest.registry.getMappedPort(8081));

        // Kafka listener properties
        registry.add("spring.kafka.listener.ack-mode", () -> "MANUAL");
        registry.add("spring.kafka.listener.type", () -> "BATCH");
        registry.add("spring.kafka.listener.concurrency", () -> 1);

        // Kafka producer properties
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", () -> "io.confluent.kafka.serializers.KafkaAvroSerializer");

        registry.add("spring.kafka.properties.schema.registry.url",
                () -> "http://localhost:" + AbstractBaseTest.registry.getMappedPort(8081));

        registry.add("spring.kafka.consumer.properties.schema.registry.url",
                () -> "http://localhost:" + AbstractBaseTest.registry.getMappedPort(8081));

        registry.add("spring.kafka.producer.properties.schema.registry.url",
                () -> "http://localhost:" + AbstractBaseTest.registry.getMappedPort(8081));
        
        // App specific properties
        registry.add("app.topics.input", () -> "device-id-topic");
        registry.add("app.topics.output", () -> "device-info-topic");
        registry.add("app.topics.dead-letter", () -> "device-id-dlt");
        registry.add("app.cache.deviceInfoTtl", () -> 1440);
        registry.add("app.retry.max-attempts", () -> 3);
        registry.add("app.retry.initial-delay-ms", () -> 1000);
        registry.add("app.retry.max-delay-ms", () -> 10000);
        registry.add("app.retry.multiplier", () -> 2.0);
        
        // Даем Hikari время на установку соединения к Proxy
        registry.add("spring.datasource.hikari.initialization-fail-timeout", () -> 60000);
    }

    @Autowired
    private Cache<String, Boolean> deviceInfoCache;

    @AfterEach
    void clearCache() {
        if (deviceInfoCache != null) {
            deviceInfoCache.invalidateAll();
        }
    }

    /**
     * Проверяет, что все основные контейнеры запущены и готовы к работе
     */
    protected void ensureContainersAreRunning() {
        if (!postgresShard1.isRunning()) {
            postgresShard1.start();
        }
        if (!postgresShard1Replica.isRunning()) {
            postgresShard1Replica.start();
        }
        if (!postgresShard2.isRunning()) {
            postgresShard2.start();
        }
        if (!postgresShard2Replica.isRunning()) {
            postgresShard2Replica.start();
        }
        if (!kafka.isRunning()) {
            kafka.start();
        }
        if (!registry.isRunning()) {
            registry.start();
        }
    }

    /**
     * Безопасно останавливает контейнер с повторными попытками
     */
    protected void safeStopContainer(org.testcontainers.containers.GenericContainer<?> container) {
        try {
            if (container.isRunning()) {
                container.stop();
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to stop container: " + e.getMessage());
        }
    }

    /**
     * Безопасно запускает контейнер с повторными попытками
     */
    protected void safeStartContainer(org.testcontainers.containers.GenericContainer<?> container) {
        try {
            if (!container.isRunning()) {
                container.start();
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to start container: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Ждет готовности контейнера к работе
     */
    protected void waitForContainerReady(org.testcontainers.containers.GenericContainer<?> container, Duration timeout) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout.toMillis()) {
            if (container.isRunning()) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for container", e);
            }
        }
        throw new RuntimeException("Container did not become ready within timeout: " + timeout);
    }

}
