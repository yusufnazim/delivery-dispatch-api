# Delivery Dispatch API Roadmap

Public roadmap for shipped behavior and remaining work. `PLAN.md` remains the local implementation tracker.

## Shipped

- Spring Boot API skeleton with Java 21, Maven Wrapper, local PostgreSQL Compose setup, Flyway, OpenAPI, and CI test workflow.
- User persistence with roles for `ADMIN`, `DISPATCHER`, `COURIER`, and `CUSTOMER`.
- Customer registration, login, JWT generation, JWT bearer authentication, and structured auth error responses.
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
- Optional courier latitude and longitude fields persisted with explicit Flyway schema support.
- Courier location update endpoint restricted to authenticated couriers.
- Reusable Haversine distance calculator for dispatch decisions.
- Eligible courier lookup for dispatch based on availability and known location.
- Nearest eligible courier selection using pickup coordinates and Haversine distance.
- Auto-dispatch service assignment that attaches the nearest eligible courier to a pending order.
- Manual dispatch service assignment for assigning a specific available courier to a pending order.

## Remaining

- Assignment conflict safety and one-active-delivery enforcement.
- Delivery lifecycle transitions and timeline records.
- PostgreSQL-backed integration coverage for dispatch and workflow conflict paths.
- Local demo kit with seed data, HTTP walkthrough, and concise README updates.
