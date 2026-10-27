# Delivery Dispatch API

Spring Boot REST API for customer delivery orders, courier dispatch rules, and delivery lifecycle tracking.

## Shipped Capabilities

- JWT authentication with `ADMIN`, `DISPATCHER`, `COURIER`, and `CUSTOMER` roles.
- Customer registration, login, order creation, lookup, cancellation, and timeline endpoints.
- Courier availability, location, current assignment, pickup, and delivery endpoints.
- Nearest-courier selection using Haversine distance and stored coordinates.
- Dispatcher/admin endpoints for automatic and manual courier assignment.
- Dispatcher/admin operational order listing with customer and courier assignment details.
- Dispatcher/admin courier listing with profile, availability, and stored location details.
- Optimistic versioning and a PostgreSQL unique index that prevent concurrent double assignment of orders or couriers.
- PostgreSQL persistence managed by versioned Flyway migrations.
- Structured request-parsing, validation, authentication, authorization, and domain error responses.
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
- `curl` for the local walkthrough

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

Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`. The OpenAPI JSON document is available at `http://localhost:8080/v3/api-docs`, and runnable request examples are in [api-tests.http](api-tests.http).

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

## Local API Walkthrough

Start PostgreSQL and the API as described in Quick Start before running these requests.

1. Log in as the seeded customer:

```bash
curl -s http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@delivery.local","password":"DemoPass123!"}'
```

Copy the `token` value from the response:

```bash
export CUSTOMER_TOKEN='<customer-jwt>'
```

2. List the customer's seeded orders:

```bash
curl -s http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

Copy the `id` of the order whose status is `DELIVERED`, then inspect its chronological timeline:

```bash
export DELIVERED_ORDER_ID='<delivered-order-id>'

curl -s "http://localhost:8080/api/v1/orders/$DELIVERED_ORDER_ID/timeline" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

The response shows `ORDER_CREATED`, `COURIER_ASSIGNED`, `ORDER_PICKED_UP`, and `ORDER_DELIVERED` events.

3. Create a new pending order:

```bash
curl -s http://localhost:8080/api/v1/orders \
  -X POST \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "pickupAddress":"Taksim Square, Beyoglu",
    "pickupLatitude":41.036900,
    "pickupLongitude":28.985000,
    "dropoffAddress":"Kadikoy Pier",
    "dropoffLatitude":40.990900,
    "dropoffLongitude":29.023300
  }'
```

Copy the new order's `id` from the response:

```bash
export NEW_ORDER_ID='<new-order-id>'
```

4. Log in as a seeded courier and copy the returned token:

```bash
curl -s http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"courier.one@delivery.local","password":"DemoPass123!"}'

export COURIER_TOKEN='<courier-jwt>'
```

5. Update the courier's location and availability:

```bash
curl -s http://localhost:8080/api/v1/couriers/me/location \
  -X PATCH \
  -H "Authorization: Bearer $COURIER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude":41.032000,"longitude":28.978000}'

curl -s http://localhost:8080/api/v1/couriers/me/availability \
  -X PATCH \
  -H "Authorization: Bearer $COURIER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"AVAILABLE"}'
```

6. Log in as the seeded dispatcher and inspect the operational courier and order views:

```bash
curl -s http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dispatcher@delivery.local","password":"DemoPass123!"}'

export DISPATCHER_TOKEN='<dispatcher-jwt>'

curl -s http://localhost:8080/api/v1/dispatch/couriers \
  -H "Authorization: Bearer $DISPATCHER_TOKEN"

curl -s http://localhost:8080/api/v1/dispatch/orders \
  -H "Authorization: Bearer $DISPATCHER_TOKEN"
```

The courier response shows profile, availability, and location data. The order response shows the new pending order alongside its customer, route, and current assignment details.

7. Auto-assign the nearest eligible courier, then inspect the updated order:

```bash
curl -s "http://localhost:8080/api/v1/dispatch/orders/$NEW_ORDER_ID/auto-assign" \
  -X POST \
  -H "Authorization: Bearer $DISPATCHER_TOKEN"

curl -s http://localhost:8080/api/v1/dispatch/orders \
  -H "Authorization: Bearer $DISPATCHER_TOKEN"
```

The assignment response identifies the selected courier, and the operational order view now shows the order as `ASSIGNED`. With the seeded locations and the location update above, `courier.one@delivery.local` is selected.

8. Discover the current assignment as the courier:

```bash
curl -i http://localhost:8080/api/v1/couriers/me/orders/current \
  -H "Authorization: Bearer $COURIER_TOKEN"
```

The `200` response contains the order's `id`, status, pickup/dropoff addresses, and coordinates. An idle courier receives `204` with no body. Copy the returned `id`, then pick up and deliver that order:

```bash
export CURRENT_ORDER_ID='<id-from-current-assignment>'

curl -s "http://localhost:8080/api/v1/couriers/me/orders/$CURRENT_ORDER_ID/pickup" \
  -X POST \
  -H "Authorization: Bearer $COURIER_TOKEN"

curl -s "http://localhost:8080/api/v1/couriers/me/orders/$CURRENT_ORDER_ID/deliver" \
  -X POST \
  -H "Authorization: Bearer $COURIER_TOKEN"

curl -i http://localhost:8080/api/v1/couriers/me/orders/current \
  -H "Authorization: Bearer $COURIER_TOKEN"
```

After delivery, the current-assignment request returns `204` until another order is assigned.

9. Inspect the completed timeline:

