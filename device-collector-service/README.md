# Device Collector Service (DCS)

Сервис для сбора и управления информацией об устройствах IoT платформы.

## Описание

DCS (Device Collector Service) - это микросервис, который:
- Получает идентификаторы устройств из топика `device-id-topic`
- Создает и обновляет информацию об устройствах в шардированной PostgreSQL базе данных
- Публикует информацию об устройствах в топик `device-info-topic` в формате Avro

## Архитектура

### База данных
- **PostgreSQL с шардированием** через Apache ShardingSphere
- **2 шарда**: `postgres-shard1` и `postgres-shard2`
- **Шардирование по device_id** с использованием HASH_MOD алгоритма
- **Flyway** для управления миграциями схемы базы данных

### Kafka
- **Consumer**: `device-id-topic` (получает идентификаторы устройств)
- **Producer**: `device-info-topic` (публикует информацию об устройствах в Avro формате)
- **Schema Registry** для управления схемами Avro

### Мониторинг
- **Prometheus** метрики
- **OpenTelemetry** трейсинг
- **Actuator** endpoints для health checks

## Структура базы данных

### Таблица device_info
```sql
CREATE TABLE device_info (
    device_id VARCHAR(255) PRIMARY KEY,
    device_type VARCHAR(100),
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    firmware_version VARCHAR(50),
    first_seen TIMESTAMP,
    last_seen TIMESTAMP,
    status VARCHAR(50)
);
```

### Таблица device_metadata
```sql
CREATE TABLE device_metadata (
    device_id VARCHAR(255) PRIMARY KEY,
    location VARCHAR(255),
    environment VARCHAR(100),
    tags JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES device_info(device_id)
);
```

### Таблица device_statistics
```sql
CREATE TABLE device_statistics (
    device_id VARCHAR(255) NOT NULL,
    stat_date DATE NOT NULL,
    events_count BIGINT DEFAULT 0,
    last_event_time TIMESTAMP,
    avg_response_time_ms DECIMAL(10,2),
    error_count BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, stat_date),
    FOREIGN KEY (device_id) REFERENCES device_info(device_id)
);
```

## Конфигурация

### Профили
- `default` - локальная разработка
- `docker` - запуск в Docker контейнере
- `sharding` - конфигурация шардирования
- `migration` - профиль для миграций

### Переменные окружения
- `POSTGRES_USER` - пользователь PostgreSQL
- `POSTGRES_PASSWORD` - пароль PostgreSQL
- `KAFKA_CONSUMER_GROUP` - группа Kafka consumer (по умолчанию: dcs-consumer)
- `INPUT_TOPIC` - входящий топик (по умолчанию: device-id-topic)
- `OUTPUT_TOPIC` - исходящий топик (по умолчанию: device-info-topic)

## Запуск

### Локальная разработка
```bash
./gradlew bootRun
```

### Docker
```bash
docker-compose up dcs
```

### Миграции
```bash
./migrate.sh
```

## API Endpoints

- `GET /actuator/health` - Health check
- `GET /actuator/metrics` - Метрики Prometheus
- `GET /actuator/info` - Информация о приложении

## Мониторинг

### Метрики
- `spring_kafka_listener_seconds` - время обработки Kafka сообщений
- `spring_data_jpa_repository_invocations_seconds` - время выполнения JPA запросов
- `http_server_requests_seconds` - время обработки HTTP запросов

### Логирование
- Структурированные логи в JSON формате
- Интеграция с Loki через OpenTelemetry

## Разработка

### Добавление новых миграций
1. Создайте файл в `src/main/resources/db/migration/`
2. Используйте формат имени: `V{номер}__{описание}.sql`
3. Миграции автоматически применятся при запуске

### Тестирование
```bash
./gradlew test
./gradlew integrationTest
./gradlew fullTest
```

## Зависимости

- Spring Boot 3.5.4
- Apache ShardingSphere 5.2.1
- Flyway 9.x
- PostgreSQL Driver
- Spring Kafka
- Confluent Kafka Avro Serializer
- OpenTelemetry
- Micrometer Prometheus
