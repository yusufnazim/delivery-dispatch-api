# Delivery Dispatch API

Spring Boot REST API for customer delivery orders, courier dispatch rules, and delivery lifecycle tracking.

## Shipped Capabilities

- JWT authentication with `ADMIN`, `DISPATCHER`, `COURIER`, and `CUSTOMER` roles.
- Customer registration, login, order creation, lookup, cancellation, and timeline endpoints.
- Bounded customer order history with database-backed pagination and ownership-filtered totals.
- Courier availability, location, current assignment, pickup, and delivery endpoints.
- Nearest-courier selection using Haversine distance and stored coordinates.
- Dispatcher/admin endpoints for automatic and manual courier assignment.
- Dispatcher/admin operational order pagination and status filtering with customer and courier assignment details.
- Dispatcher/admin courier pagination and availability filtering with profile and stored location details.
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

The response contains `items`, `page`, `size`, `totalElements`, and `totalPages`. In `items`, copy the `id` of the order whose status is `DELIVERED`, then inspect its chronological timeline:

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

Both responses are page objects. Courier `items` include profile, availability, and location data; order `items` include the new pending order with customer, route, and current assignment details. Use `?availability=AVAILABLE` for the courier list or `?status=PENDING` for the order list to filter results.

7. Auto-assign the nearest eligible courier, then inspect the updated order:

```bash
curl -s "http://localhost:8080/api/v1/dispatch/orders/$NEW_ORDER_ID/auto-assign" \
  -X POST \
  -H "Authorization: Bearer $DISPATCHER_TOKEN"

curl -s http://localhost:8080/api/v1/dispatch/orders \
  -H "Authorization: Bearer $DISPATCHER_TOKEN"
```

The assignment response identifies the selected courier, and the operational order page's `items` now show the order as `ASSIGNED`. With the seeded locations and the location update above, `courier.one@delivery.local` is selected.

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

## Customer Order Pagination

`GET /api/v1/orders` returns a page object instead of the previous array. This is an intentional pre-release response change: clients should read orders from `items`.

- `page` is optional and zero-based; default `0`, allowed range `0` to `21474836`. The upper bound keeps database offsets within JPA's supported integer range for every allowed size.
- `size` is optional; default `20`, allowed range `1` to `100`.
- Items and totals include only orders owned by the authenticated customer. Ordering is fixed: `createdAt DESC, id DESC`.
- Empty and beyond-last pages return `200` with `items: []`, the requested page/size, and the matching totals. A customer without orders has `totalElements: 0` and `totalPages: 0`.

For example, request the second page of ten orders:

```bash
curl -s "http://localhost:8080/api/v1/orders?page=1&size=10" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

Out-of-range values return `400 VALIDATION_FAILED` with field details; non-integer values return `400 INVALID_REQUEST_PARAMETER`. Pages are not a snapshot across requests: new orders can shift later pages.

## Operational Order Pagination

`GET /api/v1/dispatch/orders` requires `DISPATCHER` or `ADMIN` and lists orders across all customers. It now returns the same `items`, `page`, `size`, `totalElements`, and `totalPages` page shape instead of the previous array. This is an intentional pre-release response change; read order details from `items`.

- Optional `page` and `size` have the same defaults and limits as customer order history: page `0..21474836` (default `0`), size `1..100` (default `20`).
- Optional `status` accepts `PENDING`, `ASSIGNED`, `PICKED_UP`, `DELIVERED`, or `CANCELLED`. Omitted or empty means all statuses; unknown or lowercase values return `400 INVALID_REQUEST_PARAMETER`.
- Filtering and pagination run in PostgreSQL. Totals count only matching orders, and ordering stays `createdAt DESC, id DESC`.
- Empty and beyond-last pages return `200` with `items: []` and matching totals. No matches means both totals are zero. Concurrent creations or status changes can shift later pages.
- Customer and courier details are fetched together; unassigned orders retain null courier fields. Missing authentication returns `401`; customer/courier roles receive `403`.

For example, request the first ten pending orders:

```bash
curl -s "http://localhost:8080/api/v1/dispatch/orders?status=PENDING&page=0&size=10" \
  -H "Authorization: Bearer $DISPATCHER_TOKEN"
```

## Operational Courier Pagination

`GET /api/v1/dispatch/couriers` requires `DISPATCHER` or `ADMIN`. It now returns the shared `items`, `page`, `size`, `totalElements`, and `totalPages` page shape instead of an array. This is an intentional pre-release response change; read courier details from `items`.

- Optional `page` is `0..21474836` (default `0`); `size` is `1..100` (default `20`). Out-of-range values return `400 VALIDATION_FAILED` with field details; non-integer values return `400 INVALID_REQUEST_PARAMETER`.
- Optional `availability` accepts `AVAILABLE`, `UNAVAILABLE`, or `ON_DELIVERY`. Omitted or empty means all availability states; unknown or lowercase values return `400 INVALID_REQUEST_PARAMETER`.
- PostgreSQL filters and counts only courier users matching the requested availability. Ordering is `id ASC`; empty and beyond-last pages return `200` with empty `items` and matching totals. No matches means both totals are zero.
- Profile and location fields remain nullable. Missing authentication returns `401`; customer/courier roles receive `403`.
- The filter uses stored availability, not assignment eligibility. Both assignment paths check availability and active deliveries; automatic assignment also requires a stored location. The internal nearest-courier lookup remains unpaged and considers all eligible couriers.

```bash
curl -s "http://localhost:8080/api/v1/dispatch/couriers?availability=AVAILABLE&page=0&size=10" \
  -H "Authorization: Bearer $DISPATCHER_TOKEN"
```

Pages are not a snapshot across requests: availability changes or new courier accounts can shift later pages.

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

An authenticated customer's `GET /api/v1/orders/abc` returns `400 INVALID_REQUEST_PARAMETER`; a valid numeric ID for a missing or unowned order still returns `404 ORDER_NOT_FOUND`. Parsing responses do not expose parser details. Customer pagination validates query parameters, for example `GET /api/v1/orders?page=-1&size=101` returns details for both `page` and `size`.

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

Customer pagination tests cover parameter defaults and bounds, ownership-filtered counts, timestamp/id ordering, empty and beyond-last pages, and bounded PostgreSQL entity loading.

Operational order tests cover every status, access rules, matching page totals, stable ordering, and maximum offsets. PostgreSQL checks load only the requested orders and map customer/courier details after detaching entities, with two statements (page plus count) for the tested full pages and no per-order queries.

Operational courier tests cover each availability, courier-only items and totals, stable id ordering, empty/boundary pages, validation, and role access. PostgreSQL checks prove bounded entity loading and confirm that the eligible-courier lookup still returns candidates beyond the first display page.

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
