# STF (Save The Factory) Backend

등속조인트 생산설비 통합관제 시스템의 Spring Boot 백엔드입니다.

## Technology

- Java 21
- Spring Boot 4.1.2
- Spring Web MVC, Spring Security, OAuth2 Resource Server
- Spring Data JPA, MySQL 8.4, Flyway
- Spring Data Redis, Redis 7.4
- WebSocket/STOMP
- OpenAPI 3.0.3 and Swagger UI
- Gradle 9.8.2

## Prerequisites

- JDK 21
- Docker Desktop
- Git

## First run

Copy the local Docker environment example.

```powershell
Copy-Item .env.example .env
```

Start MySQL and Redis.

```powershell
docker compose up -d
docker compose ps
```

Run tests. Tests use an in-memory H2 database and create the Redis client
configuration without connecting to a Redis server, so Docker is not required.

```powershell
.\gradlew.bat clean test
```

Prepare the local JWT signing key once. The script preserves an existing valid key and prints no secret value.

```powershell
.\scripts\Setup-LocalJwt.ps1
```

The application reads `secrets/jwt-local.properties` from the project working directory.
This file is ignored by Git. `JWT_SECRET` in the process environment takes precedence;
it must contain at least 32 UTF-8 bytes. A missing or short key still stops startup.
Docker Compose's `.env` file is not automatically passed to an IDE Java process.

Run the backend.

```powershell
.\gradlew.bat bootRun
```

IntelliJ or STS: set the run configuration working directory to this `stf` folder,
then run `StfApplication` again. For B features, activate the `backend-b` profile and
supply the database settings separately. The existing `backend-b,manual-test` setup
uses its own secret file, dedicated database, and port 8081; it is unchanged.

## Local URLs

- Application: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

Spring Security can require authentication until the team security configuration is implemented.

## Backend responsibility

- Node-RED receives Modbus, OPC UA and MQTT data and forwards normalized data over HTTP/REST.
- Spring Boot validates incoming data, serves REST APIs and sends real-time WebSocket messages.
- Redis keeps current equipment state, last-received timestamps and latest values with TTLs.
- MySQL keeps persistent history, alarms, actions and master data.
- Modbus, OPC UA and MQTT client libraries are intentionally not included in this backend project.

## Stop services

```powershell
docker compose stop
```

Use `docker compose down` only when you intentionally want to remove containers. The named `mysql-data` volume keeps database data unless the volume is also deleted.

## Collaboration

Read [CONTRIBUTING.md](CONTRIBUTING.md) before starting development. API changes must update [docs/openapi.yaml](docs/openapi.yaml) in the same pull request.
