#!/bin/bash

# Скрипт для проверки доступных метрик в Spring Boot Actuator
# Запускать после запуска events-collector-service

echo "Проверка доступных метрик в Spring Boot Actuator..."
echo "=================================================="

# Проверяем, что сервис запущен
if ! curl -s http://localhost:8090/actuator/health > /dev/null; then
    echo "❌ Сервис не доступен на порту 8090"
    echo "Убедитесь, что events-collector-service запущен"
    exit 1
fi

echo "✅ Сервис доступен"

# Проверяем доступные endpoints
echo ""
echo "Доступные Actuator endpoints:"
curl -s http://localhost:8090/actuator | jq -r 'keys[]' 2>/dev/null || curl -s http://localhost:8090/actuator

# Проверяем Prometheus метрики
echo ""
echo "Проверка Prometheus метрик..."
echo "=============================="

# Получаем все метрики
METRICS=$(curl -s http://localhost:8090/actuator/prometheus)

# Проверяем наличие ключевых метрик
echo ""
echo "Проверка наличия ключевых метрик:"

# Kafka метрики
echo "📊 Kafka метрики:"
KAFKA_METRICS=$(echo "$METRICS" | grep -E "spring_kafka|kafka" | head -5)
if [ -n "$KAFKA_METRICS" ]; then
    echo "$KAFKA_METRICS"
else
    echo "❌ Kafka метрики не найдены"
fi

# Cassandra метрики
echo ""
echo "🗄️  Cassandra метрики:"
CASSANDRA_METRICS=$(echo "$METRICS" | grep -E "spring_data_repository|spring_data_cassandra|cassandra|datastax" | head -5)
if [ -n "$CASSANDRA_METRICS" ]; then
    echo "$CASSANDRA_METRICS"
else
    echo "❌ Cassandra метрики не найдены"
    echo "ℹ️  Возможно, Cassandra метрики не включены в конфигурации"
fi

# Caffeine cache метрики
echo ""
echo "💾 Caffeine Cache метрики:"
CACHE_METRICS=$(echo "$METRICS" | grep -E "caffeine|cache" | head -5)
if [ -n "$CACHE_METRICS" ]; then
    echo "$CACHE_METRICS"
else
    echo "❌ Cache метрики не найдены"
    echo "ℹ️  Возможно, Caffeine метрики не включены в конфигурации"
fi

# HTTP метрики
echo ""
echo "🌐 HTTP метрики:"
echo "$METRICS" | grep -E "http_server_requests|http" | head -5 || echo "❌ HTTP метрики не найдены"

# JVM метрики
echo ""
echo "☕ JVM метрики:"
echo "$METRICS" | grep -E "jvm_|process_" | head -5 || echo "❌ JVM метрики не найдены"

# Общее количество метрик
TOTAL_METRICS=$(echo "$METRICS" | grep -c "^[^#]" || echo "0")
echo ""
echo "📈 Общее количество метрик: $TOTAL_METRICS"

# Дополнительная диагностика
echo ""
echo "🔍 Дополнительная диагностика:"
echo "=============================="

# Проверяем все доступные метрики по категориям
echo ""
echo "📋 Все доступные метрики (первые 20):"
echo "$METRICS" | grep "^[^#]" | head -20

echo ""
echo "📊 Статистика по типам метрик:"
echo "JVM метрики: $(echo "$METRICS" | grep -c "jvm_" || echo "0")"
echo "HTTP метрики: $(echo "$METRICS" | grep -c "http_" || echo "0")"
echo "Kafka метрики: $(echo "$METRICS" | grep -c "spring_kafka\|kafka" || echo "0")"
echo "Spring Data метрики: $(echo "$METRICS" | grep -c "spring_data" || echo "0")"
echo "System метрики: $(echo "$METRICS" | grep -c "system_" || echo "0")"
echo "Process метрики: $(echo "$METRICS" | grep -c "process_" || echo "0")"
echo "Cache метрики: $(echo "$METRICS" | grep -c "cache\|caffeine" || echo "0")"

# Проверяем конфигурацию метрик
echo ""
echo "⚙️  Проверка конфигурации метрик:"
curl -s http://localhost:8090/actuator/metrics | jq -r '.names[]' 2>/dev/null | head -10 || echo "Не удалось получить список метрик"

# Сохраняем все метрики в файл для анализа
echo "$METRICS" > metrics-output.txt
echo ""
echo "💾 Все метрики сохранены в файл metrics-output.txt"

echo ""
echo "Проверка завершена!"

