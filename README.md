# 🏢 Job Board Platform

A production-grade **job board** built with a **microservices architecture** using **Spring Boot 3**, **Spring Cloud**, **Apache Kafka** (with the Transactional Outbox pattern), **gRPC**, and **OAuth 2.1**. The platform enables employers to post job listings, candidates to search and apply, and all services to communicate asynchronously through a fully event-driven pipeline.

---

## 📐 Architecture Overview

```
                            ┌──────────────┐
                            │   Frontend   │
                            │  (Angular)   │
                            └──────┬───────┘
                                   │
                            ┌──────▼───────┐
                            │  BFF Service │  ← OAuth2 Client / Session
                            └──────┬───────┘
                                   │
                            ┌──────▼───────┐
                            │   Gateway    │  ← JWT Resource Server
                            │   Service    │
                            └──────┬───────┘
                                   │
                      ┌────────────┼────────────┐
                      │            │            │
               ┌──────▼──┐   ┌─────▼────┐  ┌────▼──────┐
               │  Auth   │   │   User   │  │   Job     │
               │ Service │   │  Service │  │  Service  │
               └────┬────┘   └────┬─────┘  └─────┬─────┘
                    │             │              │
            ┌───────▼─────────────▼──────────────▼──────┐
            │              Apache Kafka                 │
            │  (Outbox → Debezium CDC → Schema Registry)│
            └───────┬────────────┬──────────────┬───────┘
                    │            │              │
          ┌─────────▼──┐  ┌──────▼─────┐  ┌─────▼──────────┐
          │Application │  │  Search    │  │  Notification  │
          │  Service   │  │  Service   │  │    Service     │
          └────────────┘  └────────────┘  └────────────────┘
```

**Key Architectural Patterns:**

| Pattern | Implementation |
| --- | --- |
| **Service Discovery** | Eureka (Discovery Service) |
| **API Gateway** | Spring Cloud Gateway |
| **BFF (Backend for Frontend)** | OAuth2 login, session management, request proxying |
| **Transactional Outbox** | Debezium CDC → Kafka (guaranteed delivery) |
| **Event-Driven Messaging** | Apache Kafka with Protobuf + Schema Registry |
| **Inter-Service Sync Calls** | gRPC |
| **Authentication** | Spring Authorization Server (OAuth 2.1 + PKCE) |
| **Authorization** | JWT-based Resource Servers with HMAC-signed internal tokens |

---

## 🛠 Technology Stack

| Layer | Technology |
| --- | --- |
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.5, Spring Cloud 2025.0 |
| **Auth Server** | Spring Authorization Server (OAuth 2.1) |
| **API Gateway** | Spring Cloud Gateway |
| **Service Discovery** | Netflix Eureka |
| **Messaging** | Apache Kafka (KRaft mode) |
| **CDC** | Debezium (Outbox Event Router) |
| **Schema Registry** | Confluent Schema Registry (Protobuf) |
| **Serialization** | Protocol Buffers (Protobuf) |
| **Sync IPC** | gRPC |
| **Databases** | PostgreSQL 17, MongoDB, Elasticsearch 8.18 |
| **Caching / Sessions** | Redis |
| **File Storage** | Cloudinary |
| **Email (Dev)** | Mailpit |
| **Infrastructure as Code** | Terraform (Kafka topics) |
| **Build** | Maven (multi-module), Docker Compose |

---

## 📦 Services

### Business Services

| Service | Port | Description |
| --- | --- | --- |
| **auth-service** | `8081` | OAuth 2.1 Authorization Server — issues JWTs, manages clients & consent |
| **user-service** | `8090` | User profiles, avatar uploads (Cloudinary), gRPC endpoint |
| **job-service** | `8092` | Job listing CRUD, gRPC endpoint for cross-service queries |
| **application-service** | `8096` | Candidate applications for jobs, status tracking |
| **search-service** | `8094` | Full-text search over jobs powered by Elasticsearch |
| **notification-service** | `—` | Email notifications triggered by Kafka events (Mailpit in dev) |

### Infrastructure Services

