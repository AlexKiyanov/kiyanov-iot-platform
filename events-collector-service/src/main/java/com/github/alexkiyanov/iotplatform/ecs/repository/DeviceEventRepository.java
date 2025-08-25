package com.github.alexkiyanov.iotplatform.ecs.repository;

import org.springframework.stereotype.Repository;

import com.github.alexkiyanov.iotplatform.ecs.model.cassandra.DeviceEventEntity;
import com.github.alexkiyanov.iotplatform.ecs.model.cassandra.DeviceEventKey;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface DeviceEventRepository extends CassandraRepository<DeviceEventEntity, DeviceEventKey> {

    @Query("SELECT * FROM device_events_by_device WHERE device_id = :deviceId")
    List<DeviceEventEntity> findByDeviceId(@Param("deviceId") String deviceId);
}
