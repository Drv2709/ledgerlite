-- Hibernate schema validation maps String to VARCHAR; CHAR(n) columns would fail validation.
ALTER TABLE accounts         ALTER COLUMN currency     TYPE VARCHAR(3);
ALTER TABLE accounts         ALTER COLUMN currency     SET DEFAULT 'USD';
ALTER TABLE transactions     ALTER COLUMN currency     TYPE VARCHAR(3);
ALTER TABLE idempotency_keys ALTER COLUMN request_hash TYPE VARCHAR(64);

-- Human-friendly account numbers: LL-0000000001, LL-0000000002, ...
CREATE SEQUENCE account_number_seq START WITH 1 INCREMENT BY 1;

-- SYSTEM funding accounts for the other supported currencies (USD was seeded in V1).
INSERT INTO accounts (id, user_id, account_number, name, currency, type) VALUES
  ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-000000000001', 'LL-SYSTEM-CAD', 'System Funding (CAD)', 'CAD', 'SYSTEM'),
  ('00000000-0000-0000-0000-0000000000a3', '00000000-0000-0000-0000-000000000001', 'LL-SYSTEM-EUR', 'System Funding (EUR)', 'EUR', 'SYSTEM'),
  ('00000000-0000-0000-0000-0000000000a4', '00000000-0000-0000-0000-000000000001', 'LL-SYSTEM-GBP', 'System Funding (GBP)', 'GBP', 'SYSTEM'),
  ('00000000-0000-0000-0000-0000000000a5', '00000000-0000-0000-0000-000000000001', 'LL-SYSTEM-INR', 'System Funding (INR)', 'INR', 'SYSTEM');
