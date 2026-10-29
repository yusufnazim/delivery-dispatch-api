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

The local-demo v1 scope is implemented. See [ROADMAP.md](ROADMAP.md) for verification status and future scope.

See [Backend Architecture](docs/architecture.md) for the request flow, schema relationships, security, transactions, concurrency protection, and design tradeoffs.

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
docker compose up -d --wait postgres
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

On a fresh database, the seed creates five users, two available couriers with locations, one pending order, one delivered order, and five timeline events. Restarting does not duplicate or reset existing demo data. Completed or cancelled orders stay that way; create a new order to repeat a workflow.

Disable demo data when starting the local profile with:

```bash
DEMO_DATA_ENABLED=false ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Local API Walkthrough

Start PostgreSQL and the API as described in Quick Start before running these requests.

Use IDs and tokens from actual responses, not assumed numeric IDs. In [api-tests.http](api-tests.http), fill the named variables and run the desired flow individually; some requests intentionally return errors.

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

The assignment response identifies the selected courier, and the operational order page's `items` show the order as `ASSIGNED`. On a fresh seeded database with the location update above, `courier.one@delivery.local` is selected. If demo data has changed, check the response and log in as the selected courier before continuing. A courier with an unfinished delivery must complete it before changing availability or accepting another order.

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

After delivery, the courier becomes `AVAILABLE` and the current-assignment request returns `204` until another order is assigned.

9. Inspect the completed timeline:

```bash
curl -s "http://localhost:8080/api/v1/orders/$NEW_ORDER_ID/timeline" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

The new order now has the complete created, assigned, picked-up, and delivered event sequence.

### Manual Assignment

Create a separate pending order using step 3 and record its ID as `MANUAL_ORDER_ID`. Select an idle, available courier from `GET /api/v1/dispatch/couriers?availability=AVAILABLE` and record its `id` as `COURIER_ID`:

```bash
export MANUAL_ORDER_ID='<id-from-new-order>'
export COURIER_ID='<id-from-courier-list>'

curl -s "http://localhost:8080/api/v1/dispatch/orders/$MANUAL_ORDER_ID/assign" \
  -X POST \
  -H "Authorization: Bearer $DISPATCHER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"courierId\":$COURIER_ID}"
```

Both `DISPATCHER` and `ADMIN` may assign orders. Log in as the selected courier and repeat step 8 to discover, pick up, and deliver this order. Inspect `/api/v1/orders/$MANUAL_ORDER_ID/timeline` as its customer. Do not run both assignment methods on the same order: the second attempt returns `409`.

### Cancellation

Create another order using step 3 and record its ID as `CANCEL_ORDER_ID`. Cancel it before assignment:

```bash
export CANCEL_ORDER_ID='<id-from-another-new-order>'

curl -i "http://localhost:8080/api/v1/orders/$CANCEL_ORDER_ID" \
  -X DELETE \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"

curl -s "http://localhost:8080/api/v1/orders/$CANCEL_ORDER_ID/timeline" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

Cancellation returns `200` with the updated order; it remains in history as `CANCELLED` with `ORDER_CREATED` and `ORDER_CANCELLED` events. Only an owned `PENDING` order can be cancelled. Do not use the order from a completed assignment flow.

## Customer Order Pagination

`GET /api/v1/orders` returns `items`, `page`, `size`, `totalElements`, and `totalPages`.

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

`GET /api/v1/dispatch/orders` requires `DISPATCHER` or `ADMIN` and lists orders across all customers using the same page response. Read order details from `items`.

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

`GET /api/v1/dispatch/couriers` requires `DISPATCHER` or `ADMIN` and returns the shared page response. Read courier details from `items`.

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

Handled API errors include `code`, `message`, and a `details` array. Parsing, authentication, authorization, not-found, and conflict responses use an empty array. For example, an invalid numeric path parameter returns `400`:

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

Authentication failures return `401`, role restrictions return `403`, and business conflicts return `409`. Controller return-value validation is a server error (`500`), not a client error.

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
POSTGRES_PORT=5433 docker compose up -d --wait postgres
POSTGRES_PORT=5433 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Testing

Run the complete test suite:

```bash
./mvnw test
```

For a clean verification run that removes old build output, runs all tests, and builds the executable jar:

```bash
./mvnw clean verify
```

Both commands require a running Docker daemon accessible to the current user. PostgreSQL Testcontainers tests fail rather than skip when Docker is unavailable. Check access with `docker info`; the tests create disposable databases and do not require the Compose database to be running.

CI checks Docker access and runs `./mvnw --batch-mode --no-transfer-progress verify` on pushes and pull requests to `main`. On failure, it uploads available `target/surefire-reports/` files as the `test-reports` artifact, retained for 7 days. A Docker preflight failure appears in the job log before test reports exist. Locally, inspect the same reports directory for failure details. Focused test commands below are not a substitute for full verification before committing.

Tests cover service rules, MVC validation/security, real PostgreSQL persistence, both authenticated assignment workflows, concurrency/rollback, migration upgrades, and bounded pagination without per-order queries. See [Verification Evidence](docs/architecture.md#verification-evidence) for the source-linked test map.

Run only the authenticated HTTP workflow checks:

```bash
./mvnw -Dtest=AuthenticatedDeliveryWorkflowIntegrationTest test
```

To run the built jar instead of Maven, stop the existing API process first and keep PostgreSQL running:

```bash
java -jar target/delivery-dispatch-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

For a live demo check, run the automatic, manual, and cancellation flows above, then restart the API and confirm history remains intact. Check missing-token `401`, wrong-role `403`, malformed-input `400`, and repeated-assignment `409` responses. Automated tests use isolated containers; the README and HTTP-file requests modify the Compose database.

## Database Operations

Check the PostgreSQL container:

```bash
docker compose ps
```

Stop PostgreSQL while preserving its named data volume:

```bash
docker compose down
```

Only for an intentional reset: stop the API, then remove the Compose database and its named volume. This deletes local users, orders, and history; it is not required to repeat the demo:

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
