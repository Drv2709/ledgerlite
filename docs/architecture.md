# LedgerLite: Architecture

## 1. Deployed system
One Docker image serves the React app and the API from the same origin, so the HttpOnly auth cookie never crosses sites
and no CORS configuration is needed. PostgreSQL is a managed Neon database.

```mermaid
flowchart LR
    Browser["Browser<br/>React 18 SPA"]
    subgraph Render["Render web service (one Docker image)"]
        Spring["Spring Boot 3<br/>REST API + built React app"]
    end
    Neon[("Neon PostgreSQL")]
    Browser -- "HTTPS: pages and /api/*" --> Spring
    Spring -- "JDBC over TLS" --> Neon
```

## 2. The three local layouts
```mermaid
flowchart LR
    subgraph Dev["Development"]
        Vite["Vite dev server :5173"] -- "proxy /api" --> Boot["Spring Boot :8080"]
        Boot --> PG1[("Postgres :5455")]
    end
    subgraph Full["docker compose --profile full"]
        Nginx["Nginx :8081<br/>React build"] -- "proxy /api" --> Backend["Spring Boot"]
        Backend --> PG2[("Postgres")]
    end
    subgraph Single["docker compose --profile single"]
        App["Spring Boot :8082<br/>API + React build"] --> PG3[("Postgres")]
    end
```

## 3. Authentication and CSRF
The JWT lives in an HttpOnly cookie, so JavaScript (and therefore any injected script) cannot read it. Because browsers
attach cookies automatically, state-changing requests also need the CSRF token, which the SPA reads from a second,
readable cookie and echoes in a header.

```mermaid
sequenceDiagram
    participant B as Browser
    participant A as API
    B->>A: POST /api/auth/login with email and password
    A-->>B: 200, Set-Cookie LL_TOKEN (HttpOnly) and XSRF-TOKEN (readable)
    B->>A: POST /api/transfers with the cookies, X-XSRF-TOKEN header and Idempotency-Key
    A->>A: verify JWT, verify CSRF token, check account ownership
    A-->>B: 201 Created
```

## 4. A transfer, step by step
Everything below happens in one database transaction.

```mermaid
sequenceDiagram
    participant C as Client
    participant S as TransferService
    participant D as PostgreSQL
    C->>S: POST /api/transfers with Idempotency-Key
    S->>D: BEGIN
    S->>D: INSERT idempotency key ON CONFLICT DO NOTHING
    alt key already used
        D-->>S: 0 rows inserted
        S-->>C: stored response replayed, no money moved
    else new key
        S->>D: SELECT both accounts FOR UPDATE, in id order
        S->>D: read balance (credits minus debits)
        alt balance too low
            S-->>C: 422 INSUFFICIENT_FUNDS, transaction rolled back
        else enough funds
            S->>D: INSERT transaction, DEBIT entry and CREDIT entry
            S->>D: store the response on the key row
            S->>D: COMMIT (deferred trigger checks debits equal credits)
            S-->>C: 201 Created
        end
    end
```

## 5. Data model
No table has a balance column. A balance is always `SUM(credits) - SUM(debits)` over immutable ledger entries.

```mermaid
erDiagram
    USERS ||--o{ ACCOUNTS : owns
    ACCOUNTS ||--o{ LEDGER_ENTRIES : "has"
    TRANSACTIONS ||--|{ LEDGER_ENTRIES : "consists of (2 or more)"
    TRANSACTIONS ||--o| TRANSACTIONS : "reversed by"
    USERS ||--o{ IDEMPOTENCY_KEYS : "scopes"
    TRANSACTIONS ||--o| IDEMPOTENCY_KEYS : "result of"

    USERS {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar role "USER or ADMIN"
        timestamptz created_at
    }
    ACCOUNTS {
        uuid id PK
        uuid user_id FK
        varchar account_number UK
        varchar name
        varchar currency
        varchar type "USER or SYSTEM"
    }
    TRANSACTIONS {
        uuid id PK
        varchar type "TRANSFER, REVERSAL or DEPOSIT"
        varchar status "COMPLETED or REVERSED"
        numeric amount
        uuid source_account_id FK
        uuid destination_account_id FK
        uuid reverses_transaction_id FK
    }
    LEDGER_ENTRIES {
        uuid id PK
        uuid transaction_id FK
        uuid account_id FK
        varchar entry_type "DEBIT or CREDIT"
        numeric amount
    }
    IDEMPOTENCY_KEYS {
        uuid id PK
        uuid user_id FK
        varchar idempotency_key
        varchar request_hash
        text response_body
    }
```

## 6. Database-enforced invariants
- `ledger_entries` is append-only: a trigger rejects UPDATE and DELETE.
- For every transaction, total DEBIT equals total CREDIT: a deferred constraint trigger checks it at commit.
- A transaction can be reversed once: a UNIQUE constraint on `reverses_transaction_id`.
- A key can be used once per user: a UNIQUE constraint on `(user_id, idempotency_key)`.
