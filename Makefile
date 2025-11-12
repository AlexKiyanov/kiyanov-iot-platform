DC = ./docker-compose.yaml
NEXUS_URL = http://localhost:8082
INFRA_SERVICES ?= postgres postgres-shard1 postgres-shard2 postgres-shard1-replica postgres-shard2-replica keycloak-postgres postgres-exporter postgres-shard1-exporter postgres-shard2-exporter keycloak kafka1 kafka2 kafka3 kafka-exporter schema-registry kafka-ui prometheus grafana tempo loki alloy minio create-tempo-bucket camunda redis cassandra cassandra-init

.PHONY: up down logs ps nexus infra

ifeq ($(OS),Windows_NT)
WAIT_CMD = powershell -Command "while ($$true) { \
		try { \
			Invoke-WebRequest -UseBasicParsing -Uri $(NEXUS_URL)/service/rest/v1/status -ErrorAction Stop; \
			break \
		} \
		catch { \
			Write-Host 'Nexus not ready, sleeping...'; \
			Start-Sleep -Seconds 5 \
		} \
	}"
else
WAIT_CMD = until curl -sf $(NEXUS_URL)/service/rest/v1/status; do \
echo 'Nexus not ready, sleeping...'; sleep 5; \
done
endif
nexus:
	docker-compose -f $(DC) up -d nexus
	@echo "Waiting for Nexus to be healthy..."
	@$(WAIT_CMD)
	@echo "Nexus is healthy!"
infra: nexus
	docker-compose -f $(DC) up -d $(INFRA_SERVICES)
up:
	@echo "Starting IoT platform services..."
	docker-compose -f $(DC) up -d

down:
	@echo "Stopping and removing services..."
	docker-compose -f $(DC) down -v

logs:
	docker-compose -f $(DC) logs

ps:
	docker-compose -f $(DC) ps

reset:
	docker-compose -f $(DC) down -v
	docker-compose -f $(DC) up -d

cassandra-init:
	@echo "Initializing Cassandra schema..."
	docker-compose -f $(DC) up cassandra-init

cassandra-logs:
	@echo "Cassandra logs:"
	docker-compose -f $(DC) logs cassandra

cassandra-shell:
	@echo "Opening Cassandra shell..."
	docker exec -it cassandra cqlsh

clean:
	@echo "Cleaning up all containers and volumes..."
	docker-compose -f $(DC) down -v --remove-orphans

prune: clean
	docker system prune -f

kafka:
	docker-compose -f $(DC) up -d kafka1 kafka2 kafka3 schema-registry kafka-exporter kafka-ui