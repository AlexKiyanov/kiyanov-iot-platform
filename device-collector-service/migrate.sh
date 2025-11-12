#!/bin/bash

# Скрипт для запуска миграций Flyway на всех шардах и репликах

echo "Запуск миграций для DCS с поддержкой реплик..."

# Запуск приложения с профилем migration для выполнения миграций на всех datasource'ах
echo "Выполнение миграций на всех шардах и репликах..."
SPRING_PROFILES_ACTIVE=migration ./gradlew bootRun --args="--spring.main.web-application-type=none"

echo "Миграции завершены!"
