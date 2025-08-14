DC = architecture/infrastructure/docker-compose.yaml
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