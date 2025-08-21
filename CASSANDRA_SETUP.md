# Настройка Cassandra с автоматической инициализацией

## Проблема
При чистом запуске инфраструктуры Cassandra стартует без создания namespace `ecs` и таблицы `device_events_by_device`, описанной в `schema.cql`.

## Решение
Создан bash-скрипт `cassandra-init.sh` и Docker-контейнер `cassandra-init`, который автоматически выполняется после запуска Cassandra и создает необходимые схемы.

## Компоненты решения

### 1. cassandra-init.sh
Bash-скрипт, который:
- Выполняет схему из `schema.cql`
- Проверяет успешность создания схемы

### 2. cassandra-init.Dockerfile
Dockerfile для создания контейнера-инициализатора на базе Cassandra.

### 3. Обновленный docker-compose.yaml
- Добавлен сервис `cassandra-init`
- Сервис `ecs` теперь зависит от успешного завершения инициализации Cassandra

## Использование

### Запуск всей инфраструктуры
```bash
make up
```

### Только инициализация Cassandra
```bash
make cassandra-init
```

### Просмотр логов Cassandra
```bash
make cassandra-logs
```

### Открытие Cassandra shell
```bash
make cassandra-shell
```

### Остановка и очистка
```bash
make down
make clean
```

## Принцип работы

1. Запускается контейнер `cassandra`
2. После готовности Cassandra запускается `cassandra-init`
3. `cassandra-init` выполняет схемы и завершается
4. Сервис `ecs` запускается только после успешной инициализации Cassandra

## Проверка работы

После запуска можно проверить создание схемы:

```bash
# Подключиться к Cassandra
make cassandra-shell

# В Cassandra shell выполнить:
DESCRIBE KEYSPACE ecs;
DESCRIBE TABLE ecs.device_events_by_device;
```

## Troubleshooting

### Если схема не создается
1. Проверить логи инициализации:
   ```bash
   docker-compose logs cassandra-init
   ```

2. Проверить готовность Cassandra:
   ```bash
   docker-compose logs cassandra
   ```

3. Перезапустить инициализацию:
   ```bash
   make cassandra-init
   ```

### Очистка и перезапуск
```bash
make clean
make up
```
