package com.github.alexkiyanov.iotplatform.dcs.repository;

import com.github.alexkiyanov.iotplatform.dcs.model.DeviceInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeviceInfoRepository extends JpaRepository<DeviceInfoEntity, String> {
    
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO device_info (device_id, device_type, manufacturer, model, firmware_version, 
                                first_seen, last_seen, status, meta, created_at, updated_at)
        VALUES (:deviceId, :deviceType, :manufacturer, :model, :firmwareVersion, 
                :firstSeen, :lastSeen, :status, CAST(:meta AS jsonb), :createdAt, :updatedAt)
        ON CONFLICT (device_id)
        DO UPDATE SET 
            device_type = EXCLUDED.device_type,
            manufacturer = EXCLUDED.manufacturer,
            model = EXCLUDED.model,
            firmware_version = EXCLUDED.firmware_version,
            first_seen = CASE 
                WHEN device_info.first_seen IS NULL THEN EXCLUDED.first_seen
                ELSE device_info.first_seen
            END,
            last_seen = EXCLUDED.last_seen,
            status = EXCLUDED.status,
            meta = CAST(EXCLUDED.meta AS jsonb),
            updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsertDeviceInfo(
        @Param("deviceId") String deviceId,
        @Param("deviceType") String deviceType,
        @Param("manufacturer") String manufacturer,
        @Param("model") String model,
        @Param("firmwareVersion") String firmwareVersion,
        @Param("firstSeen") LocalDateTime firstSeen,
        @Param("lastSeen") LocalDateTime lastSeen,
        @Param("status") String status,
        @Param("meta") String meta,
        @Param("createdAt") LocalDateTime createdAt,
        @Param("updatedAt") LocalDateTime updatedAt
    );

    List<DeviceInfoEntity> findByDeviceId(String deviceId);
}