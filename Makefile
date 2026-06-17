include .env
export

# ══════════════════════════════════════════════════════════════════
#  Job Board Platform — Makefile
# ══════════════════════════════════════════════════════════════════

PROJECT_NAME := job-board-platform

# Docker Compose File Definitions
INFRA_FILE := -f docker/infra.yml
APP_FILE := -f docker/app.yml
DEV_FILE := -f docker/dev-tools.yml
TOOLS_FILE := -f docker/tools.yml
ENV_FILE := --env-file .env
COMPOSE := docker compose -p $(PROJECT_NAME) $(ENV_FILE)

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
	@echo "    up-infra              Start PostgreSQL, Kafka, Schema Registry, Kafka Connect, Redis"
	@echo "    up-app                Start all services"
	@echo "    up-dev                Start full stack (infra + app + dev tools)"
	@echo "    up-dev-tools          Start dev tools (Kafka UI, pgAdmin)"
	@echo "    down                  Stop and remove all containers"
	@echo ""
	@echo "  Kafka (Run in this order)"
	@echo "    topics-apply          Create Kafka topics via Terraform"
	@echo "    register-schemas      Register Protobuf schemas to Schema Registry"
	@echo "    register-connectors   Register Debezium connectors"
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
	$(COMPOSE) --profile tools $(INFRA_FILE) $(DEV_FILE) up -d
	@echo "✓ Dev tools started"

.PHONY: down
down: # Stop and remove all containers
	$(COMPOSE) $(INFRA_FILE) $(APP_FILE) $(DEV_FILE) $(TOOLS_FILE) down --remove-orphans
	@echo "✓ All containers stopped"

# ══════════════════════════════════════════════════════════════════
#  KAFKA
#  Run in order: topics-apply → register-schemas → register-connectors
# ══════════════════════════════════════════════════════════════════

.PHONY: topics-apply
topics-apply: # Create Kafka topics via Terraform (requires Kafka running)
	@echo "→ Initializing Terraform..."
	$(COMPOSE) $(TOOLS_FILE) run --rm terraform init
	@echo "→ Applying Kafka topics..."
	$(COMPOSE) $(TOOLS_FILE) run --rm terraform apply -auto-approve
	@echo "✓ Kafka topics created"

.PHONY: register-schemas
register-schemas: # Register Protobuf schemas to Schema Registry (requires Schema Registry running)
	./mvnw -pl common/contracts \
		-P register-schemas \
		io.confluent:kafka-schema-registry-maven-plugin:$(CONFLUENT_VERSION):register \
		-Dschema.registry.url=$(SC_REGISTRY_HOST_URL)
	@echo "✓ Schemas registered"

.PHONY: register-connectors
register-connectors: # Register Debezium connectors to Kafka Connect (requires topics created)
	@echo "→ Registering Debezium connectors..."
	@bash docker/connectors/register.sh
	@echo "✓ Connectors registered"
.PHONY: register-connectors