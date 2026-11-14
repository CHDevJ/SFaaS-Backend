# STF (Save The Factory) Backend

등속조인트 생산설비 통합관제 시스템의 Spring Boot 백엔드입니다.

## Technology

- Java 21
- Spring Boot 4.1.2
- Spring Web MVC, Spring Security, OAuth2 Resource Server
- Spring Data JPA, MySQL 8.4, Flyway
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

Start MySQL.

```powershell
docker compose up -d
docker compose ps
```

Run tests. Tests use an in-memory H2 database and do not require Docker.

```powershell
.\gradlew.bat clean test
```

Run the backend.

```powershell
.\gradlew.bat bootRun
```

## Local URLs

- Application: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

Spring Security can require authentication until the team security configuration is implemented.

## Stop services

```powershell
docker compose stop
```

Use `docker compose down` only when you intentionally want to remove containers. The named `mysql-data` volume keeps database data unless the volume is also deleted.

## Collaboration

Read [CONTRIBUTING.md](CONTRIBUTING.md) before starting development. API changes must update [docs/openapi.yaml](docs/openapi.yaml) in the same pull request.
