# Currency Exchange Rates Provider Service

Spring Boot REST API for managing supported currencies, fetching exchange rates from external providers, converting amounts, and analyzing rate trends over time.

## What This Service Does

- Stores a list of active currencies in a PostgreSQL database.
- Fetches latest exchange rates from multiple external and internal providers.
- Stores both latest rates and historical snapshots for trend analysis.
- Converts amounts between currencies using a two-layer cache (JVM + Redis).
- Calculates trend direction and percent change for a selected period.
- Refreshes rates on startup and on a configurable schedule.

## Tech Stack

- Java 21
- Spring Boot 3.2.x
- Spring Web + WebFlux WebClient
- Spring Data JPA
- Spring Security (HTTP Basic, DB-backed users)
- PostgreSQL
- Liquibase (schema migrations)
- Redis (distributed cache)
- Spring Scheduling + Spring Cache
- Resilience4j (circuit breaker)
- Springdoc OpenAPI / Swagger UI
- Lombok
- Maven

## Main Components

- API layer: src/main/java/com/example/currencyexchange/controller/CurrencyController.java
- Business logic: src/main/java/com/example/currencyexchange/service/CurrencyService.java and src/main/java/com/example/currencyexchange/service/ExchangeRateService.java
- Rate cache: src/main/java/com/example/currencyexchange/service/RateCacheService.java
- Provider clients: src/main/java/com/example/currencyexchange/client
- Scheduled refresh: src/main/java/com/example/currencyexchange/scheduler/ExchangeRateScheduler.java
- Security config: src/main/java/com/example/currencyexchange/config/SecurityConfig.java
- App config: src/main/resources/application.yml
- DB migrations: src/main/resources/db/changelog

## Security And Roles

Authentication is HTTP Basic. Users and roles are stored in PostgreSQL and seeded via Liquibase.

Default users (seeded on first startup):

- user / user123 (role USER)
- admin / admin123 (roles ADMIN, USER)
- premium / premium123 (roles PREMIUM_USER, USER)

Endpoint access:

- Public (no authentication):
  - GET /actuator/health
  - GET /swagger-ui.html, /swagger-ui/**, /v3/api-docs/**
- USER, PREMIUM_USER, or ADMIN:
  - GET /api/v1/currency
  - GET /api/v1/currency/exchange-rates
- ADMIN only:
  - POST /api/v1/currency
  - POST /api/v1/currency/exchange-rates/refresh
- ADMIN or PREMIUM_USER:
  - GET /api/v1/currency/exchange-rates/trends

## API Endpoints

Base path: /api/v1/currency

1. GET /api/v1/currency
	- Returns all active currencies.
	- Requires authentication (USER, PREMIUM_USER, or ADMIN).

2. POST /api/v1/currency?currency=USD
	- Adds a new active currency.
	- Requires ADMIN.

3. GET /api/v1/currency/exchange-rates?amount=100&from=USD&to=EUR
	- Converts an amount using the cached exchange rate.
	- Requires authentication (USER, PREMIUM_USER, or ADMIN).
	- Returns 503 if the rate is not in cache (refresh first).

4. POST /api/v1/currency/exchange-rates/refresh
	- Forces rate refresh from all configured providers.
	- Requires ADMIN.

5. GET /api/v1/currency/exchange-rates/trends?from=USD&to=EUR&period=24H
	- Returns trend data (UP, DOWN, STABLE) and percentage change.
	- Requires ADMIN or PREMIUM_USER.
	- Period format: n + unit, where unit is H, D, M, or Y.
	- Example periods: 12H, 10D, 3M, 1Y.

Interactive API docs: http://localhost:8080/swagger-ui.html

## External Rate Providers

All providers contribute rates; the service merges them using a last-write-wins strategy per currency pair.

Internal provider services (run alongside the main service):

- Provider 1: http://localhost:8081/rates (EUR-based, simulated ±5% randomization)
- Provider 2: http://localhost:8082/rates (EUR-based, simulated ±5% randomization)
- Provider 3: http://localhost:8083/rates (EUR-based, simulated ±5% randomization)

External providers:

- Frankfurter (always enabled): https://api.frankfurter.app
- OpenExchangeRates (optional): https://openexchangerates.org/api

OpenExchangeRates is enabled only when this property is set:

	exchange.providers.openexchangerates.api-key=your-api-key

## Quick Start

### Option A — Docker Compose (recommended)

Starts all services: main app, PostgreSQL, Redis, and all three internal providers.

Prerequisites: Docker and Docker Compose.

```bash
docker-compose up --build
```

Service starts on http://localhost:8080.

Stop everything:

```bash
docker-compose down
```

### Option B — Local (Maven)

Prerequisites:

- Java 21 or newer
- Maven 3.9+
- PostgreSQL running on localhost:5432 with database `currencydb`, user `currency_user`, password `currency_pass`
- Redis running on localhost:6379

Build and run:

```bash
mvn clean package
mvn spring-boot:run
```

To override datasource or Redis connection, set environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/currencydb
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
SPRING_REDIS_HOST=...
SPRING_REDIS_PORT=6379
```

Liquibase runs automatically on startup and applies all schema migrations and default user seeds.

## First Run Workflow

No currencies are pre-seeded. After startup, add currencies, then refresh rates.

```bash
# Add currencies
curl -u admin:admin123 -X POST "http://localhost:8080/api/v1/currency?currency=EUR"
curl -u admin:admin123 -X POST "http://localhost:8080/api/v1/currency?currency=USD"

# Refresh rates from all providers
curl -u admin:admin123 -X POST "http://localhost:8080/api/v1/currency/exchange-rates/refresh"

# Convert an amount
curl -u user:user123 "http://localhost:8080/api/v1/currency/exchange-rates?amount=100&from=USD&to=EUR"

# Read trend (admin or premium)
curl -u premium:premium123 "http://localhost:8080/api/v1/currency/exchange-rates/trends?from=USD&to=EUR&period=24H"
```

## Running Tests

Unit and integration tests use Testcontainers (PostgreSQL) and WireMock. Docker must be available.

```bash
mvn test
```

## Configuration

Main configuration file: src/main/resources/application.yml

Key settings:

- Server port (default: 8080)
- PostgreSQL datasource (overridable via environment variables)
- Redis connection (overridable via environment variables)
- Liquibase changelog path
- Scheduler cron expression (default: every hour)
- Provider base URLs (frankfurter, openexchangerates, provider1, provider2, provider3)
- Springdoc/Swagger UI paths
- Logging levels

## Error Handling

The API returns structured JSON error responses with the following fields: `status`, `error`, `message`, `path`, `timestamp`.

Global exception mapping is implemented in:

- src/main/java/com/example/currencyexchange/exception/GlobalExceptionHandler.java