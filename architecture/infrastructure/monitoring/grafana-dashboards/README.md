# Grafana Dashboards для IoT Platform

Этот каталог содержит дашборды Grafana для мониторинга различных компонентов IoT платформы.

## Доступные дашборды

### 1. Event Collector Service Dashboard
**Файл:** `Event Collector Service Dashboard.json`

Комплексный дашборд для мониторинга сервиса сбора событий (`events-collector-service`).

#### Метрики Kafka:
- **Kafka Consumer Processing Rate** - скорость обработки сообщений потребителем
- **Kafka Messages Processed per Second** - количество обработанных сообщений в секунду
- **Kafka Producer Messages Sent per Second** - количество отправленных сообщений в секунду

#### Метрики Cassandra:
- **Cassandra Query Execution Time** - время выполнения запросов к базе данных
- **Cassandra Queries per Second** - количество запросов в секунду

#### Метрики кэша:
- **Caffeine Cache Size** - размер кэша устройств
- **Caffeine Cache Hit Rate** - частота попаданий в кэш

#### HTTP метрики:
- **HTTP Request Processing Time** - время обработки HTTP запросов
- **HTTP Requests per Second** - количество HTTP запросов в секунду

#### JVM метрики:
- **JVM Memory Usage** - использование памяти JVM
- **JVM GC Collection Time** - время сборки мусора
- **Process CPU Usage** - использование CPU процессом
- **Process Memory Usage** - использование памяти процессом

### 2. Kafka Dashboard
**Файл:** `Kafka dashboard.json`

Дашборд для мониторинга кластера Apache Kafka.

### 3. PostgreSQL Dashboard
**Файл:** `PostgreSQL dashboard.json`

Дашборд для мониторинга базы данных PostgreSQL.

### 4. Prometheus Stats Dashboard
**Файл:** `Prometheus stats dashboard.json`

Дашборд для мониторинга самого Prometheus.

## Установка и настройка

### Автоматический импорт
Дашборды автоматически импортируются при запуске Grafana благодаря настроенному provisioning.

### Ручной импорт
1. Откройте Grafana в браузере
2. Перейдите в **Dashboards** → **Import**
3. Загрузите JSON файл нужного дашборда
4. Настройте источник данных Prometheus
5. Сохраните дашборд

## Источники данных

Все дашборды используют Prometheus как источник данных. Убедитесь, что:
- Prometheus запущен и доступен
- Метрики приложений собираются и отправляются в Prometheus
- В Grafana настроен источник данных Prometheus

## Переменные и фильтры

Дашборды поддерживают:
- Фильтрацию по времени (по умолчанию: последний час)
- Автообновление каждые 5 секунд
- Легенды с детализацией по меткам

## Требования

- Grafana 12.1.0+
- Prometheus 1.0.0+
- Spring Boot Actuator с включенными метриками
- Micrometer Prometheus registry

## Теги

Дашборды помечены тегами для удобной организации:
- `iot` - IoT платформа
- `events` - сервисы событий
- `kafka` - Apache Kafka
- `cassandra` - Apache Cassandra
- `spring-boot` - Spring Boot приложения