| Service | Port | Description |
| --- | --- | --- |
| **bff-service** | `8080` | Backend for Frontend — OAuth2 login, session relay, route proxying |
| **gateway-service** | `8082` | API Gateway — routes requests, validates JWTs |
| **discovery-service** | `8761` | Eureka server for service registration and discovery |

### Shared Libraries (`common/`)

| Module | Purpose |
| --- | --- |
| `common-exceptions` | Standardized exception types and error responses |
| `common-outbox` | Transactional Outbox entity & repository (shared by producers) |
| `common-security` | JWT filters, HMAC utilities, shared security config |
| `contracts` | Kafka Protobuf event schemas + Schema Registry Maven plugin config |
| `grpc-contracts` | `.proto` service definitions for gRPC inter-service calls |
| `shared-utils` | Common utilities and helpers |

---

## ⚙️ Prerequisites

Ensure the following are installed before running the project:

- **Java 17** (JDK)
- **Docker** & **Docker Compose** (v2+)
- **Maven 3.9+** (or use the included `./mvnw` wrapper)

---

## 🚀 Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/echcherqaoui/job-board-platform.git
cd job-board-platform
```

### 2. Configure Environment Variables

```bash
cp .env.example .env
```

Open `.env` and fill in the required values:

- **Database credentials** — `PG_USER`, `PG_PASSWORD`
- **Redis password** — `RD_PASSWORD`
- **Kafka cluster ID** — generate with:
  ```bash
  docker run --rm confluentinc/cp-kafka:7.7.7 kafka-storage random-uuid
  ```
- **JWT keystore** — `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`
- **HMAC secret** — `SECRET_KEY` (any random secure string)
- **Cloudinary** — `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
- **OAuth2 client** — `BFF_CLIENT_SECRET`

### 3. Build All Modules

```bash
./mvnw clean package -DskipTests
```

### 4. Start the Infrastructure

```bash
make up-infra
```

This starts: **PostgreSQL**, **Redis**, **Kafka** (KRaft), **Schema Registry**, **Kafka Connect** (Debezium), **Elasticsearch**, and **MongoDB**.

### 5. Set Up the Kafka Pipeline

> **Run these commands in order** — each step depends on the previous one.

```bash
# 1. Create Kafka topics via Terraform
make topics-apply

# 2. Register Protobuf schemas to Schema Registry
make register-schemas

# 3. Register Debezium CDC connectors
make register-connectors
```

### 6. Start Application Services

```bash
# Start all services (infra + app)
make up-app

# Or start full stack including dev tools
make up-dev
```

### 7. (Optional) Start Dev Tools

```bash
make up-dev-tools
```

