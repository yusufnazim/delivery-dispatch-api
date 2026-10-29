# Backend Architecture

This document describes the implemented local-demo API, not a proposed production
system. See the [README](../README.md) for startup commands and API examples, and
the [roadmap](../ROADMAP.md) for remaining work.

## Runtime And Package Boundaries

One Java 21 / Spring Boot application serves synchronous REST requests and writes
to one PostgreSQL database. Docker Compose starts PostgreSQL; the Maven wrapper
starts the application on the host. There is no broker, cache, background dispatch
worker, or separate authentication service.

Under `com.yusufnazim.deliverydispatch`, packages are feature-oriented:

- `auth` owns registration, login, and administrator-created staff accounts.
- `user` owns the shared user entity, role/courier enums, and user repository.
- `order` owns customer order operations, order persistence, and state transitions.
- `dispatch` owns operational views and automatic/manual assignment orchestration.
- `courier` owns self-service availability, location, current assignment, pickup,
  and delivery completion.
- `timeline` owns delivery event persistence and customer timeline reads.
- `config` and `security` configure JWT validation, access rules, and OpenAPI.
- `exception` defines the shared error contract and MVC exception translation;
  feature-specific exceptions remain with their feature. `dto` contains the
  shared page response. `demo` contains local-profile seed data.

Controllers, services, entities, and repositories live within these feature
packages, with `dto` and `exception` subpackages where needed. The request flow is:

```text
HTTP request
  -> Spring Security (bearer token validation)
  -> MVC/controller (input validation, role checks, authenticated identity)
  -> service (transaction, ownership checks, orchestration, entity operations)
  -> Spring Data repository -> PostgreSQL
  -> response DTO -> JSON
```

Controllers do not call repositories. Delivery services construct entities from
request DTOs and map entities to response DTOs within service transactions. The
existing registration/staff-creation methods are a small mapping exception:
`AuthService` returns a `User`, and `AuthController` immediately maps its basic
fields to a response DTO. No endpoint returns a JPA entity as its JSON body.

Trace a representative request through
[DeliveryOrderController](../src/main/java/com/yusufnazim/deliverydispatch/order/DeliveryOrderController.java),
[DeliveryOrderService](../src/main/java/com/yusufnazim/deliverydispatch/order/DeliveryOrderService.java),
and [DeliveryOrderRepository](../src/main/java/com/yusufnazim/deliverydispatch/order/DeliveryOrderRepository.java).

## Authentication And Data Ownership

[AuthService](../src/main/java/com/yusufnazim/deliverydispatch/auth/AuthService.java)
trims and lowercases email addresses using `Locale.ROOT`, stores BCrypt password
hashes, and checks passwords during login. Public registration always creates a
`CUSTOMER`; only an `ADMIN` can create `DISPATCHER` or `COURIER` accounts through
the managed-user endpoint. The local seeder supplies the demo administrator.

Login issues an HS256-signed JWT containing the email subject, `userId`, `role`,
issuer, issue time, and expiration. The resource-server filter validates the
signature, configured issuer, and timestamp rules. The custom validator also
requires expiration, a nonblank subject, a positive integer user id, and a known
role. A valid token's role becomes a `ROLE_...` authority for controller
`@PreAuthorize` checks.

- Customers can create, list, read, cancel pending orders, and read timelines for
  their own orders.
- Couriers can update their own location/availability and discover, pick up, and
  complete their own assigned order.
- Dispatchers and administrators can list operational orders/couriers and assign
  couriers. `ADMIN` is not a blanket bypass for customer/courier endpoints.

Customer and courier controllers obtain the user id from the validated JWT, not
from a client-supplied ownership field. Repository queries include both the order
id and owner/courier id where appropriate. An order belonging to another customer
or courier produces the same `404 ORDER_NOT_FOUND` response as a missing order.
This separates permission to use an endpoint from permission to access a record.

[SecurityConfig](../src/main/java/com/yusufnazim/deliverydispatch/config/SecurityConfig.java)
uses stateless sessions and disables form login, HTTP Basic, and CSRF protection
for this bearer-header API. Register/login, health, Swagger UI, and OpenAPI are
public; other routes require authentication. A future cookie-based login design
would need its own CSRF protection decision.

