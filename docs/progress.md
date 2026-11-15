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

# LedgerLite: Progress

Repo: https://github.com/Drv2709/ledgerlite
Last updated: 2026-11-14

## Status

| Phase | Scope                                                              | Status            |
| ----- | ------------------------------------------------------------------ | ----------------- |
| P0    | ERD, API contract, docker-compose, pom, application.yml, V1 Flyway | Done and verified |
| P1    | Spring Boot scaffold, DB connection, health check                  | Done and verified |
| P2    | Spring Security, JWT, auth endpoints, User entity                  | Done and verified |
| P3    | Account and Transaction entities, double-entry ledger logic        | Done and verified |
| P4    | Transfer endpoint (atomic, locking, idempotency)                   | In progress       |
| P5    | Integration and concurrency tests (Testcontainers, JUnit)          | Not started       |
| P6    | React scaffold and auth integration                                | Not started       |
| P7    | Dashboard and transaction history                                  | Not started       |
| P8    | Full-stack Docker Compose and GitHub Actions CI                    | Not started       |
| P9    | Deploy, README, architecture diagram                               | Not started       |

## P0 to P2 (done)

- [x] Planning docs, infra, V1 schema (append-only ledger trigger, balanced-transaction trigger), health check
- [x] JWT in HttpOnly cookie, CSRF double-submit, register / login / logout / me, standard JSON errors

## P3: Accounts and the Ledger (done)

- [x] V2 migration: `VARCHAR` currency and request_hash columns, `account_number_seq`, SYSTEM funding accounts for CAD, EUR, GBP, INR (USD from V1)
- [x] Entities: `Account` (no balance field), `Transaction`, `LedgerEntry` (`@Immutable`)
- [x] `LedgerService`: balance = SUM(credits) - SUM(debits) in one SQL statement; `post()` writes the DEBIT and CREDIT pair and must run inside a caller's transaction
- [x] `Money`: `BigDecimal`, scale 4, `HALF_EVEN`; amounts leave the API as strings
- [x] `GET/POST /api/accounts`, `GET /api/accounts/{id}` (403 for another user's account, 404 if missing)
- [x] Opening deposits post a real `DEPOSIT` transaction from the SYSTEM account
- [x] Verified: 201 / 200 / 403 / 404 / 401 / 400 cases, every transaction nets to 0.0000, SYSTEM USD at -100.0000, UPDATE and DELETE on the ledger rejected, unbalanced insert rejected at commit

## Key decisions

- IDs: UUID; money: BigDecimal scale 4 / `NUMERIC(19,4)` / decimal strings in JSON
- Concurrency: pessimistic locking (`PESSIMISTIC_WRITE`), accounts locked in ascending UUID order
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- Supported currencies: USD, CAD, EUR, GBP, INR
- Local Postgres host port is **5455**

## Known open item

- GitHub push is not working yet (authentication). Commits are local only. To be fixed before P8 (CI).

## Next

P4: `POST /api/transfers`, reversals, idempotency, transaction history endpoints.

# LedgerLite: Progress

Repo: https://github.com/Drv2709/ledgerlite
Last updated: 2026-11-15

## Status

| Phase | Scope | Status |
|---|---|---|
| P0 | ERD, API contract, docker-compose, pom, application.yml, V1 Flyway | Done and verified |
| P1 | Spring Boot scaffold, DB connection, health check | Done and verified |
| P2 | Spring Security, JWT, auth endpoints, User entity | Done and verified |
| P3 | Account and Transaction entities, double-entry ledger logic | Done and verified |
| P4 | Transfer endpoint (atomic, locking, idempotency) | Done and verified |
| P5 | Integration and concurrency tests (Testcontainers, JUnit) | In progress |
| P6 | React scaffold and auth integration | Not started |
| P7 | Dashboard and transaction history | Not started |
| P8 | Full-stack Docker Compose and GitHub Actions CI | Not started |
| P9 | Deploy, README, architecture diagram | Not started |

## P0 to P3 (done)
- [x] Planning docs, infra, V1 and V2 schema, health check
- [x] JWT in HttpOnly cookie, CSRF double-submit, auth endpoints, standard JSON errors
- [x] Accounts, immutable double-entry ledger, balance derived from ledger sums, `Money` (BigDecimal scale 4, HALF_EVEN)

## P4: Transfers, Idempotency, Reversals, History (done)
- [x] `POST /api/transfers`: one `@Transactional` unit; claims the idempotency key, validates, locks both accounts (`PESSIMISTIC_WRITE`, ascending id), checks the balance after the lock, writes the transaction and the DEBIT / CREDIT pair
- [x] Idempotency: `INSERT ... ON CONFLICT DO NOTHING` on `(user_id, key)`; replay returns the stored status and body with `Idempotent-Replayed: true`; same key with a different payload returns 422; failures are not cached
- [x] `POST /api/transactions/{id}/reverse`: new REVERSAL transaction with opposite entries, original marked REVERSED, second reversal returns 409, only the source owner can reverse, only TRANSFERs can be reversed
- [x] `GET /api/transactions` (page, size, accountId, type, from, to) and `GET /api/transactions/{id}`, scoped to the caller's accounts
- [x] Overdraft returns 422 `INSUFFICIENT_FUNDS`; cross-currency and SYSTEM-account transfers return 400
- [x] Verified with `scripts/test-transfers.sh`: 73 checks passing, including 10 parallel transfers out of a 50.00 account (exactly 5 succeed, balance ends at 0.0000) and 5 identical concurrent requests (money moves once)
- [x] Test correction: "reversing a reversal" now checks both the ownership branch (403) and the type branch (400)

## Key decisions
- IDs: UUID; money: BigDecimal scale 4 / `NUMERIC(19,4)` / decimal strings in JSON
- Concurrency: pessimistic locking, accounts locked in ascending UUID order, balance read after the lock
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- Supported currencies: USD, CAD, EUR, GBP, INR
- Local Postgres host port is **5455**

## Known open item
- GitHub push is not working yet (authentication). Commits are local only. To be fixed before P8 (CI).

## Next
P5: JUnit + Mockito unit tests, Testcontainers integration tests, multi-threaded concurrency tests, IDOR security tests.