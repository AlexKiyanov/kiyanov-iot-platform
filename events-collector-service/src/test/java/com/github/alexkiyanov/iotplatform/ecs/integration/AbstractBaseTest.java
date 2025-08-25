package com.github.alexkiyanov.iotplatform.ecs.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public abstract class AbstractBaseTest {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    static final CassandraContainer cassandra;

    @Container
    static final KafkaContainer kafka;

    @Container
    static final GenericContainer<?> registry;

    static {
        cassandra = new CassandraContainer("cassandra:latest")
                .withNetwork(NETWORK)
                .withNetworkAliases("cassandra")
                .withInitScript("schema-integration-test.cql")
                .withReuse(true);

        kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
                .withNetwork(NETWORK)
                .withNetworkAliases("kafka")
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1))
                .withReuse(true);

        registry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.0"))
                .withNetwork(NETWORK)
                .withNetworkAliases("schema-registry")
                .withExposedPorts(8081)
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                // слушаем на всех интерфейсах контейнера
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                // ВНУТРЕННЕЕ подключение к Kafka по 9093 (контейнер↔контейнер)
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9093")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_TIMEOUT_MS", "10000")
                .withEnv("SCHEMA_REGISTRY_DEBUG", "true")
                .dependsOn(kafka)
                .waitingFor(
                        Wait.forLogMessage(".*Server started, listening for requests.*", 1)
                                .withStartupTimeout(Duration.ofMinutes(3))
                )
                .withReuse(true);


        cassandra.start();
        kafka.start();
        registry.start();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        // Cassandra properties
        registry.add("spring.cassandra.contact-points", () -> cassandra.getContactPoint().getHostString() + ":" + cassandra.getContactPoint().getPort());
        registry.add("spring.cassandra.local-datacenter", cassandra::getLocalDatacenter);
        registry.add("spring.cassandra.keyspace-name", () -> "ecs");
        registry.add("spring.cassandra.cluster-name", () -> "test-cluster");
        registry.add("spring.cassandra.dc-name", cassandra::getLocalDatacenter);

        // Kafka properties
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.hostname", kafka::getHost);
        registry.add("spring.kafka.consumer.group-id", () -> "test-consumer-group");
        registry.add("spring.kafka.consumer.enable-auto-commit", () -> false);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.key-deserializer", () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer", () -> "io.confluent.kafka.serializers.KafkaAvroDeserializer");
        registry.add("spring.kafka.consumer.properties.specific.avro.reader", () -> true);
        registry.add("spring.kafka.consumer.properties.schema.registry.url", () -> "http://schema-registry:8081");

        // Kafka listener properties
        registry.add("spring.kafka.listener.ack-mode", () -> "BATCH");
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
        registry.add("app.topics.input", () -> "events");
        registry.add("app.topics.deviceId", () -> "device-id-topic");
        registry.add("app.cache.deviceIdTtl", () -> 1440);
    }

}