JWTs avoid server-side login session storage here, but are signed, not encrypted.
The authentication filter does not reload the user's role from PostgreSQL on each
request. There are no refresh tokens, revocation list, or logout endpoint: issued
role claims remain a snapshot until token expiration. A server-side session design
would make central revocation easier but introduce session storage and lifecycle
management. This implementation does not claim JWTs are inherently safer.

Sources: [JWT configuration](../src/main/java/com/yusufnazim/deliverydispatch/config/JwtConfig.java),
[claim validation](../src/main/java/com/yusufnazim/deliverydispatch/security/JwtClaimsValidator.java),
and [token generation](../src/main/java/com/yusufnazim/deliverydispatch/security/JwtTokenService.java).

## Persistence Model

The compact relationship diagram below shows foreign-key ownership; the two user
relationships refer to the same `users` table:

```text
users.id           <-- delivery_orders.customer_id  (required)
users.id           <-- delivery_orders.courier_id   (nullable)
delivery_orders.id <-- delivery_events.delivery_order_id (required)
```

A user can own many orders and a courier can have many historical orders. Each
order has exactly one customer, zero or one assigned courier, and zero or more
event rows at the schema level. The application records an event when it creates
an order and at each subsequent delivery state change.

- `users` stores unique email, password hash, role, timestamps, an optimistic
  version, and nullable courier profile/location fields. New couriers start as
  `UNAVAILABLE`; non-couriers cannot have courier availability or coordinates.
- `delivery_orders` stores customer/courier foreign keys, pickup/dropoff addresses
  and coordinates, status, timestamps, and an optimistic version. Completed orders
  keep their courier link for history.
- `delivery_events` stores an order foreign key, event type, description, and
  creation time. This is a timeline beside the current order state, not event
  sourcing: the API does not reconstruct orders by replaying events. No database
  immutability policy makes these rows a tamper-proof audit log.

[Flyway migrations](../src/main/resources/db/migration) define tables, foreign
keys, allowed enum values, coordinate ranges, and the paired courier-coordinate
constraint. Hibernate validates entity/schema compatibility in the local profile;
it does not create or update the schema. Flyway executes SQL against PostgreSQL,
and PostgreSQL enforces the resulting constraints on later writes.

Existing indexes cover order customer id, courier id, and status; event order id
and creation time; unique user email; and the partial unique active-courier index
described below. These are not composite pagination indexes or spatial indexes.
Foreign keys enforce references, not that the referenced customer/courier has a
particular role; role checks belong to application code.

## Delivery Lifecycle And Transactions

[OrderStatus](../src/main/java/com/yusufnazim/deliverydispatch/order/OrderStatus.java)
defines the allowed transitions, and
[DeliveryOrder](../src/main/java/com/yusufnazim/deliverydispatch/order/DeliveryOrder.java)
checks them in its mutation methods:

```text
PENDING -> ASSIGNED -> PICKED_UP -> DELIVERED
   |
   +----> CANCELLED
```

`DELIVERED` and `CANCELLED` are terminal. Cancellation is a status change, not a
row deletion, and is only allowed while pending. There is no reassignment or
skip-pickup transition.

Write service methods use Spring `@Transactional` boundaries. Creation saves the
order and `ORDER_CREATED` event together. Assignment changes the order to
`ASSIGNED`, changes courier availability to `ON_DELIVERY`, and records
`COURIER_ASSIGNED`. Pickup records `ORDER_PICKED_UP`. Completion records
`ORDER_DELIVERED` and releases the courier to `AVAILABLE` in the same transaction.
Cancellation records `ORDER_CANCELLED`.

[DeliveryTimelineService](../src/main/java/com/yusufnazim/deliverydispatch/timeline/DeliveryTimelineService.java)
recording methods save through the repository within the calling write service's
transaction; they do not start independent event transactions. A write failure
that escapes the transaction rolls back its order, courier, and event changes.
Calling through Spring's service proxy matters: constructing a service with
`new` does not activate annotation-driven transactions.

[CourierService](../src/main/java/com/yusufnazim/deliverydispatch/courier/CourierService.java)
rejects manual changes to `ON_DELIVERY`, and rejects availability changes while
the courier is on delivery or has an active order. Location updates remain
allowed during delivery. Completion's automatic switch to `AVAILABLE` makes the
courier eligible again; the idle courier can then choose `UNAVAILABLE`.

