# LedgerLite: Progress

Repo: https://github.com/Drv2709/ledgerlite
Last updated: 2026-11-13

## Status

| Phase | Scope                                                              | Status            |
| ----- | ------------------------------------------------------------------ | ----------------- |
| P0    | ERD, API contract, docker-compose, pom, application.yml, V1 Flyway | Done and verified |
| P1    | Spring Boot scaffold, DB connection, health check                  | In progress       |
| P2    | Spring Security, JWT, auth endpoints, User entity                  | Not started       |
| P3    | Account and Transaction entities, double-entry ledger logic        | Not started       |
| P4    | Transfer endpoint (atomic, locking, idempotency)                   | Not started       |
| P5    | Integration and concurrency tests (Testcontainers, JUnit)          | Not started       |
| P6    | React scaffold and auth integration                                | Not started       |
| P7    | Dashboard and transaction history                                  | Not started       |
| P8    | Full-stack Docker Compose and GitHub Actions CI                    | Not started       |
| P9    | Deploy, README, architecture diagram                               | Not started       |

## P0: Planning and Data Architecture (done)

### Completed

- [x] `docs/ERD.md`: ERD, no balance column, double-entry rules, BigDecimal / `NUMERIC(19,4)` money policy, idempotency strategy, pessimistic locking (ascending UUID lock order), IDOR rules, indexes
- [x] `docs/API_CONTRACT.md`: all endpoints with JSON in/out, error format, cookie-based JWT notes
- [x] `docker-compose.yml`: PostgreSQL 16, healthcheck, persistent volume
- [x] `pom.xml`: Java 21, Spring Boot 3.3.5, Security, JPA, Flyway, jjwt, Testcontainers
- [x] `application.yml`: env-driven datasource, `ddl-auto: validate`, Flyway enabled
- [x] `V1__init.sql`: all tables and constraints, append-only ledger trigger, deferred debit = credit trigger, seeded SYSTEM account
- [x] Maven wrapper generated (`mvnw`, `.mvn/`)
- [x] Verified: Postgres healthy, Flyway migration applied, app starts

### Key decisions

- IDs: UUID
- Concurrency: pessimistic locking (`PESSIMISTIC_WRITE`)
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- Funding: SYSTEM account posts opening deposits and is the only account allowed to go negative
- Local Postgres host port is **5455**, because 5432 and 5433 were already taken on the dev machine
- Cookie-based JWT needs CSRF protection (SameSite plus CSRF header), planned for P2

### Known open item

- GitHub push is not working yet (authentication). Commits are local only. To be fixed before P8 (CI).

## Next

P1: `/health` endpoint with a real DB check, and a minimal stateless security config.

# LedgerLite: Progress

Repo: https://github.com/Drv2709/ledgerlite
Last updated: 2026-11-13

## Status

| Phase | Scope                                                              | Status            |
| ----- | ------------------------------------------------------------------ | ----------------- |
| P0    | ERD, API contract, docker-compose, pom, application.yml, V1 Flyway | Done and verified |
| P1    | Spring Boot scaffold, DB connection, health check                  | Done and verified |
| P2    | Spring Security, JWT, auth endpoints, User entity                  | In progress       |
| P3    | Account and Transaction entities, double-entry ledger logic        | Not started       |
| P4    | Transfer endpoint (atomic, locking, idempotency)                   | Not started       |
| P5    | Integration and concurrency tests (Testcontainers, JUnit)          | Not started       |
| P6    | React scaffold and auth integration                                | Not started       |
| P7    | Dashboard and transaction history                                  | Not started       |
| P8    | Full-stack Docker Compose and GitHub Actions CI                    | Not started       |
| P9    | Deploy, README, architecture diagram                               | Not started       |

## P0: Planning and Data Architecture (done)

- [x] ERD, API contract, plan, docker-compose, pom, application.yml, V1 Flyway schema (append-only ledger trigger, balanced-transaction trigger, SYSTEM account seed), Maven wrapper
- [x] Verified: Postgres healthy, Flyway applied, app starts

## P1: Scaffold and Health Check (done)

