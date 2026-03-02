.PHONY: up down clean logs ps build test test-sdk test-all perf perf-local

down:
	docker compose down

clean:
	docker compose down -v

logs:
	docker compose logs -f

logs-app:
	docker compose logs -f app

ps:
	docker compose ps

build:
	mvn clean package -DskipTests

build-docker: build
	docker compose build app

up: build-docker
	docker compose up -d

test:
	cd feature-flag-service && mvn verify

test-sdk:
	cd feature-flag-sdk && mvn verify

test-all:
	mvn verify

perf:
	docker compose --profile perf up --abort-on-container-exit k6