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
| P5 | Integration and concurrency tests (Testcontainers, JUnit) | Done and verified |
| P6 | React scaffold and auth integration | Done and verified |
| P7 | Dashboard and transaction history | Not started (next) |
| P8 | Full-stack Docker Compose and GitHub Actions CI | Not started |
| P8b | Admin role: read-only admin API (users, transactions) and tests | Planned (added by request) |
| P9 | Deploy, README, architecture diagram | Not started |

## P0 to P5 (done)
- [x] Planning docs, infra, V1 and V2 schema, health check
- [x] JWT in HttpOnly cookie, CSRF double-submit, auth endpoints, standard JSON errors
- [x] Accounts, immutable double-entry ledger, atomic transfers, idempotency, reversals, history
- [x] 39 JUnit tests (unit, Testcontainers integration, concurrency, IDOR) and 73 end-to-end shell checks passing

## P6: React scaffold and auth (done)
- [x] Vite 5 + React 18 + TypeScript `strict`; `npm run check:no-any` fails the build if `any` appears in `src/`
- [x] Axios client: `withCredentials`, XSRF cookie copied into `X-XSRF-TOKEN`, response interceptor handles expired sessions (401)
- [x] TanStack Query holds the session (`['auth','me']`); login, register and logout update it; other cached data is cleared on user change
- [x] Dev proxy: the browser only talks to `localhost:5173`, which forwards `/api` and `/health` to the backend, so cookies are same-origin
- [x] Login and Register pages with client-side validation, server field errors, loading and error states
- [x] `ProtectedRoute` and `PublicOnlyRoute`; post-login redirect only accepts safe in-app paths (no `//`, no backslashes)
- [x] `react-router-dom` upgraded to v7.18.5; `npm audit --omit=dev` reports 0 vulnerabilities
- [x] Verified in the browser after the upgrade: redirect to login, register and sign in, session survives refresh, `LL_TOKEN` is HttpOnly, logout, wrong-password message, 404 page, login page bounces signed-in users

## Key decisions
- IDs: UUID; money: BigDecimal scale 4 / `NUMERIC(19,4)` / decimal strings in JSON
- Concurrency: pessimistic locking, accounts locked in ascending UUID order, balance read after the lock
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- Frontend talks to the API same-origin (Vite proxy in dev). Production will do the same through a reverse proxy, so the HttpOnly cookie keeps working
- Local Postgres host port is **5455**
- Admin role: added as phase P8b (after P8, before P9). Promotion by SQL only, read-only API, ledger stays immutable.

## Known open items
- GitHub push is not working yet (authentication). Commits are local only. To be fixed before P8 (CI).
- Dev-only advisories (Vite 5 / esbuild, moderate and high) remain. They affect only the local dev server and never the production bundle. Plan: bump Vite in a separate chore before P9; keep the dev server on localhost (never use --host).
- Optional: run the three mutation checks to confirm the P5 tests fail when a rule is removed.

## Next
P7: Dashboard (accounts and balances, recent transactions), transfer form, history with pagination and filters, loading, error and empty states.
