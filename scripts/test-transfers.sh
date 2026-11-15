#!/usr/bin/env bash
# P4 test: transfers, idempotency, IDOR, reversals, history, concurrency, DB invariants.
# Run from the project root while the app is running on :8080.
BASE=http://localhost:8080
PASSWORD='Str0ng!Passw0rd'
SUF=$(date +%s)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
HDR="$TMP/hdr"; BODYF="$TMP/body"
PSQL="docker exec -i ledgerlite-postgres psql -U ledgerlite -d ledgerlite -t -A"
PASS=0; FAIL=0; STATUS=""; BODY=""; REPLAYED=""

xsrf()     { awk '$6=="XSRF-TOKEN"{print $7}' "$1"; }
check()    { if [ "$2" = "$3" ]; then echo "  PASS  $1"; PASS=$((PASS+1)); else echo "  FAIL  $1 (expected '$2', got '$3')"; FAIL=$((FAIL+1)); fi; }
field()    { echo "$BODY" | grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
numfield() { echo "$BODY" | grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2; }
newkey()   { echo "k-$SUF-$RANDOM$RANDOM"; }

register() {
  local jar="$TMP/$1.jar"
  curl -s -o /dev/null -c "$jar" -X POST "$BASE/api/auth/register" -H "Content-Type: application/json" \
    -d "{\"email\":\"$1$SUF@example.com\",\"password\":\"$PASSWORD\"}"
  echo "$jar"
}

# req JAR METHOD PATH [IDEMPOTENCY_KEY] [JSON_BODY]  -> sets STATUS, BODY, REPLAYED
req() {
  local jar=$1 method=$2 path=$3 key=${4-} body=${5-}
  local args=(-s -D "$HDR" -o "$BODYF" -w "%{http_code}" -b "$jar" -c "$jar" -X "$method" "$BASE$path")
  if [ "$method" = POST ]; then
    args+=(-H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(xsrf "$jar")")
  fi
  [ -n "$key" ] && args+=(-H "Idempotency-Key: $key")
  [ -n "$body" ] && args+=(-d "$body")
  STATUS=$(curl "${args[@]}")
  BODY=$(cat "$BODYF")
  REPLAYED=no
  grep -qi '^Idempotent-Replayed: true' "$HDR" && REPLAYED=yes
}

balance() { req "$1" GET "/api/accounts/$2"; field balance; }
transfer_json() { echo '{"sourceAccountId":"'"$1"'","destinationAccountNumber":"'"$2"'","amount":"'"$3"'","description":"'"${4-test}"'"}'; }

echo "== 0. Setup: users alice, bob, carol; accounts"
JA=$(register alice); JB=$(register bob); JC=$(register carol)
req "$JA" POST /api/accounts "" '{"name":"A-main","currency":"USD","initialDeposit":"100.00"}'; A1=$(field id); A1N=$(field accountNumber)
req "$JA" POST /api/accounts "" '{"name":"A-second","currency":"USD"}'; A2=$(field id); A2N=$(field accountNumber)
req "$JA" POST /api/accounts "" '{"name":"A-cad","currency":"CAD"}'; ACADN=$(field accountNumber)
req "$JB" POST /api/accounts "" '{"name":"B-main","currency":"USD"}'; B1=$(field id); B1N=$(field accountNumber)
check "A1 starts at 100.0000" "100.0000" "$(balance "$JA" "$A1")"
check "B1 starts at 0.0000" "0.0000" "$(balance "$JB" "$B1")"
echo

echo "== 1. Transfer and idempotency"
K1=$(newkey); T1=$(transfer_json "$A1" "$B1N" "25.00" "Rent")
req "$JA" POST /api/transfers "$K1" "$T1"
check "transfer returns 201" 201 "$STATUS"
check "type is TRANSFER" TRANSFER "$(field type)"
check "direction is OUT" OUT "$(field direction)"
check "amount is a decimal string" "25.0000" "$(field amount)"
check "first call is not a replay" no "$REPLAYED"
ENTRIES=$(echo "$BODY" | grep -o '"entryType"' | wc -l | tr -d ' ')
check "response carries 2 ledger entries" 2 "$ENTRIES"
TX1=$(field id); FIRST_BODY=$BODY
check "A1 is now 75.0000" "75.0000" "$(balance "$JA" "$A1")"
check "B1 is now 25.0000" "25.0000" "$(balance "$JB" "$B1")"

req "$JA" POST /api/transfers "$K1" "$T1"
check "retry with same key returns 201" 201 "$STATUS"
check "retry is marked as replay" yes "$REPLAYED"
check "retry returns the identical body" "$FIRST_BODY" "$BODY"
check "A1 still 75.0000 (money moved once)" "75.0000" "$(balance "$JA" "$A1")"

req "$JA" POST /api/transfers "$K1" "$(transfer_json "$A1" "$B1N" "30.00" "Rent")"
check "same key, different amount -> 422" 422 "$STATUS"
check "code IDEMPOTENCY_KEY_REUSED" IDEMPOTENCY_KEY_REUSED "$(field code)"

req "$JA" POST /api/transfers "" "$T1"
check "missing Idempotency-Key -> 400" 400 "$STATUS"
echo

echo "== 2. Overdraft, IDOR and validation"
req "$JA" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "$B1N" "500.00")"
check "overdraft -> 422" 422 "$STATUS"
check "code INSUFFICIENT_FUNDS" INSUFFICIENT_FUNDS "$(field code)"
check "A1 unchanged after overdraft" "75.0000" "$(balance "$JA" "$A1")"

req "$JB" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "$B1N" "1.00")"
check "bob spending from alice's account -> 403" 403 "$STATUS"

