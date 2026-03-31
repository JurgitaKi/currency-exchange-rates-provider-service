# Currency Exchange Rates Provider Service

Spring Boot REST API for managing supported currencies, fetching exchange rates from external providers, converting amounts, and analyzing rate trends over time.

## What This Service Does

- Stores a list of active currencies in a database.
- Fetches latest exchange rates from external providers.
- Stores both latest rates and historical snapshots.
- Converts amounts between currencies using stored rates.
- Calculates trend direction and percent change for a selected period.
- Refreshes rates on startup and on a schedule.

## Tech Stack

- Java 21
- Spring Boot 3.2.x
- Spring Web + WebFlux WebClient
- Spring Data JPA
- Spring Security (HTTP Basic)
- H2 in-memory database
- Spring Scheduling + Spring Cache
- Resilience4j
- Maven

## Main Components

- API layer: src/main/java/com/example/currencyexchange/controller/CurrencyController.java
- Business logic: src/main/java/com/example/currencyexchange/service/CurrencyService.java and src/main/java/com/example/currencyexchange/service/ExchangeRateService.java
- Provider clients: src/main/java/com/example/currencyexchange/client
- Scheduled refresh: src/main/java/com/example/currencyexchange/scheduler/ExchangeRateScheduler.java
- Security config: src/main/java/com/example/currencyexchange/config/SecurityConfig.java
- App config: src/main/resources/application.yml

## Security And Roles

Authentication is HTTP Basic.

Default in-memory users:

- user / user123 (role USER)
- admin / admin123 (roles ADMIN, USER)
- premium / premium123 (roles PREMIUM_USER, USER)

Endpoint access:

- Public:
  - GET /api/v1/currency
  - GET /api/v1/currency/exchange-rates
  - GET /actuator/health
  - /h2-console
- ADMIN:
  - POST /api/v1/currency
  - POST /api/v1/currency/exchange-rates/refresh
- ADMIN or PREMIUM_USER:
  - GET /api/v1/currency/exchange-rates/trends

## API Endpoints

Base path: /api/v1/currency

1. GET /api/v1/currency
	- Returns all active currencies.

2. POST /api/v1/currency?currency=USD
	- Adds a new active currency.
	- Requires ADMIN.

3. GET /api/v1/currency/exchange-rates?amount=100&from=USD&to=EUR
	- Converts amount using stored exchange rate.

4. POST /api/v1/currency/exchange-rates/refresh
	- Forces rate refresh from external providers.
	- Requires ADMIN.

5. GET /api/v1/currency/exchange-rates/trends?from=USD&to=EUR&period=24H
	- Returns trend data (UP, DOWN, STABLE) and percentage change.
	- Requires ADMIN or PREMIUM_USER.
	- Period format: n + unit, where unit is H, D, M, or Y.
	- Example periods: 12H, 10D, 3M, 1Y.

## External Rate Providers

- Frankfurter (enabled by default): https://api.frankfurter.app
- OpenExchangeRates (optional fallback): https://openexchangerates.org/api

OpenExchangeRates client is enabled only when this property is configured:

exchange.providers.openexchangerates.api-key=your-api-key

## Quick Start (Local)

Prerequisites:

- Java 21 or newer
- Maven 3.9+

From repository root:

1. Build:
	mvn clean package

2. Run:
	mvn spring-boot:run

Service starts on:

- http://localhost:8080

Useful local endpoints:

- Health: http://localhost:8080/actuator/health
- H2 console: http://localhost:8080/h2-console

H2 connection settings (default):

- JDBC URL: jdbc:h2:mem:currencydb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
- Username: sa
- Password: (empty)

## First Run Workflow

No currencies are pre-seeded. After startup, add currencies first, then refresh rates.

Example with curl:

1. Add EUR:
	curl -u admin:admin123 -X POST "http://localhost:8080/api/v1/currency?currency=EUR"

2. Add USD:
	curl -u admin:admin123 -X POST "http://localhost:8080/api/v1/currency?currency=USD"

3. Refresh rates:
	curl -u admin:admin123 -X POST "http://localhost:8080/api/v1/currency/exchange-rates/refresh"

4. Convert amount:
	curl "http://localhost:8080/api/v1/currency/exchange-rates?amount=100&from=USD&to=EUR"

5. Read trend (admin or premium):
	curl -u premium:premium123 "http://localhost:8080/api/v1/currency/exchange-rates/trends?from=USD&to=EUR&period=24H"

## Running Tests

Run all tests:

mvn test

## Docker

A Dockerfile is present and uses a multi-stage build.

Important: the current Dockerfile copies Maven wrapper files (.mvn and mvnw). If those files are not present in your checkout, Docker build will fail until either:

- Maven wrapper files are added, or
- Dockerfile is adjusted to use installed Maven inside the build stage.

## Configuration

Main configuration file:

- src/main/resources/application.yml

Key settings include:

- Server port
- H2 datasource
- JPA mode (create-drop)
- Scheduler cron
- Provider base URLs
- Logging levels

## Error Handling

The API returns structured error responses with status, error, message, path, and timestamp.

Global exception mapping is implemented in:

- src/main/java/com/example/currencyexchange/exception/GlobalExceptionHandler.java