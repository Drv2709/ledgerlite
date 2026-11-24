# LedgerLite: Plan

A minimal double-entry payments platform: accounts, atomic transfers, reversals, idempotent retries.

## Stack (locked)
Java 21 + Spring Boot 3.3 (Spring Security 6, Spring Data JPA), PostgreSQL (Docker local, Render/Railway prod),
Flyway, JWT (jjwt) in HttpOnly cookie + BCrypt, React 18 + Vite + TypeScript (strict, no `any`),
TanStack Query + Axios + React Router, JUnit 5 + Mockito + Testcontainers, GitHub Actions.

## ERD

```
┌────────────────────┐        ┌────────────────────────┐
│ User               │ 1    * │ Account                │
│────────────────────│────────│────────────────────────│
│ id (PK, uuid)      │        │ id (PK, uuid)          │
│ email (UQ, lower)  │        │ userId (FK→User)       │  idx: userId
│ passwordHash       │        │ accountNumber (UQ)     │
│ role USER | ADMIN  │        │ name                   │
│ createdAt          │        │ currency (CHAR 3)      │
└──┬─────────────────┘        │ type USER | SYSTEM     │
   │ 1                        │ createdAt              │
   │                          └───────────┬────────────┘
   │ *                                    │ 1
┌──┴─────────────────────┐                │
│ IdempotencyKey         │                │ *
│────────────────────────│        ┌───────┴────────────────┐
│ id (PK)                │        │ LedgerEntry            │
│ userId (FK→User)       │        │────────────────────────│
│ idempotencyKey         │        │ id (PK)                │
│ requestHash (SHA-256)  │        │ transactionId (FK)     │  idx: transactionId
│ transactionId (FK, ∅)  │        │ accountId (FK)         │  idx: (accountId, createdAt)
│ responseStatus, Body   │        │ entryType DEBIT|CREDIT │
│ createdAt              │        │ amount NUMERIC(19,4)>0 │
│ UQ(userId, key)        │        │ createdAt              │
└──────────┬─────────────┘        └───────┬────────────────┘
           │ 0..1                         │ * (>= 2 per transaction)
           │                              │ 1
        ┌──┴──────────────────────────────┴──┐
        │ Transaction                        │
        │────────────────────────────────────│
        │ id (PK)                            │
        │ type TRANSFER | REVERSAL | DEPOSIT │
        │ status COMPLETED | REVERSED        │
        │ amount NUMERIC(19,4) > 0, currency │
        │ sourceAccountId (FK→Account)       │  idx: (sourceAccountId, createdAt)
        │ destinationAccountId (FK→Account)  │  idx: (destinationAccountId, createdAt)
        │ initiatedBy (FK→User)              │
        │ reversesTransactionId (FK→self, ∅) │  UQ: a transaction is reversed once
        │ description, createdAt             │
        └────────────────────────────────────┘

No balance column anywhere.
balance(account) = SUM(CREDIT) - SUM(DEBIT) over its LedgerEntry rows.
LedgerEntry is append-only (DB trigger blocks UPDATE/DELETE).
Per transaction, SUM(DEBIT) = SUM(CREDIT) (deferred DB trigger checks at commit).
```

### Transfer state machine
- `POST /api/transfers` → validate → lock both accounts (ascending UUID order) → check balance
- Balance >= amount → one `Transaction` (`COMPLETED`) + one DEBIT (source) + one CREDIT (destination) → commit
- Balance < amount → `422 INSUFFICIENT_FUNDS`, nothing written
- Nothing is ever edited or deleted in the ledger

### Reversal state machine
- `POST /api/transactions/{id}/reverse` → new `REVERSAL` transaction with opposite entries
- Original `status` becomes `REVERSED` (metadata only; its ledger entries are untouched)
- Second reversal attempt → `409 ALREADY_REVERSED` (also enforced by the UNIQUE constraint)
- Destination no longer has the money → `422 INSUFFICIENT_FUNDS`
- Only the original source account's owner may reverse

