# Быстрый старт IoT Platform

## Предварительные требования
- Docker и Docker Compose
- Make (опционально, но рекомендуется)

## Быстрый запуск

### 1. Запуск всей инфраструктуры
```bash
make up
```

Это запустит все сервисы, включая:
- PostgreSQL + Keycloak
- Kafka + Zookeeper + Schema Registry
- Cassandra + автоматическая инициализация схемы
- Prometheus + Grafana + Loki + Tempo
- Redis + Camunda
- Events Collector Service (ECS)

### 2. Проверка работы Cassandra
```bash
# Подключиться к Cassandra shell
make cassandra-shell

# В Cassandra выполнить:
DESCRIBE KEYSPACE ecs;
DESCRIBE TABLE ecs.device_events_by_device;
```

### 3. Просмотр логов
```bash
# Все сервисы
make logs

# Только Cassandra
make cassandra-logs
```

### 4. Остановка
```bash
make down
```

## Что нового

✅ **Автоматическая инициализация Cassandra** - схема создается автоматически при первом запуске

✅ **Правильная последовательность запуска** - ECS запускается только после готовности Cassandra

✅ **Удобные команды Make** - простые команды для управления инфраструктурой

## Troubleshooting

Если что-то пошло не так:
```bash
# Полная очистка и перезапуск
make clean
make up
```

## Детальная документация

См. `CASSANDRA_SETUP.md` для подробного описания настройки Cassandra.
