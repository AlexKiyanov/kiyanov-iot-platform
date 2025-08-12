# kiyanov-iot-platform

## Описание

IoT Microservice Platform – учебный проект, демонстрирующий архитектуру микросервисов для IoT. Содержит инфраструктуру (Kafka, PostgreSQL, Grafana и др.) и пример реализации процессов IoT.

## Структура проекта

```
kiyanov-iot-platform/
├── architecture
│   ├── diagrams
│   ├── infrastructure
│   │   ├── monitoring
│   │   │   ├── alloy
│   │   │   ├── loki
│   │   │   ├── prometeus
│   │   │   └── tempo
│   │   ├── postrges-init
│   │   ├── docker-compose.yml
│   │   └── ~~.env~~ должен быть здесь
│   └──.env.example
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

## Endpoints:

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

## Технологии

* PostgreSQL
* Kafka
* Prometheus / Grafana
* Loki (логирование)
* Tempo / Alloy (tracing)
* Docker / Docker Compose
* MinIO
* Camunda
* Cassandra
* Redis
* Keycloak

## Автор
[Aleksey Kiyanov](t.me/fuku_ro_u)