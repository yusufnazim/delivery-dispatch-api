# Delivery Dispatch API Roadmap

Public roadmap for shipped behavior and remaining work. `PLAN.md` remains the local implementation tracker.

## Shipped

- Spring Boot API skeleton with Java 21, Maven Wrapper, local PostgreSQL Compose setup, Flyway, OpenAPI, and CI test workflow.
- User persistence with roles for `ADMIN`, `DISPATCHER`, `COURIER`, and `CUSTOMER`.
- Customer registration, login, JWT generation, JWT bearer authentication, and structured auth error responses.
- JWT issuer and required identity/expiration validation, preserving HS256 signature and timestamp checks; invalid tokens return 401 before reaching controllers.
- Signed-token regression tests for missing/malformed claims, expired tokens, future `nbf` values, wrong signing keys, tampering, and unsigned tokens; valid wrong-role access still returns 403.
- Admin-only managed user creation for dispatcher and courier accounts.
- Delivery order persistence model with customer and courier links, explicit order status values, pickup/dropoff coordinates, optimistic versioning, and Flyway schema migration.
- Delivery order creation request and response DTOs with address and coordinate validation.
- Customer order creation service that saves pending orders for authenticated customer ids.
- Customer order creation endpoint restricted to authenticated customers.
- Customer order lookup and listing service with ownership-safe not-found behavior.
- Customer order lookup and listing endpoints restricted to authenticated customers.
- Database-backed customer order pagination with optional page/size parameters (defaults 0/20, maximum size 100), a stable page response, ownership-filtered totals, and deterministic timestamp/id ordering.
- MVC, serialization, OpenAPI, and PostgreSQL pagination coverage for bounds, ownership, page boundaries, empty results, and bounded entity loading.
- Customer order cancellation service for owned pending orders.
- Customer order cancellation endpoint restricted to authenticated customers.
- Courier profile fields persisted on user accounts with explicit Flyway schema support.
- Courier availability status persisted with default `UNAVAILABLE` state for courier users.
- Courier availability update endpoint restricted to authenticated couriers.
- Manual availability changes rejected during assigned or picked-up deliveries; completion still releases the courier to `AVAILABLE`.
- Optional courier latitude and longitude fields persisted with explicit Flyway schema support.
- Courier location update endpoint restricted to authenticated couriers.
- Courier-only current-assignment lookup using JWT identity, returning the active order's addresses, coordinates, and status or an empty 204 when idle.
- Availability, location, and completion writes translate optimistic version conflicts to `409 COURIER_UPDATE_CONFLICT`, rolling back the full transaction.
- Reusable Haversine distance calculator for dispatch decisions.
- Eligible courier lookup for dispatch based on availability and known location.
- Nearest eligible courier selection using pickup coordinates and Haversine distance.
- Auto-dispatch service assignment that attaches the nearest eligible courier to a pending order.
- Manual dispatch service assignment for assigning a specific available courier to a pending order.
- One-active-delivery enforcement under concurrent manual and automatic assignment, backed by a partial unique PostgreSQL index.
- Double-assignment prevention for orders that are already assigned.
- Transaction coverage for auto-dispatch and manual courier assignment.
- Order and user optimistic versioning, with assignment version/active-courier uniqueness conflicts translated to HTTP 409 and full transaction rollback.
- Explicit delivery status transition rules for assignment, pickup, delivery, cancellation, and terminal states.
- Courier pickup action that lets the assigned courier move an order from assigned to picked up.
- Courier delivery completion action that marks picked-up orders as delivered and makes the courier available again.
- Delivery event persistence model for storing order timeline history.
- Automatic delivery timeline event records for order creation, assignment, pickup, delivery, and cancellation.
- Customer order timeline endpoint for viewing owned delivery history.
- PostgreSQL-backed integration coverage for the customer delivery workflow from order creation through delivery.
- Full-context MockMvc workflow coverage with real login JWTs, Spring Security, PostgreSQL/Flyway, and Open Session in View disabled; both assignment modes lead through courier discovery, pickup, completion, and customer history/timeline retrieval.
- Independent JDBC checks for committed workflow state, released courier availability, and timeline records, plus unchanged persisted state after authentication, role, ownership, and transition failures.
- Mandatory PostgreSQL/Testcontainers tests that fail when Docker is unavailable; CI checks Docker access, runs Maven verification, and retains available test reports for 7 days on failure.
- PostgreSQL-backed integration coverage for no-courier auto-dispatch failure behavior.
- PostgreSQL-backed integration coverage for manual assignment conflict behavior.
- PostgreSQL-backed coverage for sequential double-assignment rejection and stale same-order version conflicts.
- Deterministic two-transaction PostgreSQL tests for manual/manual, auto/auto, mixed assignment, same-order competition, loser rollback, and courier reuse after completion.
- PostgreSQL regression and coordinated race tests for active-delivery availability guards, courier updates versus assignment/completion, and state/timeline rollback.
- Flyway V8 upgrade tests for existing data, including non-destructive refusal of duplicate active courier assignments.
- PostgreSQL-backed integration coverage for invalid delivery status transitions.
- MVC slice coverage for delivery order and courier request validation, authentication, and role access.
- Consistent 400 error bodies for unreadable JSON, unknown enum values, missing/unconvertible parameters, and MVC input validation, without leaking rejected values or parser details.
- MVC regression coverage for malformed request errors while preserving existing DTO, authentication, authorization, and domain error behavior; return-value validation remains a server error.
- Field-level DTO and controller parameter validation details using public field names and safe messages, with stable ordering and no rejected values; other API errors include an empty details array.
- Serialization, multi-field validation, password/token non-disclosure, real JWT error serialization, and OpenAPI schema coverage for validation details.
- Service, MVC, and PostgreSQL coverage for current-assignment ownership, active statuses, completed-history exclusion, and reads without state or timeline changes.
- Local-profile demo data for every user role, two available couriers with locations, and pending and completed sample orders with timeline history.
- OpenAPI operation documentation with endpoint summaries, role requirements, response descriptions, and a reusable JWT bearer security scheme.
- Public Swagger UI and OpenAPI JSON access for reviewing and trying the documented API.
- README local development guide covering prerequisites, Docker and profile startup, demo accounts, configuration, tests, and database operations.
- Runnable local API walkthrough for customer login, seeded timeline review, order creation, and courier location and availability updates.
- Dispatcher/admin auto-assignment endpoint that returns the selected courier and delegates to the transactional nearest-courier dispatch service.
- Dispatcher/admin manual-assignment endpoint with validated courier selection and existing eligibility/conflict rules.
- Dispatcher/admin operational order listing with customer, courier, route, status, and timestamp details.
- Bounded operational order pagination with optional status filtering, stable timestamp/id ordering, and matching totals in the shared page response.
- Service, MVC, OpenAPI, and PostgreSQL checks for all status filters, role restrictions, page bounds and empty results, plus bounded entity loading and N+1-safe customer/courier mapping.
- Dispatcher/admin courier listing with profile, availability, and stored location details.
- Bounded operational courier pagination with optional availability filtering, courier-only totals, and stable id ordering; the nearest-courier candidate lookup remains unpaged.
- Service, MVC, OpenAPI, and PostgreSQL coverage for availability filters, page boundaries, role isolation, validation, bounded entity loading, and dispatch candidates beyond the first display page.
- Full local delivery walkthrough from order creation through auto-assignment, pickup, delivery, and timeline review.
- [Backend architecture](docs/architecture.md) with source-linked package boundaries, schema relationships, security/ownership rules, transactions, concurrency protection, query behavior, and explicit v1 limits.
- Verified demo guide for both assignment modes and separate cancellation, with response-derived IDs, seeded role logins, repeat-demo guidance, and clean-build/jar commands.

## Final Verification

Verified locally on 2026-10-29:

- `./mvnw --batch-mode --no-transfer-progress clean verify`: 564 tests passed with zero failures, errors, or skips; executable jar built.
- Fresh PostgreSQL startup through Compose, all eight Flyway migrations, packaged-jar and Maven startup, public health/Swagger/OpenAPI, and unchanged seed/history data across restarts.
- 69 live HTTP audit requests covering real authentication, both assignment modes, courier actions, timelines, cancellation, pagination/filtering, and rejected requests without persisted-state changes.
- All 49 edited `api-tests.http` examples replayed with actual tokens and IDs; Markdown links and shell syntax checked.
- Disposable audit infrastructure removed; existing development data preserved. Git authorship and local-only file exclusions checked.

The preceding architecture commit `b563768` passed [GitHub Actions](https://github.com/yusufnazim/delivery-dispatch-api/actions/runs/40871408372). Confirm CI for each newly pushed revision; local verification does not establish its remote result.

## Remaining

No planned v1 feature or documentation commits remain. Interview/portfolio material was excluded at the user's request. New defects should receive focused fixes with regression tests, not artificial commits to meet a count.

Redis, queues, microservices, external routing, and cloud deployment remain future scope, not prerequisites for this v1.
