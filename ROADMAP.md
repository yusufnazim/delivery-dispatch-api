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
- Dispatcher/admin courier listing with profile, availability, and stored location details.
- Full local delivery walkthrough from order creation through auto-assignment, pickup, delivery, and timeline review.

## Remaining

The current plan has 8 remaining focused commits across API usability, verification, and portfolio preparation. Counts may change as work is verified; the outcomes below define completion.

### API Usability

- Add bounded customer order pagination with a stable response contract.
- Add bounded dispatcher/admin order pagination and status filtering while preserving N+1-safe fetching.
- Add bounded dispatcher/admin courier pagination and availability filtering.

### Verification And CI

- Exercise real login, JWT-authenticated requests, assignment, courier actions, and persisted timeline in a full application PostgreSQL workflow test.
- Require database-backed tests in full verification and CI; fail rather than silently skip when Docker is unavailable.

### Portfolio Preparation

- Document the implemented architecture, schema relationships, transaction/concurrency guarantees, and deliberate v1 tradeoffs.
- Prepare a technical project walkthrough and evidence-based achievements for interviews and resume use.
- Finish the clean-start demo, code/documentation consistency checks, Git-history review, and final verification. Keep API examples current as each behavior changes.

Redis, queues, microservices, external routing, and cloud deployment remain future scope, not prerequisites for this v1.
