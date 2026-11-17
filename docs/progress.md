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
| P8 | Full-stack Docker Compose and GitHub Actions CI | In progress |
| P8b | Admin role: read-only admin API (users, transactions) and tests | Planned (added by request) |
| P9 | Deploy, README, architecture diagram | Not started |

## P0 to P5 (done)
- [x] Planning docs, infra, V1 and V2 schema, health check
- [x] JWT in HttpOnly cookie, CSRF double-submit, auth endpoints, standard JSON errors
- [x] Accounts, immutable double-entry ledger, atomic transfers, idempotency, reversals, history
- [x] 39 JUnit tests (unit, Testcontainers integration, concurrency, IDOR) and 73 end-to-end shell checks passing

## P6: React scaffold and auth (done)
- [x] Vite 5 + React 18 + TypeScript `strict`; `npm run check:no-any` fails the build if `any` appears in `src/`
- [x] Axios client with cookie auth and CSRF header; TanStack Query holds the session; protected and public-only routes
- [x] `react-router-dom` upgraded to v7.18.5; `npm audit --omit=dev` reports 0 vulnerabilities

## P7: Dashboard, transfers and history (done)
- [x] Dashboard: one card per account with a large formatted balance, "New account" form (optional starting balance), 5 most recent transactions
- [x] Send money page: source account select, destination account number, amount, note; backend errors shown inline (insufficient funds, unknown account)
- [x] Idempotency-Key generated once per intent: reused when retrying the same request, rotated when any field changes or after success
- [x] History page: filters (account, type, from and to dates), server-side pagination (10, 25 or 50 per page), previous page stays visible while the next loads
- [x] Loading skeletons, error panels with Retry, empty states (no accounts, no transactions, no filter matches), error boundary per page
- [x] Money is handled as decimal strings with BigInt checks; no parseFloat, no toFixed, no floats
- [x] `scripts/seed-ui-demo.sh` creates demo users with 28 transactions for UI testing
- [x] Verified: typecheck, no-any check and production build pass; every browser check passed (dashboard, pagination, filters, transfer errors and success, skeleton, error and empty states)

## Key decisions
- IDs: UUID; money: BigDecimal scale 4 / `NUMERIC(19,4)` / decimal strings in JSON
- Concurrency: pessimistic locking, accounts locked in ascending UUID order, balance read after the lock
- Reversals: new `REVERSAL` transaction, never edit or delete ledger rows
- Frontend talks to the API same-origin (Vite proxy in dev, Nginx in Docker). The HttpOnly cookie keeps working
- Local Postgres host port is **5455**
- Admin role: added as phase P8b (after P8, before P9). Promotion by SQL only, read-only API, ledger stays immutable.

## Known open items
- GitHub push is not working yet (authentication). Commits are local only. Must be fixed for CI (P8).
- Dev-only advisories (Vite 5 / esbuild) remain. They affect only the local dev server and never the production bundle. Plan: bump Vite in a separate chore before P9; keep the dev server on localhost (never use --host).
- Optional: run the three mutation checks to confirm the P5 tests fail when a rule is removed.
- Reverse-transaction button is not in the UI (the backend endpoint exists).

## Next
P8: backend and frontend Dockerfiles, full-stack Docker Compose, GitHub Actions CI.