## Assignment And Concurrency

[DispatchService](../src/main/java/com/yusufnazim/deliverydispatch/dispatch/DispatchService.java)
requires a pending order for either assignment mode:

- Automatic assignment queries all `COURIER` users who are `AVAILABLE`, have both
  coordinates, and have no `ASSIGNED` or `PICKED_UP` order. It selects the shortest
  Haversine distance to the pickup point in Java. No candidate yields
  `409 NO_ELIGIBLE_COURIER`, leaving the order pending.
- Manual assignment loads the requested courier and checks their role,
  availability, and absence of an active order. Coordinates are not required for
  manual assignment because it does not compute a distance.

[HaversineDistanceCalculator](../src/main/java/com/yusufnazim/deliverydispatch/dispatch/HaversineDistanceCalculator.java)
uses an Earth-radius approximation to calculate great-circle distance in
kilometers. It does not measure road distance, travel time, traffic, or route
feasibility. Stored coordinates have no freshness cutoff. The internal candidate
query deliberately stays unpaged: selecting from only the first operational page
could miss the nearest eligible courier. Its database/result-processing cost grows
with the candidate set; there is no production-scale performance claim.

Read-time eligibility checks alone cannot prevent two requests from assigning the
same order or courier. The implementation combines three protections:

1. `DeliveryOrder.version` rejects stale updates to the same order.
2. `User.version` rejects conflicting writes to the same courier row, including
   assignment versus availability/location/completion changes.
3. [Migration V8](../src/main/resources/db/migration/V8__enforce_active_courier_assignment.sql)
   creates `uk_delivery_orders_active_courier`, a unique index on `courier_id`
   where status is `ASSIGNED` or `PICKED_UP`. PostgreSQL prevents two active orders
   for the same non-null courier id even when they are different order rows.

The index permits multiple completed orders for a courier. It does not replace
status-transition rules or make courier availability and order status consistent
on its own. Those coupled changes remain the service transaction's responsibility.
V8 refuses existing duplicate active assignments rather than silently rewriting
them; an operator must reconcile conflicting data before retrying the migration.

Assignment flushes the persistence context before recording its event, translates
optimistic-lock or active-courier uniqueness violations to
`409 ORDER_ASSIGNMENT_CONFLICT`, and rolls the transaction back. Courier
availability/location/completion methods similarly flush and translate optimistic
conflicts to `409 COURIER_UPDATE_CONFLICT`. Flushing sends writes to PostgreSQL; it
is not a commit. The explicit translations apply to these service paths, not every
possible database exception elsewhere in the application.

This is optimistic concurrency plus a database constraint, not pessimistic
locking, a distributed lock, or an automatic retry queue. After a conflict the
caller must reload current state and decide whether to retry; automatic dispatch
does not silently try another courier. There is no general idempotency-key API.

## Read Queries And DTOs

The local profile sets `open-in-view: false`. Read services use read-only
transactions and materialize delivery response DTOs before returning, so JSON
serialization does not depend on an open persistence context.

Customer history and operational order/courier lists use database pagination:
page defaults to 0, size defaults to 20 and is limited to 1-100, and the maximum
page number keeps the offset within the supported integer range. Order pages sort
by `created_at DESC, id DESC`; courier pages sort by `id ASC`. Ownership, role, and
optional status/availability filters also apply to totals. `totalElements` counts
all matching rows, not just the returned page.

Operational order mapping reads customer/courier details. Its repository methods
use `@EntityGraph(attributePaths = {"customer", "courier"})` to fetch those to-one
relations with the page and avoid one additional query per order. Customer order
responses use only the order's own fields. Operational courier responses use user
row fields. Repository tests check bounded loading and two statements (page plus
count) for the tested full pages, with no additional queries during DTO mapping;
this is not a guarantee that every page executes exactly two statements.

Timeline reads first check customer ownership, then retrieve events ordered by
`created_at ASC, id ASC`. Current-assignment reads filter by authenticated courier
and active status, returning 200 with an order or 204 with no body. These reads do
not change the order or timeline. Offset pagination provides deterministic tie
breaking, not a snapshot across separate requests during concurrent writes.

