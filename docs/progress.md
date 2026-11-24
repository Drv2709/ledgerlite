# LedgerLite: Progress

Repo: https://github.com/Drv2709/ledgerlite
Last updated: 2026-11-24

## Status

| Phase | Scope | Status |
|---|---|---|
| P0 | ERD, API contract, docker-compose, pom, application.yml, V1 Flyway | Done and verified |
| P1 | Spring Boot scaffold, DB connection, health check | Done and verified |
| P2 | Spring Security, JWT, auth endpoints, User entity | Done and verified |
| P3 | Account and Transaction entities, double-entry ledger logic | Done and verified |
| P4 | Transfer endpoint (atomic, locking, idempotency) | Done and verified |
| P5 | Integration and concurrency tests (Testcontainers, JUnit) | Done and verified |
| P6 | React scaffold and auth integration | Done and verified |
| P7 | Dashboard and transaction history | Done and verified |
| P8 | Full-stack Docker Compose and GitHub Actions CI | Done locally; CI run pending (needs GitHub push) |
| P8b | Admin role: read-only admin API (users, transactions) and tests | Done and verified |
| P9 | Deploy, README, architecture diagram | Not started (next) |

## P0 to P7 (done)
- [x] Planning docs, infra, V1 and V2 schema, health check
- [x] JWT in HttpOnly cookie, CSRF double-submit, auth endpoints, standard JSON errors
- [x] Accounts, immutable double-entry ledger, atomic transfers, idempotency, reversals, history
- [x] React 18 + Vite + TypeScript strict: auth, dashboard, transfer form, history with pagination and filters, loading, error and empty states
- [x] 73 end-to-end shell checks passing (`scripts/test-transfers.sh`)

## P8: Docker and CI
- [x] Backend `Dockerfile` (multi-stage, non-root), frontend `Dockerfile` + `nginx.conf` (SPA fallback, `/api` proxy, security headers)
- [x] `docker-compose.yml`: Postgres by default; backend and frontend behind the `full` profile; `JWT_SECRET` has no default
- [x] `scripts/smoke-test.sh`: 21 end-to-end checks through Nginx, all passing locally
- [x] `.github/workflows/ci.yml`: backend tests, frontend checks, Docker stack smoke test (written, not yet run on GitHub)
- [ ] GitHub push (SSH) and first green CI run

## P8b: Admin role (done)
- [x] `GET /api/admin/users` and `GET /api/admin/transactions` (paginated; transactions filter by account, type, date range). Read-only: no write endpoints exist
- [x] Two layers of protection: the `/api/admin/**` rule needs `ROLE_ADMIN` in the filter chain, and every call re-checks the role in the database, so a demoted admin loses access immediately
- [x] Promotion is SQL only (`scripts/make-admin.sh <email>`); the role lives in the JWT, so a promoted user signs in again
- [x] Never exposed: password hashes, tokens, the SYSTEM user. Admins still get 403 on other users' normal-API accounts
- [x] 9 new integration tests (anonymous 401, user 403, promotion and demotion token cases, listing and filters, validation, ownership unchanged, no write endpoints). Full suite passes with the admin tests included
- [x] Verified manually: promotion, admin token sees users and cross-user transactions with account numbers, normal user 403, anonymous 401
- [x] `docs/API_CONTRACT.md` documents the admin API

## Key decisions
- IDs: UUID; money: BigDecimal scale 4 / `NUMERIC(19,4)` / decimal strings in JSON
- Concurrency: pessimistic locking, accounts locked in ascending UUID order, balance read after the lock
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- Frontend talks to the API same-origin (Vite proxy in dev, Nginx in Docker). The HttpOnly cookie keeps working
- Admin: SQL promotion only, read-only API, role re-checked in the database
- Local Postgres host port is **5455**
- Deployment plan: Neon (Postgres) + Render. Leaning towards one Render web service serving both API and UI (same-origin), pending confirmation

## Known open items
- GitHub push is not working yet. Commits are local only. Needed for CI.
- Dev-only advisories (Vite 5 / esbuild) remain; they affect only the local dev server. Plan: bump Vite in a separate chore before P9.
- Optional: run the mutation checks to confirm the P5 and P8b tests fail when a rule is removed.
- Reverse-transaction button and an admin page are not in the UI (the backend endpoints exist).

## Next
P9: deploy to Render + Neon, README, architecture diagram.
