# LedgerLite: Progress

Repo: https://github.com/Drv2709/ledgerlite
Live: https://ledgerlite-wg8w.onrender.com
Last updated: 2026-11-27

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
| P8 | Full-stack Docker Compose and GitHub Actions CI | Done and verified (local Docker stack and green GitHub Actions run) |
| P8b | Admin role: read-only admin API (users, transactions) and tests | Done and verified |
| P9 | Deploy, README, architecture diagram | Done and verified |

## P0 to P8b (done)
- [x] Double-entry ledger with database-enforced invariants, atomic idempotent transfers, reversals, history
- [x] JWT in an HttpOnly cookie with CSRF protection, ownership checks, read-only admin API
- [x] React 18 + Vite + TypeScript strict UI with loading, error and empty states
- [x] 50+ JUnit tests (unit, Testcontainers integration, concurrency, IDOR, admin, static serving), 73 end-to-end checks, 21 smoke checks
- [x] Dockerfiles, Docker Compose profiles (dev, Nginx stack, single image), GitHub Actions workflow

## P9: Deployment, README and diagrams (done)
- [x] One Docker image (`Dockerfile.render`) serves the API and the React app from the same origin; Spring serves the build with a safe SPA fallback and long-lived caching for hashed assets
- [x] Production profile with no fallback values: the app refuses to start without its database and JWT settings
- [x] Neon PostgreSQL (direct connection) and a Render Docker web service; configuration documented in `docs/DEPLOY.md`
- [x] Neon-friendly pool: small, idle connections closed after a minute, 60 second startup wait, graceful shutdown
- [x] Responsive UI checked at phone, tablet and laptop widths: sticky header, tabs on phones, transactions as cards on phones, 44px touch targets
- [x] README with design decisions, run instructions, testing and honest limitations; `docs/architecture.md` with diagrams; `docs/DEPLOY.md` with setup and troubleshooting
- [x] Verified live: `scripts/smoke-test.sh` against the deployed URL passed with no failures

## Operations notes
- Making someone an admin on the live site: run `UPDATE users SET role = 'ADMIN' WHERE email = '...';` in the Neon SQL editor, then sign out and in again. Revoke with `'USER'`. There is no API for this.

## Key decisions
- IDs: UUID; money: BigDecimal scale 4 / `NUMERIC(19,4)` / decimal strings in JSON
- Concurrency: pessimistic locking, accounts locked in ascending UUID order, balance read after the lock
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- One origin for page and API (Vite proxy in dev, Nginx in the Docker stack, single image in production), so the HttpOnly cookie keeps working
- Admin: SQL promotion only, read-only API, role re-checked in the database

## Known limitations and follow-ups
- Free hosting tiers sleep when idle; the first request after a pause can take about a minute
- Public sign-up has no rate limiting, and the demo starting balance is free money from a system account (fine for a demo, not for real use)
- Dev-only advisories (Vite 5 / esbuild) affect only the local dev server; plan a Vite upgrade
- Testcontainers 1.19 needs `api.version=1.44` on very new Docker engines (set in CI and locally)
- Not built: rate limiting, CSV export, guest login, reverse button and admin pages in the UI