req "$JA" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "$B1N" "0")"
check "amount 0 -> 400" 400 "$STATUS"
req "$JA" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "$B1N" "1.23456")"
check "5 decimal places -> 400" 400 "$STATUS"
req "$JA" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "$B1N" "abc")"
check "non-numeric amount -> 400" 400 "$STATUS"
req "$JA" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "$A1N" "1.00")"
check "same source and destination -> 400" 400 "$STATUS"
req "$JA" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "LL-9999999999" "1.00")"
check "unknown destination -> 404" 404 "$STATUS"
req "$JA" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "$ACADN" "1.00")"
check "currency mismatch -> 400" 400 "$STATUS"
req "$JA" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "LL-SYSTEM-USD" "1.00")"
check "transfer to SYSTEM account -> 400" 400 "$STATUS"
echo

echo "== 3. History and access control"
req "$JA" GET "/api/transactions?page=0&size=1"
check "history 200" 200 "$STATUS"
check "alice has 2 transactions (deposit + transfer)" 2 "$(numfield totalElements)"
check "size=1 gives 2 pages" 2 "$(numfield totalPages)"
req "$JA" GET "/api/transactions?type=TRANSFER"
check "type=TRANSFER filter -> 1" 1 "$(numfield totalElements)"
req "$JA" GET "/api/transactions?accountId=$A1&type=TRANSFER"
check "accountId filter works" 1 "$(numfield totalElements)"
req "$JA" GET "/api/transactions?from=2099-01-01T00:00:00Z"
check "future 'from' -> 0 results" 0 "$(numfield totalElements)"
req "$JA" GET "/api/transactions?accountId=$B1"
check "filtering by another user's account -> 403" 403 "$STATUS"
req "$JA" GET "/api/transactions?size=0"
check "size=0 -> 400" 400 "$STATUS"
req "$JA" GET "/api/transactions?size=101"
check "size=101 -> 400" 400 "$STATUS"
req "$JA" GET "/api/transactions?type=BOGUS"
check "bad type -> 400" 400 "$STATUS"
req "$JC" GET "/api/transactions"
check "carol sees 0 transactions" 0 "$(numfield totalElements)"

req "$JA" GET "/api/transactions/$TX1"
check "alice reads her transfer -> 200" 200 "$STATUS"
check "detail has 2 entries" 2 "$(echo "$BODY" | grep -o '"entryType"' | wc -l | tr -d ' ')"
req "$JB" GET "/api/transactions/$TX1"
check "bob (recipient) reads it -> 200" 200 "$STATUS"
check "bob sees direction IN" IN "$(field direction)"
req "$JC" GET "/api/transactions/$TX1"
check "carol (outsider) -> 403" 403 "$STATUS"
req "$JA" GET "/api/transactions/00000000-0000-4000-8000-000000000000"
check "unknown transaction -> 404" 404 "$STATUS"
echo