```bash
curl -s "http://localhost:8080/api/v1/orders/$NEW_ORDER_ID/timeline" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

The new order now has the complete created, assigned, picked-up, and delivered event sequence.

## Request Errors

Handled API errors retain `code` and `message` and now always include a `details` array. Parsing, authentication, authorization, not-found, and conflict responses use an empty array. For example, an invalid numeric path parameter returns `400`:

```json
{
  "code": "INVALID_REQUEST_PARAMETER",
  "message": "Request parameter is missing or invalid",
  "details": []
}
```

- `MALFORMED_REQUEST`: a required body is missing, JSON is malformed, or a body value cannot be deserialized (including an unknown enum value). Message: `Request body is missing or malformed`.
- `INVALID_REQUEST_PARAMETER`: a declared path/query parameter cannot be converted, or a required query parameter is missing. Message: `Request parameter is missing or invalid`.
- `VALIDATION_FAILED`: DTO fields or declared controller parameter constraints fail validation. Message: `Request validation failed`.

For example, login with a blank email and password returns `400` with both validation failures:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "details": [
    {"field": "email", "message": "must not be blank"},
    {"field": "password", "message": "must not be blank"}
  ]
}
```

Validation details use public request field/parameter names and are sorted by field, then message. Distinct failures on the same field are retained; duplicate field/message pairs are removed. Request-wide errors use `_request`. Messages never include rejected passwords, bearer tokens, or other submitted values; validation annotations must not interpolate those values into their messages.

An authenticated customer's `GET /api/v1/orders/abc` returns `400 INVALID_REQUEST_PARAMETER`; a valid numeric ID for a missing or unowned order still returns `404 ORDER_NOT_FOUND`. Parsing responses do not expose parser details. Query validation is covered by MVC fixtures; list pagination and filtering are not shipped yet.

Existing domain error codes, authentication failures (`401`), role restrictions (`403`), and business conflicts (`409`) are unchanged. Controller return-value validation remains a server error (`500`), not a client error.

## Assignment Conflicts

Manual and automatic assignment use optimistic versions on both the order and courier's user record. A partial unique index also limits each courier to one `ASSIGNED` or `PICKED_UP` order; completed deliveries do not occupy that slot.

An assignment that loses a concurrent version or active-courier uniqueness check returns `409` with code `ORDER_ASSIGNMENT_CONFLICT`. Its order, courier, and timeline writes roll back together. The API does not retry automatically; inspect the current operational state before retrying.

## Courier State

`GET /api/v1/couriers/me/orders/current` is restricted to `COURIER` users and derives ownership from the JWT. It returns only that courier's `ASSIGNED` or `PICKED_UP` order (`200`), or an empty `204` when none exists. Completed deliveries are excluded. This read does not change availability, order state, or timeline history.

Couriers can select `AVAILABLE` or `UNAVAILABLE` only when idle. During an `ASSIGNED` or `PICKED_UP` delivery, manual availability changes return `409 COURIER_AVAILABILITY_CHANGE_NOT_ALLOWED`. `ON_DELIVERY` remains system-managed. Successful delivery completion automatically sets the courier to `AVAILABLE`; the courier can then choose `UNAVAILABLE`.

Location updates are allowed during delivery and do not change availability. Availability, location, and completion writes use the existing optimistic versions. A stale write returns `409 COURIER_UPDATE_CONFLICT`, with the entire transaction rolled back, including any completion and timeline changes. No automatic retry occurs; reload current state before retrying. Dispatcher/admin operational views expose the current order and courier state.

## Configuration

| Variable | Purpose | Default |
| --- | --- | --- |
| `POSTGRES_PORT` | Local PostgreSQL host port | `5432` |
| `JWT_ISSUER` | Issuer used for token generation and required during validation | `delivery-dispatch-api` |
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

Dispatch concurrency tests synchronize two real transactions and verify manual/manual, automatic/automatic, mixed assignment, same-order conflicts, rollback, and courier reuse after delivery.

Courier state tests also overlap availability/location writes with assignment and completion, verify both winning and losing transactions, and check persisted state and timeline rollback.

Current-assignment tests cover role access, JWT-derived ownership, persisted active orders, idle results, and completed-history exclusion with Open Session in View disabled.

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

Migration V8 adds user versioning and the unique active-courier index. If an existing database already contains multiple active orders for one courier, the migration fails without deleting or rewriting those records. Reconcile the conflicting assignments explicitly before retrying startup.

## Authentication

Application endpoints are versioned under `/api/v1`.

Public endpoints:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`

Protected endpoints expect the JWT returned by login:

```text
Authorization: Bearer <jwt>
```

Tokens must have a valid HS256 signature using the configured key and an `iss` matching `JWT_ISSUER`. Required claims are a positive JSON integer `userId` within Java's `long` range, a recognized `role`, a nonblank subject (`sub`), and an expiration (`exp`). Numeric strings and fractional user IDs are rejected.

Spring's timestamp validation remains enabled for `exp` and optional `nbf`, with its default 60-second clock tolerance. Missing or invalid tokens return `401 AUTHENTICATION_REQUIRED`; valid tokens without the endpoint's required role return `403 ACCESS_DENIED`. Changing the configured issuer invalidates previously issued tokens with a different issuer.

`POST /api/v1/auth/users` requires the `ADMIN` role and creates only `DISPATCHER` or `COURIER` accounts. Customer accounts use the public registration endpoint. Email addresses are trimmed and lowercased, and passwords are stored as BCrypt hashes.
