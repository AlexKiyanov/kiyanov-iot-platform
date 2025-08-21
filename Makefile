DC = ./docker-compose.yaml
.PHONY: up down logs ps
up:
	@echo "Starting IoT platform services..."
	docker-compose -f $(DC) up -d

down:
	@echo "Stopping and removing services..."
	docker-compose -f $(DC) down

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
	docker system prune -f