### Idempotency outcomes (`Idempotency-Key` header, per user)
| Condition | Result |
|---|---|
| Header missing | `400 VALIDATION_ERROR` |
| No stored key for (user, key) | Process transfer, store key and response, `201` |
| Stored key, same request hash | Replay stored status and body, **no money moved** |
| Stored key, different request hash | `422 IDEMPOTENCY_KEY_REUSED` |
| Two identical requests at once | Unique index makes one wait; the second replays the first's result |

### Access outcomes (IDOR)
| Condition | Result |
|---|---|
| Account or transaction belongs to me | `200` |
| Belongs to another user | `403 FORBIDDEN` |
| Does not exist | `404 NOT_FOUND` |
| Transfer source account not mine | `403 FORBIDDEN` |

## API map

| Route | Method | Auth | Purpose |
|---|---|---|---|
| `/health` | GET | public | Liveness + DB check (P1) |
| `/api/auth/register` | POST | public | Create user, set cookie |
| `/api/auth/login` | POST | public | Authenticate, set cookie |
| `/api/auth/logout` | POST | user | Clear cookie |
| `/api/auth/me` | GET | user | Current user (session restore) |
| `/api/accounts` | GET | user | My accounts with computed balances |
| `/api/accounts` | POST | user | Create account (optional initial deposit from SYSTEM) |
| `/api/accounts/{id}` | GET | owner | One account |
| `/api/transactions` | GET | user | History: `page`, `size`, `accountId`, `type`, `from`, `to` |
| `/api/transactions/{id}` | GET | participant | Transaction with its entries |
| `/api/transfers` | POST | user | Atomic transfer (`Idempotency-Key` required) |
| `/api/transactions/{id}/reverse` | POST | source owner | Reverse a transaction (`Idempotency-Key` required) |

Auth gate: everything returns `401` unless authenticated, except `/health`, `/actuator/health`, `/api/auth/register` and `/api/auth/login`.

## UI map (P6 and P7)

| Route | Auth | Purpose |
|---|---|---|
| `/login`, `/register` | public | Auth forms |
| `/` | user | Dashboard: account balances, recent transactions |
| `/transfer` | user | Transfer form (loading state, backend errors such as "Insufficient funds") |
| `/transactions` | user | History with pagination and filters |
| `*` | n/a | Not-found page; protected routes redirect to `/login` |

Every data view handles loading (skeleton), error and empty states.

## Phases
P0 ERD/contract/infra → P1 scaffold + health → P2 auth + JWT → P3 entities + ledger →
P4 transfers → P5 integration + concurrency tests → P6 React auth → P7 dashboard + history →
P8 full-stack Compose + CI → P9 deploy + README + architecture diagram

## Security baseline (every phase)
BCrypt, never log or return password hashes or JWTs; JWT in HttpOnly cookie, SameSite set, `Secure` in prod;
stateless Spring Security (no server sessions); `@Valid` / `jakarta.validation` on all input;
server-side ownership checks on every account and transaction access; `JWT_SECRET` from the environment;
CSRF token (double-submit) on every state-changing request; money is `BigDecimal` (`HALF_EVEN`) in Java,
`NUMERIC(19,4)` in Postgres and decimal strings in JSON, never `double` or `float`.
## Deployment (P9)
- **Option A:** one Render Docker web service (`Dockerfile.render`) serves the API and the built React app from the same
  origin, so the HttpOnly cookie and CSRF design work unchanged and no CORS is needed.
- **Database:** Neon PostgreSQL over the direct (non-pooled) endpoint. The Hikari pool is small and closes idle connections
  after a minute so Neon can suspend its compute.
- **Same artifact everywhere:** `docker compose --profile single` builds the exact image that is deployed, and CI
  smoke-tests it.
- Setup steps: `docs/DEPLOY.md`. Diagrams: `docs/architecture.md`. Overview and decisions: `README.md`.