| Tool | URL | Purpose |
| --- | --- | --- |
| **pgAdmin** | [http://localhost:8000](http://localhost:8000) | PostgreSQL management |
| **Kafka UI** | [http://localhost:8800](http://localhost:8800) | Kafka cluster, topics, consumers |
| **Kibana** | [http://localhost:5601](http://localhost:5601) | Elasticsearch dashboards (profile: `tools`) |
| **Mailpit** | [http://localhost:8025](http://localhost:8025) | Email testing UI |

---

## 📋 Makefile Reference

| Command | Description |
| --- | --- |
| `make help` | Show all available commands |
| `make up-infra` | Start core infrastructure (PostgreSQL, Kafka, Redis, etc.) |
| `make up-app` | Start infrastructure + all application services |
| `make up-dev` | Start full stack (infrastructure + services + dev tools) |
| `make up-dev-tools` | Start dev tools only (pgAdmin, Kafka UI, Mailpit) |
| `make down` | Stop and remove all containers |
| `make topics-apply` | Create Kafka topics via Terraform |
| `make register-schemas` | Register Protobuf schemas to Schema Registry |
| `make register-connectors` | Register Debezium CDC connectors |
| `make rebuild-service MODULE=<path> SERVICE=<name>` | Rebuild and restart a specific service |

**Rebuild example:**

```bash
make rebuild-service MODULE=services/user-service SERVICE=user-service
```

---

## 📁 Project Structure

```
job-board-platform/
│
├── common/                          # Shared libraries
│   ├── common-exceptions/           #   └─ Standardized exceptions & error handlers
│   ├── common-outbox/               #   └─ Transactional Outbox entity & repository
│   ├── common-security/             #   └─ JWT/HMAC security configuration
│   ├── contracts/                   #   └─ Kafka Protobuf event schemas
│   ├── grpc-contracts/              #   └─ gRPC .proto service definitions
│   └── shared-utils/                #   └─ Common utilities
│
├── infrastructure/                  # Platform infrastructure services
│   ├── discovery-service/           #   └─ Eureka service registry
│   └── gateway-service/             #   └─ Spring Cloud Gateway
│
├── services/                        # Business domain services
│   ├── application-service/         #   └─ Job application management
│   ├── auth-service/                #   └─ OAuth 2.1 Authorization Server
│   ├── bff-service/                 #   └─ Backend for Frontend
│   ├── job-service/                 #   └─ Job listing management
│   ├── notification-service/        #   └─ Event-driven email notifications
│   ├── search-service/              #   └─ Elasticsearch-powered search
│   └── user-service/                #   └─ User profile management
│
├── docker/                          # Docker Compose & container configs
│   ├── infra.yml                    #   └─ PostgreSQL, Kafka, Redis, ES, MongoDB
│   ├── app.yml                      #   └─ All application service containers
│   ├── dev-tools.yml                #   └─ pgAdmin, Kafka UI, Kibana, Mailpit
│   ├── tools.yml                    #   └─ Terraform runner
│   ├── connectors/                  #   └─ Debezium connector JSON configs
│   ├── kafka-connect.Dockerfile     #   └─ Custom Kafka Connect image with Debezium
│   └── init-db.sql                  #   └─ Initial database setup
│
├── terraform/                       # Kafka topic provisioning
│   ├── main.tf                      #   └─ Topic definitions (auth, job, app, etc.)
│   ├── provider.tf                  #   └─ Kafka provider config
│   └── variables.tf                 #   └─ Terraform variables
│
├── pom.xml                          # Parent POM (multi-module Maven)
├── Makefile                         # Developer workflow commands
├── .env.example                     # Environment variable template
├── qodana.yaml                      # JetBrains Qodana code quality config
└── mvnw / mvnw.cmd                  # Maven wrapper scripts
```

---

## 🔄 Event-Driven Pipeline

The platform uses the **Transactional Outbox** pattern for reliable event delivery:

```
 Service writes to DB          Debezium captures           Kafka delivers
┌─────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
│  1. Business write  │    │  3. CDC reads WAL  │    │  5. Consumer reads │
│  2. Outbox insert   │───▶│  4. Publishes to   │───▶│     and processes  │
│     (same TX)       │    │     Kafka topic    │    │     the event      │
└─────────────────────┘    └────────────────────┘    └────────────────────┘
```

**Kafka Topics** (managed via Terraform):

| Topic | Producer | Description |
| --- | --- | --- |
| `jobboard.events.auth` | auth-service | User registration, login events |
| `jobboard.events.company` | user-service | Company profile events |
| `jobboard.events.job` | job-service | Job created/updated/deleted events |
| `jobboard.events.application` | application-service | Application submitted/status changed |

Each topic has a corresponding **dead-letter topic** (`*-dlt`) and a **Debezium heartbeat topic** for connector health monitoring.

---

## 🔐 Authentication Flow

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ Frontend │───▶│   BFF    │───▶│   Auth   │───▶│ Gateway  │
│          │    │ (OAuth2  │    │ (AuthZ   │    │ (JWT     │
│          │◀───│  Client) │◀───│  Server) │    │ Verify)  │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
                     │                               │
                     │          Redis Session        │
                     └──────────────────────────────▶│
                                                     ▼
                                               Downstream
                                                Services
```

1. **Frontend** redirects to BFF for login
2. **BFF Service** initiates OAuth2 Authorization Code + PKCE flow
3. **Auth Service** authenticates, issues JWT access + refresh tokens
4. **BFF** stores session in Redis, proxies API requests through Gateway
5. **Gateway** validates JWT and routes to downstream services


---

## 📝 License

This project is for educational and portfolio purposes.

---

<p align="center">
  Built with ☕ and Spring Boot
</p>
