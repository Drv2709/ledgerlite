-- LedgerLite initial schema
-- Money: NUMERIC(19,4). IDs: UUID. No balance column anywhere.

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email))
);

CREATE TABLE accounts (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users (id),
    account_number VARCHAR(20)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    currency       CHAR(3)      NOT NULL DEFAULT 'USD',
    type           VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_accounts_number UNIQUE (account_number),
    CONSTRAINT ck_accounts_type CHECK (type IN ('USER', 'SYSTEM'))
);
CREATE INDEX idx_accounts_user ON accounts (user_id);

CREATE TABLE transactions (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    type                    VARCHAR(20)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    amount                  NUMERIC(19,4) NOT NULL,
    currency                CHAR(3)       NOT NULL,
    source_account_id       UUID          NOT NULL REFERENCES accounts (id),
    destination_account_id  UUID          NOT NULL REFERENCES accounts (id),
    initiated_by            UUID          NOT NULL REFERENCES users (id),
    reverses_transaction_id UUID          REFERENCES transactions (id),
    description             VARCHAR(255),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_tx_type   CHECK (type IN ('TRANSFER', 'REVERSAL', 'DEPOSIT')),
    CONSTRAINT ck_tx_status CHECK (status IN ('COMPLETED', 'REVERSED')),
    CONSTRAINT ck_tx_amount CHECK (amount > 0),
    CONSTRAINT ck_tx_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT uq_tx_reverses UNIQUE (reverses_transaction_id)   -- a transaction can be reversed once
);
CREATE INDEX idx_tx_source_created ON transactions (source_account_id, created_at DESC);
CREATE INDEX idx_tx_dest_created   ON transactions (destination_account_id, created_at DESC);
CREATE INDEX idx_tx_created        ON transactions (created_at DESC);

CREATE TABLE ledger_entries (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID          NOT NULL REFERENCES transactions (id),
    account_id     UUID          NOT NULL REFERENCES accounts (id),
    entry_type     VARCHAR(10)   NOT NULL,
    amount         NUMERIC(19,4) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_le_type   CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_le_amount CHECK (amount > 0)
);
CREATE INDEX idx_le_account_created ON ledger_entries (account_id, created_at);
CREATE INDEX idx_le_transaction     ON ledger_entries (transaction_id);

CREATE TABLE idempotency_keys (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id),
    idempotency_key VARCHAR(64)  NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    transaction_id  UUID         REFERENCES transactions (id),
    response_status INTEGER,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idem_user_key UNIQUE (user_id, idempotency_key)
);

-- ---------------------------------------------------------------
-- Ledger immutability: entries can never be updated or deleted.
-- (TRUNCATE is not blocked by row triggers; tests may use it.)
-- ---------------------------------------------------------------
CREATE FUNCTION forbid_ledger_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only (% not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_mutation();

-- ---------------------------------------------------------------
-- Double-entry invariant: per transaction, SUM(DEBIT) = SUM(CREDIT).
-- Deferred, so it is checked at COMMIT after all entries are inserted.
-- ---------------------------------------------------------------
CREATE FUNCTION check_transaction_balanced() RETURNS trigger AS $$
DECLARE
    total_debit  NUMERIC;
    total_credit NUMERIC;
BEGIN
    SELECT COALESCE(SUM(amount) FILTER (WHERE entry_type = 'DEBIT'), 0),
           COALESCE(SUM(amount) FILTER (WHERE entry_type = 'CREDIT'), 0)
      INTO total_debit, total_credit
      FROM ledger_entries
     WHERE transaction_id = NEW.transaction_id;

    IF total_debit <> total_credit THEN
        RAISE EXCEPTION 'Unbalanced transaction %: debits=% credits=%',
            NEW.transaction_id, total_debit, total_credit;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_balanced
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_transaction_balanced();

-- ---------------------------------------------------------------
-- Seed the SYSTEM user + funding account (used for opening deposits).
-- password_hash is a non-BCrypt placeholder, so nobody can log in as SYSTEM.
-- ---------------------------------------------------------------
INSERT INTO users (id, email, password_hash, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'system@ledgerlite.internal', '!', 'ADMIN');

INSERT INTO accounts (id, user_id, account_number, name, currency, type)
VALUES ('00000000-0000-0000-0000-0000000000a1',
        '00000000-0000-0000-0000-000000000001',
        'LL-SYSTEM-USD', 'System Funding (USD)', 'USD', 'SYSTEM');
