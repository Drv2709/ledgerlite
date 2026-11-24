# LedgerLite: Progress

Repo: https://github.com/Drv2709/ledgerlite
Last updated: 2026-11-17

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
| P8b | Admin role: read-only admin API (users, transactions) and tests | In progress |
| P9 | Deploy, README, architecture diagram | Not started |

## P0 to P7 (done)
- [x] Planning docs, infra, V1 and V2 schema, health check
- [x] JWT in HttpOnly cookie, CSRF double-submit, auth endpoints, standard JSON errors
- [x] Accounts, immutable double-entry ledger, atomic transfers, idempotency, reversals, history
- [x] 39 JUnit tests (unit, Testcontainers integration, concurrency, IDOR) and 73 end-to-end shell checks passing
- [x] React 18 + Vite + TypeScript strict: auth, dashboard, transfer form, history with pagination and filters, loading, error and empty states

## P8: Docker and CI
- [x] Backend `Dockerfile`: multi-stage (Maven + JDK 21, then JRE 21 Alpine), non-root user
- [x] Frontend `Dockerfile` + `nginx.conf`: Vite build served by Nginx, proxies `/api` and `/health` to the backend (same-origin cookies), SPA fallback, security headers
- [x] `docker-compose.yml`: Postgres by default; backend and frontend behind the `full` profile (`docker compose --profile full up -d --build --wait`, app on http://localhost:8081). `JWT_SECRET` has no default, so the backend refuses to start without one
- [x] `scripts/smoke-test.sh`: 21 end-to-end checks through Nginx (static site, SPA fallback, auth, accounts, transfer, idempotent replay, ownership, CSRF)
- [x] Verified locally: all three containers healthy, smoke test 21 of 21 passing
- [x] `.github/workflows/ci.yml`: backend tests, frontend checks, Docker stack smoke test (written, not yet run on GitHub)
- [ ] GitHub push (SSH) and first green CI run

## Key decisions
- IDs: UUID; money: BigDecimal scale 4 / `NUMERIC(19,4)` / decimal strings in JSON
- Concurrency: pessimistic locking, accounts locked in ascending UUID order, balance read after the lock
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- Frontend talks to the API same-origin (Vite proxy in dev, Nginx in Docker). The HttpOnly cookie keeps working
- Local Postgres host port is **5455**
- Admin role: added as phase P8b (after P8, before P9). Promotion by SQL only, read-only API, ledger stays immutable.
- Deployment plan: Neon (Postgres) + Render. Leaning towards one Render web service serving both API and UI (same-origin), pending confirmation

## Known open items
- GitHub push is not working yet. Commits are local only. Needed for CI.
- Dev-only advisories (Vite 5 / esbuild) remain; they affect only the local dev server. Plan: bump Vite in a separate chore before P9.
- Optional: run the three mutation checks to confirm the P5 tests fail when a rule is removed.
- Reverse-transaction button is not in the UI (the backend endpoint exists).

## Next
P8b: admin role (read-only admin API, tests). Then P9: deploy, README, architecture diagram.
