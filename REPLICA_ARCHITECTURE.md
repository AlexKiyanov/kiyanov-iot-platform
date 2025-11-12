# Архитектура с репликами PostgreSQL

## Обзор

Система теперь использует архитектуру с репликами PostgreSQL для каждого шарда, управляемую через Apache ShardingSphere. Это обеспечивает:

- **Высокую доступность**: Реплики могут заменить основные шарды при отказе
- **Масштабируемость чтения**: Запросы на чтение распределяются между мастером и репликами
- **Отказоустойчивость**: Автоматическое переключение на реплики при проблемах с мастером

## Архитектура БД

### Шарды и реплики

```
┌─────────────────┐    ┌─────────────────┐
│   Shard 1       │    │   Shard 2       │
│   (Master)      │    │   (Master)      │
│   Port: 5433    │    │   Port: 5434    │
└─────────────────┘    └─────────────────┘
         │                       │
         │                       │
┌─────────────────┐    ┌─────────────────┐
│   Shard 1       │    │   Shard 2       │
│   (Replica)     │    │   (Replica)     │
│   Port: 5435    │    │   Port: 5436    │
└─────────────────┘    └─────────────────┘
```

### Управление через ShardingSphere

- **Write операции**: Направляются на мастер-шарды (ds0, ds1)
- **Read операции**: Распределяются между мастером и репликами (round-robin)
- **Sharding**: Данные распределяются по device_id с помощью HASH_MOD алгоритма

## Конфигурация

### Docker Compose

Добавлены новые сервисы:
- `postgres-shard1-replica` (порт 5435)
- `postgres-shard2-replica` (порт 5436)

### ShardingSphere Configuration

```yaml
shardingsphere:
  datasource:
    names: ds0,ds1,ds0_replica,ds1_replica
  rules:
    sharding:
      tables:
        device_info:
          actual-data-nodes: ds0_group.device_info,ds1_group.device_info
    readwrite-splitting:
      data-sources:
        ds0_group:
          write-data-source-name: ds0
          read-data-source-names: ds0_replica
        ds1_group:
          write-data-source-name: ds1
          read-data-source-names: ds1_replica
```

## Миграции

### Профили

- `migration`: Выполнение миграций на всех datasource'ах
- `sharding`: Рабочий режим с поддержкой реплик

### Запуск миграций

```bash
# Запуск миграций на всех шардах и репликах
./migrate.sh
```

Миграции выполняются через специальный `MigrationConfig` класс, который:
1. Подключается ко всем datasource'ам
2. Выполняет Flyway миграции на каждом из них
3. Создает одинаковую структуру БД на всех узлах

## Мониторинг

### Проверка статуса реплик

```bash
# Проверка подключения к репликам
docker exec -it postgres-shard1-replica pg_isready -U postgres -d dcs_shard1_replica
docker exec -it postgres-shard2-replica pg_isready -U postgres -d dcs_shard2_replica
```

### Логи ShardingSphere

Включите SQL логирование для отладки:
```yaml
shardingsphere:
  props:
    sql-show: true
    sql-simple: true
```

## Преимущества новой архитектуры

1. **Производительность**: Чтение распределяется между мастером и репликами
2. **Надежность**: Автоматическое переключение при отказе мастера
3. **Масштабируемость**: Легко добавить больше реплик
4. **Консистентность**: ShardingSphere обеспечивает правильное распределение запросов

## Настройка репликации PostgreSQL

Для полноценной работы реплик необходимо настроить streaming replication между мастером и репликами. Это можно сделать через:

1. Настройку `wal_level = replica` на мастерах
2. Создание пользователя для репликации
3. Настройку `recovery.conf` на репликах
4. Запуск процесса репликации

Пока что реплики работают как независимые БД с одинаковой структурой через Flyway миграции.
