include .env
export

# ══════════════════════════════════════════════════════════════════
#  Job Board Platform — Makefile
# ══════════════════════════════════════════════════════════════════

PROJECT_NAME := job-board-platform

# Docker Compose File Definitions
INFRA_FILE   := -f docker/infra.yml
APP_FILE     := -f docker/app.yml
DEV_FILE     := -f docker/dev-tools.yml
ENV_FILE     := --env-file .env
COMPOSE      := docker compose -p $(PROJECT_NAME) $(ENV_FILE)

.DEFAULT_GOAL := help

# ── Help ──────────────────────────────────────────────────────────
.PHONY: help
help:
	@echo ""
	@echo "  Job Board Platform"
	@echo ""
	@echo "  Build"
	@echo "    rebuild-service       Build and restart a specific service"
	@echo "                          Usage: make rebuild-service MODULE=services/user-service SERVICE=user-service"
	@echo ""
	@echo "  Execution"
	@echo "    up-infra              Start PostgreSQL, Kafka, Schema Registry, Kafka Connect, Keycloak, Redis"
	@echo "    up-app                Start all services"
	@echo "    up-dev                Start full stack (infra + app + dev tools)"
	@echo "    up-dev-tools          Start dev tools (Kafka UI, pgAdmin)"
	@echo "    down                  Stop and remove all containers"
	@echo ""

# ══════════════════════════════════════════════════════════════════
#  BUILD
# ══════════════════════════════════════════════════════════════════
.PHONY: rebuild-service
# Usage: make rebuild-service MODULE=infrastructure/discovery-service SERVICE=discovery-service
rebuild-service:
	@echo "→ Rebuilding $(MODULE)..."
	./mvnw clean package -pl $(MODULE) -am -DskipTests
	@echo "→ Restarting $(SERVICE)..."
	$(COMPOSE) $(INFRA_FILE) $(APP_FILE) up -d --build $(SERVICE)
	@echo "✓ $(SERVICE) is updated and running"

# ══════════════════════════════════════════════════════════════════
#  EXECUTION
# ══════════════════════════════════════════════════════════════════
.PHONY: up-infra
up-infra: # Start core infrastructure
	$(COMPOSE) $(INFRA_FILE) up -d
	@echo "✓ Infrastructure started"

.PHONY: up-app
up-app: # Start all services
	$(COMPOSE) $(INFRA_FILE) $(APP_FILE) up -d --remove-orphans
	@echo "✓ Services started"

.PHONY: up-dev
up-dev: # Start full stack — infra + app + dev tools
	$(COMPOSE) $(INFRA_FILE) $(APP_FILE) $(DEV_FILE) up -d --remove-orphans
	@echo "✓ Full stack started"

.PHONY: up-dev-tools
up-dev-tools: # Start observability stack
	$(COMPOSE) $(INFRA_FILE) $(DEV_FILE) up -d
	@echo "✓ Dev tools started"

.PHONY: down
down: # Stop and remove all containers
	$(COMPOSE) $(INFRA_FILE) $(APP_FILE) $(DEV_FILE) down --remove-orphans
	@echo "✓ All containers stopped"