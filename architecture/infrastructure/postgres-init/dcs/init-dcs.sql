-- Создание базы данных для Device Collector Service
CREATE DATABASE dcs;

-- Подключение к базе данных dcs
\c dcs;

-- Создание таблицы device_info
CREATE TABLE device_info (
    device_id VARCHAR(255) PRIMARY KEY,
    device_type VARCHAR(100),
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    firmware_version VARCHAR(50),
    first_seen TIMESTAMP,
    last_seen TIMESTAMP,
    status VARCHAR(50)
);

-- Создание индексов для оптимизации запросов
CREATE INDEX idx_device_info_device_type ON device_info(device_type);
CREATE INDEX idx_device_info_manufacturer ON device_info(manufacturer);
CREATE INDEX idx_device_info_status ON device_info(status);
CREATE INDEX idx_device_info_last_seen ON device_info(last_seen);

-- Комментарии к таблице и полям
COMMENT ON TABLE device_info IS 'Информация об устройствах IoT платформы';
COMMENT ON COLUMN device_info.device_id IS 'Уникальный идентификатор устройства';
COMMENT ON COLUMN device_info.device_type IS 'Тип устройства (sensor, actuator, gateway, etc.)';
COMMENT ON COLUMN device_info.manufacturer IS 'Производитель устройства';
COMMENT ON COLUMN device_info.model IS 'Модель устройства';
COMMENT ON COLUMN device_info.firmware_version IS 'Версия прошивки устройства';
COMMENT ON COLUMN device_info.first_seen IS 'Время первого появления устройства в системе';
COMMENT ON COLUMN device_info.last_seen IS 'Время последнего появления устройства в системе';
COMMENT ON COLUMN device_info.status IS 'Статус устройства (active, inactive, error, etc.)';
