-- Инициализация базы данных dcs_shard1
CREATE TABLE IF NOT EXISTS device_info (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    device_name VARCHAR(255),
    device_type VARCHAR(100),
    location VARCHAR(255),
    status VARCHAR(50),
    last_seen TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Создаем индексы
CREATE INDEX IF NOT EXISTS idx_device_info_device_id ON device_info(device_id);
CREATE INDEX IF NOT EXISTS idx_device_info_status ON device_info(status);
CREATE INDEX IF NOT EXISTS idx_device_info_last_seen ON device_info(last_seen);

-- Создаем функцию для обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Создаем триггер для автоматического обновления updated_at
DROP TRIGGER IF EXISTS update_device_info_updated_at ON device_info;
CREATE TRIGGER update_device_info_updated_at
    BEFORE UPDATE ON device_info
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
