# LedgerLite API Contract (v1)

Base path: `/api`. All bodies are JSON. All money amounts are **decimal strings**.
Auth: JWT in an **HttpOnly, Secure, SameSite cookie** named `LL_TOKEN`. Public endpoints are `/health`, `/api/auth/register` and `/api/auth/login`; everything else requires auth.

## Error format (RFC 7807-style)
```json
{
  "type": "about:blank",
  "title": "Insufficient funds",
  "status": 422,
  "code": "INSUFFICIENT_FUNDS",
  "detail": "Source account balance is lower than the transfer amount.",
  "timestamp": "2026-11-13T18:00:00Z",
  "fieldErrors": [{ "field": "amount", "message": "must be greater than 0" }]
}
```
`fieldErrors` appears only on validation errors (400).

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bad input, currency mismatch, same source and destination |
| 401 | `UNAUTHENTICATED` | Missing, invalid or expired token |
| 403 | `FORBIDDEN` | Resource belongs to another user |
| 404 | `NOT_FOUND` | Resource does not exist |
| 409 | `EMAIL_TAKEN` / `ALREADY_REVERSED` | Conflicts |
| 422 | `INSUFFICIENT_FUNDS` / `IDEMPOTENCY_KEY_REUSED` | Business rule violations |

## Health
### GET /health  (public)
`200` `{ "status": "UP" }`

## Auth
### POST /api/auth/register  (public)
Request:
```json
{ "email": "ana@example.com", "password": "Str0ng!Passw0rd" }
```
Rules: valid email, password 10 to 72 characters.
`201`:
```json
{ "id": "7b0f1c1e-...", "email": "ana@example.com", "role": "USER" }
```
Sets the `LL_TOKEN` cookie. The password hash and token are never in the body.

### POST /api/auth/login  (public)
Request: same shape as register. `200`: same body as register's `201`, plus a fresh cookie. Bad credentials return `401` with a generic message ("Invalid email or password").

### POST /api/auth/logout
`204`. Clears the cookie.

### GET /api/auth/me
`200` `{ "id": "...", "email": "...", "role": "USER" }`. Used by the frontend to restore a session, since JS cannot read the HttpOnly cookie.

## Accounts
### GET /api/accounts
`200`:
```json
[
  {
    "id": "a1b2...", "accountNumber": "LL-0000000001", "name": "Everyday",
    "currency": "USD", "balance": "250.0000", "createdAt": "2026-11-13T18:00:00Z"
  }
]
```
Only the caller's accounts. `balance` is computed from ledger entries.

### POST /api/accounts
Request:
```json
{ "name": "Everyday", "currency": "USD", "initialDeposit": "100.00" }
```
`name`: 1 to 100 characters. `currency`: 3-letter ISO code. `initialDeposit`: optional, zero or more, max 1,000,000 (demo funding, posted as a `DEPOSIT` transaction from the SYSTEM account).
`201`: the account object above.

### GET /api/accounts/{accountId}
`200`: the account object. `403` if it is not yours.

## Transactions
### GET /api/transactions
Query params (all optional):

| Param | Default | Notes |
|---|---|---|
| `page` | 0 | zero-based |
| `size` | 10 | max 100 |
| `accountId` | all my accounts | must be mine, else 403 |
| `type` | any | `TRANSFER` / `REVERSAL` / `DEPOSIT` |
| `from`, `to` | none | ISO-8601 instants, inclusive |

Sorted by `createdAt` descending. `200`:
```json
{
  "content": [
    {
      "id": "t1...", "type": "TRANSFER", "status": "COMPLETED",
      "amount": "10.0000", "currency": "USD",
      "sourceAccountId": "a1b2...", "destinationAccountId": "c3d4...",
      "direction": "OUT",
      "reversesTransactionId": null, "description": "Rent",
      "createdAt": "2026-11-13T18:05:00Z"
    }
  ],
  "page": 0, "size": 10, "totalElements": 1, "totalPages": 1
}
```
`direction` is `IN` or `OUT` from the caller's perspective. Only transactions touching the caller's accounts are returned.

### GET /api/transactions/{transactionId}
`200`: the transaction object plus an `entries` array (`accountId`, `entryType`, `amount`). `403` if it does not touch your accounts.

## Transfers
### POST /api/transfers
Headers: `Idempotency-Key: <uuid>` (**required**, 400 if missing).
Request:
```json
{
  "sourceAccountId": "a1b2...",
  "destinationAccountNumber": "LL-0000000002",
  "amount": "10.00",
  "description": "Rent"
}
```
Rules: `amount` greater than 0 with at most 4 decimals; source must be owned by the caller; source and destination must differ; currencies must match.
`201`: the transaction object (with `entries`). A retry with the same key and the same body returns the **same status and body** and moves no money. Errors: `403`, `404`, `422 INSUFFICIENT_FUNDS`, `422 IDEMPOTENCY_KEY_REUSED`.

### POST /api/transactions/{transactionId}/reverse
Headers: `Idempotency-Key` (required). Body: `{ "reason": "Sent to wrong account" }` (optional).
Creates a `REVERSAL` transaction with opposite entries. Only the original source account's owner may reverse. `201`: the reversal transaction. `409 ALREADY_REVERSED` if it was already reversed. `422 INSUFFICIENT_FUNDS` if the destination no longer has the money.

## Security notes
- Cookie auth needs CSRF protection: `SameSite` plus a CSRF token header (double-submit pattern), implemented in P2.
- Passwords, hashes and tokens are never logged or returned.

## Implementation notes (P4)
- Replayed idempotent responses carry the header `Idempotent-Replayed: true` and the exact original status and body.
- Failed requests are not cached: a retry after an error (for example INSUFFICIENT_FUNDS) is evaluated again.
- Idempotency-Key format: 1 to 64 characters from letters, digits, `.`, `_`, `-`. Keys are scoped per user.
- Transfers to or from SYSTEM accounts are rejected (400). Cross-currency transfers are rejected (400).
- Supported currencies: USD, CAD, EUR, GBP, INR. Amounts accept at most 4 decimal places (more is rejected, never rounded).
- `direction` is relative to the `accountId` filter when given, otherwise to all of the caller's accounts (OUT if the money left one of them).
- Only a TRANSFER can be reversed (400 otherwise). Reversals of an already reversed transaction return 409.
- Invalid path or query values (bad UUID, enum or date) return 400 VALIDATION_ERROR.
