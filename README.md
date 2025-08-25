# kiyanov-iot-platform

## Описание

IoT Microservice Platform – учебный проект, демонстрирующий архитектуру микросервисов для IoT. Содержит инфраструктуру (
Kafka, PostgreSQL, Grafana и др.) и пример реализации процессов IoT.

## Структура проекта

```
kiyanov-iot-platform/
├── architecture/                    # Архитектурные диаграммы и инфраструктура
│   ├── diagrams/                   # PlantUML диаграммы
│   └── infrastructure/             # Docker Compose и конфигурации
│       ├── monitoring/             # Конфигурации мониторинга
│       │   ├── alloy/              # Конфигурация Alloy
│       │   ├── grafana-dashboards/ # Дашборды Grafana
│       │   ├── grafana-provisioning/
│       │   ├── loki/               # Конфигурация Loki
│       │   ├── prometheus/         # Конфигурация Prometheus
│       │   └── tempo/              # Конфигурация Tempo
│       ├── cassandra-init/         # Инициализация Cassandra
│       └── postgres-init/          # Инициализация PostgreSQL
├── events-collector-service/       # Сервис сбора событий IoT
│   ├── src/main/java/             # Java код
│   ├── src/main/resources/        # Конфигурации и схемы
│   ├── Dockerfile                 # Docker образ
│   └── build.gradle               # Gradle конфигурация
├── event-producer/                # Генератор тестовых событий
│   ├── src/main/java/             # Java код
│   ├── src/main/resources/        # Конфигурации
│   ├── Dockerfile                 # Docker образ
│   └── build.gradle               # Gradle конфигурация
├── docker-compose.yaml            # Основной файл инфраструктуры
├── Makefile                       # Команды для управления
├── QUICK_START.md                 # Руководство по быстрому запуску
├── CASSANDRA_SETUP.md             # Настройка Cassandra
└── README.md
```

## Быстрый старт через Docker Compose

1. Скопируйте .env.example в .env и укажите необходимые переменные (или используйте значения по умолчанию)
2. Перейдите в infrastructure
3. Запустите сборку docker-compose

```bash
cp architecture/.env.example architecture/infrastructure/.env 
cd architecture/infrastructure
docker-compose up -d
```

### Makefile
Makefile упрощает работу с сервисами Docker Compose для IoT платформы.  
Все команды используют файл `docker-compose.yaml` в корне проекта.

#### Основные команды

| Команда      | Описание                                                                                                                                              |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `make up`    | Поднимает все сервисы в фоне (`docker-compose up -d`).                                                                                                |
| `make down`  | Останавливает и удаляет контейнеры, тома остаются.                                                                                                    |
| `make logs`  | Просмотр логов всех сервисов.                                                                                                                         |
| `make ps`    | Показывает список запущенных контейнеров.                                                                                                             |
| `make reset` | Полный сброс сервисов: останавливает и удаляет контейнеры и тома, <br/> затем поднимает их заново (`docker-compose down -v && docker-compose up -d`). |

#### Команды для Cassandra

| Команда              | Описание                                                                                    |
|----------------------|---------------------------------------------------------------------------------------------|
| `make cassandra-init` | Инициализация схемы Cassandra (создание keyspace и таблиц).                                |
| `make cassandra-logs` | Просмотр логов только Cassandra.                                                            |
| `make cassandra-shell` | Подключение к Cassandra shell для выполнения CQL команд.                                   |

#### Команды очистки

| Команда    | Описание                                                                                    |
|------------|---------------------------------------------------------------------------------------------|
| `make clean` | Полная очистка: останавливает контейнеры, удаляет тома и неиспользуемые образы.           |

---

## Примеры использования

### Основные операции
```bash
# Поднять все сервисы
make up

# Остановить сервисы
make down

# Посмотреть логи
make logs

# Список запущенных контейнеров
make ps

# Полный сброс и перезапуск сервисов
make reset
```

