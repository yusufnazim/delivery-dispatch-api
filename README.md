# Delivery Dispatch API

Spring Boot backend for a delivery dispatch workflow, built as a junior backend engineering portfolio project.

## Tech Stack

- Java 21
- Spring Boot
- Spring Web MVC
- Spring Security
- Spring Data JPA
- Jakarta Validation
- PostgreSQL
- Flyway
- Testcontainers
- Maven

## Local Development

Run tests:

```bash
./mvnw test
```

Start the API:

```bash
./mvnw spring-boot:run
```

Start local PostgreSQL:

```bash
docker compose up -d postgres
```

Stop local PostgreSQL:

```bash
docker compose down
```

Stop local PostgreSQL and remove its data volume:

```bash
docker compose down -v
```

The API will expose versioned endpoints under `/api/v1` as features are implemented.
