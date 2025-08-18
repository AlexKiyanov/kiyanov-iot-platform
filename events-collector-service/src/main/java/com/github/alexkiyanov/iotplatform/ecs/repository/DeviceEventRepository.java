package com.github.alexkiyanov.iotplatform.ecs.repository;

import org.springframework.stereotype.Repository;

import com.github.alexkiyanov.iotplatform.ecs.model.cassandra.DeviceEventEntity;
import com.github.alexkiyanov.iotplatform.ecs.model.cassandra.DeviceEventKey;

import org.springframework.data.cassandra.repository.CassandraRepository;

@Repository
public interface DeviceEventRepository extends CassandraRepository<DeviceEventEntity, DeviceEventKey> {

}
