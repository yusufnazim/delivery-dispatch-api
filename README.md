# Delivery Dispatch API

Spring Boot REST API for customer delivery orders, courier dispatch rules, and delivery lifecycle tracking.

## Shipped Capabilities

- JWT authentication with `ADMIN`, `DISPATCHER`, `COURIER`, and `CUSTOMER` roles.
- Customer registration, login, order creation, lookup, cancellation, and timeline endpoints.
- Courier availability, location, pickup, and delivery endpoints.
- Nearest-courier selection using Haversine distance and stored coordinates.
- Transactional assignment rules that prevent double assignment and limit couriers to one active delivery.
- PostgreSQL persistence managed by versioned Flyway migrations.
- Structured validation, authentication, authorization, and domain error responses.
- PostgreSQL-backed integration tests using Testcontainers.
- OpenAPI operation and JWT bearer documentation.

Planned documentation and final portfolio work are tracked separately in [ROADMAP.md](ROADMAP.md).

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Web MVC and Spring Security
- Spring Data JPA and Jakarta Validation
- PostgreSQL and Flyway
- Testcontainers
- OpenAPI
- Maven Wrapper

## Prerequisites

- JDK 21
- Docker with Docker Compose

Maven does not need to be installed because the repository includes `./mvnw`.

## Quick Start

1. Start PostgreSQL:

```bash
docker compose up -d postgres
```

2. Start the API with the local profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway applies pending migrations during startup. The local profile also loads idempotent demo data by default.

3. Check the application:

```bash
curl http://localhost:8080/actuator/health
```

The OpenAPI JSON document is available at `http://localhost:8080/v3/api-docs`. Runnable request examples are in [api-tests.http](api-tests.http).

## Demo Accounts

All local demo accounts use the password `DemoPass123!`.

| Email | Role |
| --- | --- |
| `admin@delivery.local` | `ADMIN` |
| `dispatcher@delivery.local` | `DISPATCHER` |
| `customer@delivery.local` | `CUSTOMER` |
| `courier.one@delivery.local` | `COURIER` |
| `courier.two@delivery.local` | `COURIER` |

The seed includes two available couriers with locations, one pending order, one delivered order, and matching timeline events. Restarting the application does not duplicate this data.

Disable demo data when starting the local profile with:

```bash
DEMO_DATA_ENABLED=false ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Configuration

| Variable | Purpose | Default |
| --- | --- | --- |
| `POSTGRES_PORT` | Local PostgreSQL host port | `5432` |
| `JWT_ISSUER` | JWT issuer claim | `delivery-dispatch-api` |
| `JWT_SECRET` | JWT signing secret outside local development | No non-local default |
| `JWT_EXPIRATION` | JWT lifetime | `1h` |
| `DEMO_DATA_ENABLED` | Enable local demo seed data | `true` in the local profile |

The local profile provides development-only database credentials and a JWT secret. Do not reuse them outside local development.

If port `5432` is already in use, run both PostgreSQL and the API with another port:

```bash
POSTGRES_PORT=5433 docker compose up -d postgres
POSTGRES_PORT=5433 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Testing

Run the complete test suite:

```bash
./mvnw test
```

Run the full Maven verification lifecycle and build the application jar:

```bash
./mvnw verify
```

Docker must be available for the PostgreSQL Testcontainers integration tests.

## Database Operations

Check the PostgreSQL container:

```bash
docker compose ps
```

Stop PostgreSQL while preserving its named data volume:

```bash
docker compose down
```

Stop PostgreSQL and remove local database data:

```bash
docker compose down -v
```

Flyway migrations are stored in `src/main/resources/db/migration`. Hibernate validates the mapped schema and does not update it automatically.

## Authentication

Application endpoints are versioned under `/api/v1`.

Public endpoints:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`

Protected endpoints expect the JWT returned by login:

```text
Authorization: Bearer <jwt>
```

`POST /api/v1/auth/users` requires the `ADMIN` role and creates only `DISPATCHER` or `COURIER` accounts. Customer accounts use the public registration endpoint. Email addresses are trimmed and lowercased, and passwords are stored as BCrypt hashes.
