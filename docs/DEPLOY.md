# Deploying LedgerLite to Render + Neon

One Render web service runs the single image (`Dockerfile.render`: API and React app together). Neon hosts PostgreSQL.
The schema, including the seeded SYSTEM funding accounts, is created by Flyway on the first start.

Prerequisites: the code is on GitHub, and you have Neon and Render accounts.

## 1. Neon (database)
1. Create a project. Pick a region close to the Render region you will use.
2. Open the connection details and copy the **direct** connection (the host that does **not** contain `-pooler`).
   It looks like `postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require`.
3. The app takes the JDBC form plus separate credentials:
   - `DB_URL` = `jdbc:postgresql://HOST/DBNAME?sslmode=require`
   - `DB_USERNAME` = `USER`
   - `DB_PASSWORD` = `PASSWORD`

## 2. Render (web service)
1. New, Web Service, connect the GitHub repository.
2. Language / runtime: **Docker**. Dockerfile path: `./Dockerfile.render`. Build context: the repository root.
3. Region: the same as the Neon project. Instance type: Free is fine for a demo (see the notes below).
4. Health check path: `/health`.
5. Environment variables:

| Name | Value |
|---|---|
| `DB_URL` | the JDBC URL from step 1 |
| `DB_USERNAME` | the Neon user |
| `DB_PASSWORD` | the Neon password |
| `JWT_SECRET` | a long random value: `openssl rand -hex 32` (never reuse the dev default) |
| `COOKIE_SECURE` | `true` (Render serves HTTPS) |
| `COOKIE_SAMESITE` | `Lax` |
| `DB_POOL_SIZE` | optional, default `5` |

6. Create the service. The first build takes several minutes. Later pushes to the branch redeploy automatically.

## 3. First run
1. Open the service URL, register an account, create an account with a starting balance, send money.
2. In the Render logs you should see `Successfully applied 2 migrations` on the first start.
3. To make yourself an admin, run this in the Neon SQL editor, then sign out and sign in again:
   `UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';`

## 4. Verify from your machine
`bash scripts/smoke-test.sh https://YOUR-SERVICE.onrender.com`
It registers two users, moves money, checks idempotent replay and ownership, and expects `FAIL=0`.

## 5. Things to know
- **Cold starts.** A free Render instance sleeps after about 15 minutes without traffic, and the first request afterwards can
  take around a minute. Neon also suspends an idle database and wakes within a moment. A paid always-on instance removes the
  Render delay. Check both providers' current limits before relying on them.
- **Not for real money.** Free tiers are a portfolio setup. This project has no payment rails, no email verification and no
  rate limiting.
- **Secrets.** Never commit `.env`. Changing `JWT_SECRET` signs everyone out.
- **Backups.** Neon's free plan is for demos. For a copy of the data: `pg_dump "postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require" > backup.sql`.
- **Memory.** Small instances have limited RAM. If the service is killed for memory, lower `DB_POOL_SIZE` or use a larger instance.

## 6. Troubleshooting
| Symptom in the logs or browser | Likely cause |
|---|---|
| `JWT secret must be at least 32 bytes long` | `JWT_SECRET` is missing or too short |
| `password authentication failed` | wrong `DB_USERNAME` or `DB_PASSWORD` |
| `SSL` or `no pg_hba.conf entry` | `DB_URL` is missing `?sslmode=require` |
| `Unable to obtain connection` at startup, then recovers | the Neon database was waking up: the pool waits up to a minute |
| Sign-in appears to succeed but you are sent back to sign-in | `COOKIE_SECURE=true` over plain HTTP, or a stale cookie from another deployment |
| The page loads but every API call fails | wrong `DB_URL` form: it must start with `jdbc:postgresql://` |
| `Could not resolve placeholder` (for example `DB_URL` or `JWT_SECRET`) | that environment variable is missing in Render. The production profile refuses to start without it |
| `Could not resolve placeholder` (for example `DB_URL` or `JWT_SECRET`) | that environment variable is missing in Render. The production profile refuses to start without it |
