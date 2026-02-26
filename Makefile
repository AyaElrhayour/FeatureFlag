.PHONY: up down logs ps

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f postgres

ps:
	docker compose ps