### Работа с Cassandra
```bash
# Инициализация схемы Cassandra
make cassandra-init

# Просмотр логов Cassandra
make cassandra-logs

# Подключение к Cassandra shell
make cassandra-shell
```

### Очистка и обслуживание
```bash
# Полная очистка (контейнеры, тома, образы)
make clean

# Пересборка образов после изменений
docker-compose build

# Пересборка конкретного сервиса
docker-compose build events-collector-service
```

## Endpoints:

### Инфраструктура
* Postgres (localhost:5432)
* Postgres Exporter (localhost:9187)
* Keycloak (localhost:9091, логин: admin/admin)
* Kafka1 (localhost:9092)
* Kafka2 (localhost:9093)
* Kafka3 (localhost:9094)
* Kafka Exporter (localhost:9308)
* Schema Registry (localhost:8081)
* Kafka UI (localhost:8070)
* Prometheus (localhost:9090)
* Grafana (localhost:3000, логин: admin/admin)
* Loki (localhost:3100)
* Alloy (localhost:9080, localhost:4317, localhost:4318)
* Tempo (localhost:3200)
* Minio (localhost:9000, localhost:9001, логин: tempo/tempo-tempo)
* Camunda (localhost:8088)
* Redis (localhost:6379)
* Cassandra (localhost:9042)

### Микросервисы
* **Events Collector Service** (localhost:8090) - API для сбора и обработки событий IoT
* **Event Producer** (localhost:8091) - API для генерации тестовых событий

## Технологии

### Backend
* **Java 24** + Spring Boot 3
* **Apache Kafka** - брокер сообщений
* **Apache Cassandra** - NoSQL база данных
* **PostgreSQL** - реляционная база данных
* **Redis** - кэширование

### Мониторинг и наблюдаемость
* **Prometheus** - сбор метрик
* **Grafana** - визуализация
* **Loki** - логирование
* **Tempo** - трассировка
* **Alloy** - агент телеметрии

### Инфраструктура
* **Docker** + **Docker Compose**
* **Keycloak** - аутентификация
* **Camunda** - BPM
* **MinIO** - объектное хранилище

### Схемы данных
* **Apache Avro** - сериализация событий
* **Schema Registry** - управление схемами

## Документация

- [QUICK_START.md](QUICK_START.md) - подробное руководство по запуску
- [CASSANDRA_SETUP.md](CASSANDRA_SETUP.md) - настройка Cassandra
- [events-collector-service/README.md](events-collector-service/README.md) - документация Events Collector Service

## Разработка

### Предварительные требования

- **Java 24** - для разработки микросервисов
- **Docker & Docker Compose** - для запуска инфраструктуры
- **Make** - для удобного управления (опционально)

### Сборка сервисов

```bash
# Events Collector Service
cd events-collector-service
./gradlew build

# Event Producer
cd event-producer
./gradlew build
```

### Тестирование

```bash
# Unit тесты
./gradlew test

# Integration тесты
./gradlew integrationTest

# Все тесты с покрытием
./gradlew fullTest
```

### Пересборка образов

После изменения Java кода необходимо пересобрать образы:

```bash
# Пересборка всех сервисов
docker-compose build

# Пересборка конкретного сервиса
docker-compose build events-collector-service
docker-compose build event-producer
```

### Локальная разработка

1. **Запуск инфраструктуры:**
   ```bash
   make up
   ```

2. **Запуск сервиса локально:**
   ```bash
   cd events-collector-service
   ./gradlew bootRun
   ```

3. **Проверка работы:**
   ```bash
   curl http://localhost:8090/actuator/health
   ```

### Отладка

- **Логи сервисов**: `make logs`
- **Метрики**: http://localhost:8090/actuator/prometheus
- **Grafana дашборды**: http://localhost:3000
- **Kafka UI**: http://localhost:8070

## Автор

[Aleksey Kiyanov](https://t.me/fuku_ro_u)