- [x] `SecurityConfig`: stateless, only `/health` and `/actuator/health/**` public, everything else 401
- [x] `HealthController`: `GET /health` runs `SELECT 1`, returns 200 `{"status":"UP"}` or 503 `{"status":"DOWN"}`
- [x] Verified: `/health` 200, `/api/accounts` 401, `/actuator/health` 200, `/health` 503 with Postgres stopped and 200 again after restart

## Key decisions

- IDs: UUID
- Concurrency: pessimistic locking (`PESSIMISTIC_WRITE`), accounts locked in ascending UUID order
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- Funding: SYSTEM account posts opening deposits and is the only account allowed to go negative
- Local Postgres host port is **5455** (5432 and 5433 were taken on the dev machine)

## Known open item

- GitHub push is not working yet (authentication). Commits are local only. To be fixed before P8 (CI).

## Next

P2: User entity, BCrypt, JWT in an HttpOnly cookie, CSRF protection, `/api/auth/*` endpoints.

# LedgerLite: Progress

Repo: https://github.com/Drv2709/ledgerlite
Last updated: 2026-11-14

## Status

| Phase | Scope                                                              | Status            |
| ----- | ------------------------------------------------------------------ | ----------------- |
| P0    | ERD, API contract, docker-compose, pom, application.yml, V1 Flyway | Done and verified |
| P1    | Spring Boot scaffold, DB connection, health check                  | Done and verified |
| P2    | Spring Security, JWT, auth endpoints, User entity                  | Done and verified |
| P3    | Account and Transaction entities, double-entry ledger logic        | In progress       |
| P4    | Transfer endpoint (atomic, locking, idempotency)                   | Not started       |
| P5    | Integration and concurrency tests (Testcontainers, JUnit)          | Not started       |
| P6    | React scaffold and auth integration                                | Not started       |
| P7    | Dashboard and transaction history                                  | Not started       |
| P8    | Full-stack Docker Compose and GitHub Actions CI                    | Not started       |
| P9    | Deploy, README, architecture diagram                               | Not started       |

## P0: Planning and Data Architecture (done)

- [x] ERD, API contract, plan, docker-compose, pom, application.yml, V1 Flyway schema (append-only ledger trigger, balanced-transaction trigger, SYSTEM account seed), Maven wrapper

## P1: Scaffold and Health Check (done)

- [x] `GET /health` with a real DB check (200 UP / 503 DOWN), stateless security baseline

## P2: Authentication and Security (done)

- [x] `User` entity, BCrypt (cost 12), `CustomUserDetailsService`, `AuthenticatedUser` principal carrying the user id
- [x] `JwtService` (jjwt, HS384, secret length check), `JwtAuthenticationFilter` reading the `LL_TOKEN` HttpOnly cookie
- [x] CSRF double-submit (`XSRF-TOKEN` cookie plus `X-XSRF-TOKEN` header); register and login exempt
- [x] `POST /api/auth/register`, `/login`, `/logout`, `GET /api/auth/me`
- [x] Global JSON error format (`ApiError`), 401 / 403 handled by `SecurityErrorHandler`
- [x] Verified: register 201, duplicate 409, bad input 400, `/me` 200 then 401 after logout, logout 403 without CSRF header and 204 with it, wrong password 401, BCrypt hash prefix only in DB
- [x] Bug found and fixed: Spring's CSRF session strategy wiped the XSRF cookie on every authenticated request. Fixed with `NullAuthenticatedSessionStrategy` in the `csrf(...)` config

## Key decisions

- IDs: UUID
- Concurrency: pessimistic locking (`PESSIMISTIC_WRITE`), accounts locked in ascending UUID order
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- Funding: SYSTEM accounts post opening deposits and are the only accounts allowed to go negative
- JWT in HttpOnly cookie (XSS cannot read it) plus CSRF token to cover the CSRF risk this introduces
- Local Postgres host port is **5455** (5432 and 5433 were taken on the dev machine)

## Known open item

- GitHub push is not working yet (authentication). Commits are local only. To be fixed before P8 (CI).

## Next

P3: Account, Transaction and LedgerEntry entities, balance from ledger sums, `GET/POST /api/accounts`.