Sources: [user queries](../src/main/java/com/yusufnazim/deliverydispatch/user/UserRepository.java),
[order queries](../src/main/java/com/yusufnazim/deliverydispatch/order/DeliveryOrderRepository.java),
and [page DTO](../src/main/java/com/yusufnazim/deliverydispatch/dto/PageResponse.java).

## Errors, Configuration, And Scope

[GlobalExceptionHandler](../src/main/java/com/yusufnazim/deliverydispatch/exception/GlobalExceptionHandler.java)
maps supported MVC input failures to 400, missing owned resources to 404, and
domain conflicts to 409. Wrong login credentials return 401. Security-filter
authentication/authorization failures use
[SecurityErrorHandler](../src/main/java/com/yusufnazim/deliverydispatch/security/SecurityErrorHandler.java)
for 401/403, since they can happen before controller execution. These responses
share `code`, `message`, and `details`; validation details are sorted field/message
pairs without rejected values, and non-validation errors have an empty array.
Unexpected server failures are not reclassified as invalid client input.

[application-local.yml](../src/main/resources/application-local.yml) contains
development-only database/JWT settings and enables idempotent demo seeding.
The local seeder preserves existing users and skips sample-order creation when
the demo customer already has orders; it is not a reset operation. The non-local
JWT secret has no usable default, and typed `JwtProperties` validates the issuer,
secret length, and positive lifetime. Public API documentation is a deliberate
demo convenience, not a claim that every deployment should expose it.

The v1 boundary is one delivery per active courier and a reproducible local demo.
Payments, restaurants/menus, external routing/geocoding, PostGIS, Redis, queues,
WebSockets, and cloud deployment are not implemented. Production operation would
also need deliberate token revocation/key rotation, login abuse controls, secret
management, TLS, operational monitoring, and workload-based performance analysis;
the current tests do not establish those capabilities.

## Verification Evidence

- [AuthenticatedDeliveryWorkflowIntegrationTest](../src/test/java/com/yusufnazim/deliverydispatch/AuthenticatedDeliveryWorkflowIntegrationTest.java)
  uses full-context MockMvc, real login JWTs and security filters, and a fresh
  Flyway-migrated PostgreSQL container. It exercises both assignment modes through
  completion and customer history/timeline, and checks committed state with JDBC
  outside a test transaction. This covers the application HTTP stack, not a live
  network listener, proxy, or TLS connection.
- [DispatchConcurrencyIntegrationTest](../src/test/java/com/yusufnazim/deliverydispatch/dispatch/DispatchConcurrencyIntegrationTest.java)
  coordinates competing transactions for different orders/same courier and the
  same order/different couriers, checking the losing transaction's rollback.
  [CourierStateIntegrationTest](../src/test/java/com/yusufnazim/deliverydispatch/courier/CourierStateIntegrationTest.java)
  covers overlapping courier writes and completion, persisted state, and events.
- [ActiveCourierMigrationTest](../src/test/java/com/yusufnazim/deliverydispatch/dispatch/ActiveCourierMigrationTest.java)
  checks V7-to-V8 upgrades with existing data, including refusal of conflicting
  assignments. [DeliveryOrderRepositoryTest](../src/test/java/com/yusufnazim/deliverydispatch/order/DeliveryOrderRepositoryTest.java)
  and [UserRepositoryTest](../src/test/java/com/yusufnazim/deliverydispatch/user/UserRepositoryTest.java)
  cover constraints, ownership/filter totals, pagination, and query behavior.
- [JwtBearerAuthenticationTest](../src/test/java/com/yusufnazim/deliverydispatch/config/JwtBearerAuthenticationTest.java)
  covers real signed-token validation and role failures. Focused service/entity
  and MVC tests cover business rules, validation, and response contracts.

Run `./mvnw verify` with Docker available. All PostgreSQL test classes require
Docker rather than silently skipping. The [CI workflow](../.github/workflows/test.yml)
checks Docker first, runs the same Maven verification lifecycle, and retains
available failure reports for 7 days. These tests provide regression evidence for
the cases above, not a proof of every possible concurrency schedule or a benchmark.
