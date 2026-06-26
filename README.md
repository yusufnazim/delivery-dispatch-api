# Delivery Dispatch API

Spring Boot backend for a delivery dispatch workflow, built as a junior backend engineering portfolio project.

## Tech Stack

- Java 21
- Spring Boot
- Spring Web MVC
- Spring Security
- Spring Data JPA
- Jakarta Validation
- OpenAPI
- PostgreSQL
- Flyway
- Testcontainers
- Maven

## Local Development

Run tests:

```bash
./mvnw test
```

Start the API with the local profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Start local PostgreSQL:

```bash
docker compose up -d postgres
```

If port `5432` is already in use locally, start PostgreSQL on another host port:

```bash
POSTGRES_PORT=5433 docker compose up -d postgres
```

Then run the API with the same port:

```bash
POSTGRES_PORT=5433 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Stop local PostgreSQL:

```bash
docker compose down
```

Stop local PostgreSQL and remove its data volume:

```bash
docker compose down -v
```

Database schema changes are managed with Flyway migrations in `src/main/resources/db/migration`.

The OpenAPI JSON document is available locally at:

```text
http://localhost:8080/v3/api-docs
```

Runnable HTTP examples are kept in `api-tests.http`.

Application endpoints use versioned paths under `/api/v1`.

## Authentication

The API currently supports JWT-based authentication for the delivery dispatch user model.

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public | Register a customer account. |
| `POST` | `/api/v1/auth/login` | Public | Log in with email and password and receive a JWT. |
| `POST` | `/api/v1/auth/users` | `ADMIN` only | Create dispatcher or courier accounts. |

Registration and login normalize email addresses by trimming whitespace and lowercasing them. Passwords are stored with BCrypt hashes, not plaintext values.

JWTs include the authenticated user's role. Protected endpoints expect the token in this format:

```text
Authorization: Bearer <jwt>
```

The managed user creation endpoint requires an existing admin token and only accepts `DISPATCHER` or `COURIER` as the new user's role. Customer accounts are created through `/api/v1/auth/register`.

Auth errors use structured JSON responses:

- `400 Bad Request` for validation failures.
- `401 Unauthorized` for missing or invalid credentials.
- `403 Forbidden` for authenticated users without the required role.
- `409 Conflict` for duplicate email addresses.

JWT settings are configured through `app.jwt` properties:

- `JWT_SECRET` must be set outside local development and must be at least 32 characters.
- `JWT_ISSUER` defaults to `delivery-dispatch-api`.
- `JWT_EXPIRATION` defaults to `1h`.

The `local` profile provides a development-only JWT secret so the API can run locally without production secrets.
