-- Flyway миграция для создания функций и триггеров device_info

-- Создание функции для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Создание триггера для автоматического обновления updated_at
CREATE TRIGGER update_device_info_updated_at 
    BEFORE UPDATE ON device_info 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Создание функции для upsert device_info
CREATE OR REPLACE FUNCTION upsert_device_info(
    p_device_id VARCHAR(255),
    p_device_type VARCHAR(100),
    p_manufacturer VARCHAR(100),
    p_model VARCHAR(100),
    p_firmware_version VARCHAR(50),
    p_first_seen TIMESTAMP,
    p_last_seen TIMESTAMP,
    p_status VARCHAR(50),
    p_meta JSONB,
    p_created_at TIMESTAMP,
    p_updated_at TIMESTAMP
)
RETURNS VOID AS $$
BEGIN
    INSERT INTO device_info (
        device_id, device_type, manufacturer, model, firmware_version,
        first_seen, last_seen, status, meta, created_at, updated_at
    ) VALUES (
        p_device_id, p_device_type, p_manufacturer, p_model, p_firmware_version,
        p_first_seen, p_last_seen, p_status, p_meta, p_created_at, p_updated_at
    )
    ON CONFLICT (device_id) DO UPDATE SET
        device_type = EXCLUDED.device_type,
        manufacturer = EXCLUDED.manufacturer,
        model = EXCLUDED.model,
        firmware_version = EXCLUDED.firmware_version,
        last_seen = EXCLUDED.last_seen,
        status = EXCLUDED.status,
        meta = EXCLUDED.meta,
        updated_at = EXCLUDED.updated_at;
END;
$$ LANGUAGE plpgsql;
