CREATE KEYSPACE IF NOT EXISTS ecs
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
CREATE TABLE IF NOT EXISTS ecs.device_events_by_device
(
    device_id text,
    event_id  text,
    timestamp bigint,
    type      text,
    payload   text,
    PRIMARY KEY ((device_id), event_id)
) WITH CLUSTERING ORDER BY (event_id ASC);