#!/bin/bash

# Скрипт для инициализации Cassandra
# Cassandra уже готова благодаря healthcheck в docker-compose
# Получаем параметры подключения из переменных окружения или используем значения по умолчанию
CASSANDRA_HOST=${CASSANDRA_HOST:-cassandra}
CASSANDRA_PORT=${CASSANDRA_PORT:-9042}

echo "Connecting to Cassandra at ${CASSANDRA_HOST}:${CASSANDRA_PORT}"
echo "Cassandra is ready (healthcheck passed), proceeding with schema initialization..."

# Выполняем схему
cqlsh "${CASSANDRA_HOST}" "${CASSANDRA_PORT}" -f /schema/schema.cql

echo "Schema initialization completed successfully!"

# Проверяем, что схема создана
echo "Verifying schema creation..."
cqlsh "${CASSANDRA_HOST}" "${CASSANDRA_PORT}" -e "DESCRIBE KEYSPACE ecs"
cqlsh "${CASSANDRA_HOST}" "${CASSANDRA_PORT}" -e "DESCRIBE TABLE ecs.device_events_by_device"

echo "Schema creation completed!"
