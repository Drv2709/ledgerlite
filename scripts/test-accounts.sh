#!/usr/bin/env bash
# P3 smoke test. Run from the project root while the app is running on :8080.
BASE=http://localhost:8080
PASSWORD='Str0ng!Passw0rd'
SUF=$(date +%s)
JA=$(mktemp); JB=$(mktemp)
trap 'rm -f "$JA" "$JB"' EXIT
PSQL="docker exec -i ledgerlite-postgres psql -U ledgerlite -d ledgerlite"

xsrf()     { awk '$6=="XSRF-TOKEN"{print $7}' "$1"; }
register() { curl -s -o /dev/null -w "%{http_code}" -c "$1" -X POST "$BASE/api/auth/register" \
               -H "Content-Type: application/json" -d "{\"email\":\"$2\",\"password\":\"$PASSWORD\"}"; }
post()     { curl -s -w "  [HTTP %{http_code}]\n" -b "$1" -c "$1" -X POST "$BASE$2" \
               -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(xsrf "$1")" -d "$3"; }
get()      { curl -s -w "  [HTTP %{http_code}]\n" -b "$1" -c "$1" "$BASE$2"; }
idof()     { echo "$1" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4; }

echo "== 0. Register users A and B (expect 201 201)"
echo "A: $(register "$JA" "alice$SUF@example.com")  B: $(register "$JB" "bob$SUF@example.com")"
echo

echo "== 1. A creates USD account with 100.00 deposit (expect 201, balance \"100.0000\")"
OUT=$(post "$JA" /api/accounts '{"name":"Everyday","currency":"USD","initialDeposit":"100.00"}')
echo "$OUT"; A1=$(idof "$OUT")
echo

echo "== 2. A creates CAD account, no deposit (expect 201, balance \"0.0000\")"
post "$JA" /api/accounts '{"name":"Savings","currency":"cad"}'
echo

echo "== 3. A lists accounts (expect 2 accounts)"
get "$JA" /api/accounts
echo

echo "== 4. Access control"
echo -n "A reads own account (expect 200):        "; get "$JA" "/api/accounts/$A1" | tail -c 20
echo -n "B reads A's account (expect 403):        "; get "$JB" "/api/accounts/$A1"
echo -n "A reads unknown id (expect 404):         "; get "$JA" "/api/accounts/00000000-0000-4000-8000-000000000000"
echo -n "B lists accounts (expect [] only):       "; get "$JB" /api/accounts
echo -n "Not logged in (expect 401):              "; curl -s -o /dev/null -w "[HTTP %{http_code}]\n" "$BASE/api/accounts"
echo -n "POST without CSRF header (expect 403):   "; curl -s -o /dev/null -w "[HTTP %{http_code}]\n" -b "$JA" -X POST "$BASE/api/accounts" -H "Content-Type: application/json" -d '{"name":"x","currency":"USD"}'
echo

echo "== 5. Validation (all expect 400)"
echo -n "bad currency:        "; post "$JA" /api/accounts '{"name":"X","currency":"XXX"}' | tail -c 120
echo -n "negative deposit:    "; post "$JA" /api/accounts '{"name":"X","currency":"USD","initialDeposit":"-5"}' | tail -c 120
echo -n "5 decimal places:    "; post "$JA" /api/accounts '{"name":"X","currency":"USD","initialDeposit":"1.23456"}' | tail -c 120
echo -n "missing name:        "; post "$JA" /api/accounts '{"currency":"USD"}' | tail -c 120
echo

echo "== 6. Ledger integrity in Postgres"
echo "-- every transaction nets to zero (expect net = 0.0000 on every row):"
$PSQL -c "SELECT transaction_id, SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE -amount END) AS net FROM ledger_entries GROUP BY transaction_id;"
echo "-- SYSTEM USD account is negative by what it funded (expect a negative balance):"
$PSQL -c "SELECT a.account_number, COALESCE(SUM(CASE WHEN e.entry_type='CREDIT' THEN e.amount ELSE -e.amount END),0) AS balance FROM accounts a LEFT JOIN ledger_entries e ON e.account_id=a.id WHERE a.type='SYSTEM' GROUP BY a.account_number ORDER BY 1;"
echo "-- UPDATE on ledger_entries (expect ERROR: ledger_entries is append-only):"
$PSQL -c "UPDATE ledger_entries SET amount = 1;" 2>&1 | head -3
echo "-- DELETE on ledger_entries (expect the same ERROR):"
$PSQL -c "DELETE FROM ledger_entries;" 2>&1 | head -3
echo "-- UNBALANCED insert (expect ERROR: Unbalanced transaction ... at commit):"
$PSQL -c "INSERT INTO transactions (id,type,amount,currency,source_account_id,destination_account_id,initiated_by) VALUES ('11111111-1111-1111-1111-111111111111','TRANSFER',5,'USD','00000000-0000-0000-0000-0000000000a1',(SELECT id FROM accounts WHERE type='USER' LIMIT 1),'00000000-0000-0000-0000-000000000001'); INSERT INTO ledger_entries (transaction_id,account_id,entry_type,amount) VALUES ('11111111-1111-1111-1111-111111111111','00000000-0000-0000-0000-0000000000a1','DEBIT',5);" 2>&1 | head -3
echo "-- the failed insert left nothing behind (expect 0 rows):"
$PSQL -t -c "SELECT count(*) FROM transactions WHERE id='11111111-1111-1111-1111-111111111111';"