echo "== 4. Reversals"
req "$JB" POST "/api/transactions/$TX1/reverse" "$(newkey)" '{}'
check "recipient cannot reverse -> 403" 403 "$STATUS"
KR=$(newkey)
req "$JA" POST "/api/transactions/$TX1/reverse" "$KR" '{"reason":"Sent by mistake"}'
check "owner reverses -> 201" 201 "$STATUS"
check "type is REVERSAL" REVERSAL "$(field type)"
REV=$(field id); REV_BODY=$BODY
check "A1 back to 100.0000" "100.0000" "$(balance "$JA" "$A1")"
check "B1 back to 0.0000" "0.0000" "$(balance "$JB" "$B1")"
req "$JA" GET "/api/transactions/$TX1"
check "original is now REVERSED" REVERSED "$(field status)"
req "$JA" POST "/api/transactions/$TX1/reverse" "$KR" '{"reason":"Sent by mistake"}'
check "reversal retry is a replay" yes "$REPLAYED"
check "reversal replay returns identical body" "$REV_BODY" "$BODY"
req "$JA" POST "/api/transactions/$TX1/reverse" "$(newkey)" '{}'
check "second reversal -> 409" 409 "$STATUS"
check "code ALREADY_REVERSED" ALREADY_REVERSED "$(field code)"
# A reversal's source account is the original RECIPIENT's account (bob's), so alice fails the
# ownership check first (403). Bob owns the source, passes ownership, and hits the type rule (400).
req "$JA" POST "/api/transactions/$REV/reverse" "$(newkey)" '{}'
check "alice cannot reverse bob's reversal (not source owner) -> 403" 403 "$STATUS"
req "$JB" POST "/api/transactions/$REV/reverse" "$(newkey)" '{}'
check "reversing a reversal (by its source owner) -> 400" 400 "$STATUS"

req "$JA" POST /api/transfers "$(newkey)" "$(transfer_json "$A1" "$B1N" "20.00")"; TX2=$(field id)
req "$JB" POST /api/transfers "$(newkey)" "$(transfer_json "$B1" "$A2N" "20.00")"
check "bob spends the money onward -> 201" 201 "$STATUS"
req "$JA" POST "/api/transactions/$TX2/reverse" "$(newkey)" '{}'
check "reversal with insufficient funds -> 422" 422 "$STATUS"
check "code INSUFFICIENT_FUNDS" INSUFFICIENT_FUNDS "$(field code)"
check "A1 is 80.0000" "80.0000" "$(balance "$JA" "$A1")"
check "A2 is 20.0000" "20.0000" "$(balance "$JA" "$A2")"
check "B1 is 0.0000" "0.0000" "$(balance "$JB" "$B1")"
echo

echo "== 5. Concurrency: 10 parallel transfers of 10.00 out of a 50.00 account"
JD=$(register dave); JE=$(register erin)
req "$JD" POST /api/accounts "" '{"name":"D","currency":"USD","initialDeposit":"50.00"}'; D1=$(field id)
req "$JE" POST /api/accounts "" '{"name":"E","currency":"USD"}'; E1=$(field id); E1N=$(field accountNumber)
XD=$(xsrf "$JD"); PAY=$(transfer_json "$D1" "$E1N" "10.00")
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "%{http_code}\n" -b "$JD" -X POST "$BASE/api/transfers" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XD" -H "Idempotency-Key: race-$SUF-$i" \
    -d "$PAY" >> "$TMP/race.out" &
done
wait
check "exactly 5 transfers succeeded" 5 "$(grep -c '^201$' "$TMP/race.out")"
check "exactly 5 were rejected (422)" 5 "$(grep -c '^422$' "$TMP/race.out")"
check "D1 ends at exactly 0.0000" "0.0000" "$(balance "$JD" "$D1")"
check "E1 received exactly 50.0000" "50.0000" "$(balance "$JE" "$E1")"
echo

echo "== 6. Concurrency: 5 identical requests (same Idempotency-Key) at once"
JF=$(register frank)
req "$JF" POST /api/accounts "" '{"name":"F","currency":"USD","initialDeposit":"100.00"}'; F1=$(field id)
XF=$(xsrf "$JF"); PAYF=$(transfer_json "$F1" "$E1N" "10.00")
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -D "$TMP/idem$i" -w "%{http_code}\n" -b "$JF" -X POST "$BASE/api/transfers" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XF" -H "Idempotency-Key: same-$SUF" \
    -d "$PAYF" >> "$TMP/idem.out" &
done
wait
check "all 5 return 201" 5 "$(grep -c '^201$' "$TMP/idem.out")"
check "exactly 4 are replays" 4 "$(grep -il '^Idempotent-Replayed: true' "$TMP"/idem? | wc -l | tr -d ' ')"
check "F1 debited once: 90.0000" "90.0000" "$(balance "$JF" "$F1")"
check "E1 is 60.0000" "60.0000" "$(balance "$JE" "$E1")"
echo

echo "== 7. Database invariants"
check "every transaction nets to zero" 0 "$($PSQL -c "SELECT count(*) FROM (SELECT 1 FROM ledger_entries GROUP BY transaction_id HAVING SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE -amount END) <> 0) x;")"
check "no USER account is below zero" 0 "$($PSQL -c "SELECT count(*) FROM (SELECT a.id FROM accounts a JOIN ledger_entries e ON e.account_id=a.id WHERE a.type='USER' GROUP BY a.id HAVING SUM(CASE WHEN e.entry_type='CREDIT' THEN e.amount ELSE -e.amount END) < 0) x;")"
echo

echo "RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]