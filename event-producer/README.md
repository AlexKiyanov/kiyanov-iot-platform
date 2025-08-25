# Event Producer

Временный продюсер сообщений в Kafka для тестирования events-collector-service.

## Описание

Этот модуль генерирует тестовые события IoT устройств и отправляет их в Kafka топик `events` каждую секунду. События используют Avro схему `DeviceEvent` и содержат случайные данные от 10 различных устройств.

## Функциональность

- Автоматическая генерация событий каждую секунду
- 10 предопределенных deviceId (device-001 до device-010)
- Случайный выбор типа события и payload
- Использование Avro схемы для сериализации
- REST API для управления продюсером

## Сборка

```bash
./gradlew build
```

## Запуск

### Локально
```bash
./gradlew bootRun
```

### В Docker
```bash
docker build -t event-producer .
docker run -p 8091:8091 event-producer
```

## REST API

- `GET /api/producer/status` - статус продюсера
- `POST /api/producer/start` - запуск продюсера
- `POST /api/producer/stop` - остановка продюсера
- `POST /api/producer/send-single` - отправка одного события

## Конфигурация

Основные настройки в `application.yaml`:

- `server.port`: порт приложения (8091)
- `app.producer.interval-ms`: интервал между событиями (мс)
- `app.producer.device-ids`: список deviceId для генерации событий
- `spring.kafka.bootstrap-servers`: адреса Kafka брокеров
- `spring.kafka.producer.properties.schema.registry.url`: URL Schema Registry

## Типы событий

- TEMPERATURE_READING
- HUMIDITY_READING
- PRESSURE_READING
- MOTION_DETECTED
- DOOR_OPENED
- WINDOW_OPENED
- LIGHT_ON
- LIGHT_OFF
- BATTERY_LOW
- CONNECTION_LOST
