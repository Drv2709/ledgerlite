#!/usr/bin/env bash
# End-to-end smoke test against a running full stack, going through Nginx like a real browser would.
# Usage: bash scripts/smoke-test.sh [base-url]        (default http://localhost:8081)
set -uo pipefail
BASE=${1:-http://localhost:8081}
PASSWORD='Str0ng!Passw0rd'
SUF="$(date +%s)$RANDOM"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
HDR="$TMP/hdr"; BODYF="$TMP/body"
PASS=0; FAIL=0; STATUS=""; BODY=""; REPLAYED=""

xsrf()  { [ -f "$1" ] && awk '$6=="XSRF-TOKEN"{print $7}' "$1"; }
check() { if [ "$2" = "$3" ]; then echo "  PASS  $1"; PASS=$((PASS+1)); else echo "  FAIL  $1 (expected '$2', got '$3')"; FAIL=$((FAIL+1)); fi; }
field() { echo "$BODY" | grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
contains() { case "$2" in *"$3"*) check "$1" yes yes ;; *) check "$1" yes no ;; esac; }

# req JAR METHOD PATH [IDEMPOTENCY_KEY] [JSON_BODY]
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

echo "Smoke test against $BASE"
JA="$TMP/a.jar"; JB="$TMP/b.jar"; JANON="$TMP/anon.jar"

echo "== Static site and proxy"
req "$JANON" GET /health
check "GET /health returns 200" 200 "$STATUS"
contains "/health reports UP" "$BODY" '"status":"UP"'
req "$JANON" GET /
check "GET / returns 200" 200 "$STATUS"
contains "index.html contains the React root" "$BODY" 'id="root"'
req "$JANON" GET /transactions
check "SPA route /transactions falls back to index.html" 200 "$STATUS"
contains "fallback serves the React root" "$BODY" 'id="root"'
check "security header X-Content-Type-Options is set" 1 "$(curl -sI "$BASE/" | grep -ci '^X-Content-Type-Options: nosniff')"
req "$JANON" GET /api/accounts
check "anonymous GET /api/accounts is 401" 401 "$STATUS"

echo "== Register and accounts"
req "$JA" POST /api/auth/register "" "{\"email\":\"smoke-a-$SUF@example.com\",\"password\":\"$PASSWORD\"}"
check "register user A" 201 "$STATUS"
req "$JB" POST /api/auth/register "" "{\"email\":\"smoke-b-$SUF@example.com\",\"password\":\"$PASSWORD\"}"
check "register user B" 201 "$STATUS"
req "$JA" POST /api/accounts "" '{"name":"A","currency":"USD","initialDeposit":"100.00"}'
check "A creates an account with a deposit" 201 "$STATUS"
A1=$(field id)
check "A starts with 100.0000" "100.0000" "$(field balance)"
req "$JB" POST /api/accounts "" '{"name":"B","currency":"USD"}'
check "B creates an account" 201 "$STATUS"
B1=$(field id); BN=$(field accountNumber)

echo "== Transfer, idempotency, ownership"
KEY="smoke-$SUF"
PAYLOAD="{\"sourceAccountId\":\"$A1\",\"destinationAccountNumber\":\"$BN\",\"amount\":\"25.00\"}"
req "$JA" POST /api/transfers "$KEY" "$PAYLOAD"
check "transfer returns 201" 201 "$STATUS"
FIRST=$BODY
req "$JA" POST /api/transfers "$KEY" "$PAYLOAD"
check "retry with the same key returns 201" 201 "$STATUS"
check "retry is marked as a replay" yes "$REPLAYED"
check "retry returns the identical body" "$FIRST" "$BODY"
req "$JA" GET "/api/accounts/$A1"
check "A is debited exactly once (75.0000)" "75.0000" "$(field balance)"
req "$JB" GET "/api/accounts/$B1"
check "B is credited exactly once (25.0000)" "25.0000" "$(field balance)"
req "$JB" GET "/api/accounts/$A1"
check "B cannot read A's account (403)" 403 "$STATUS"
check "logout without the CSRF header is 403" 403 "$(curl -s -o /dev/null -w '%{http_code}' -b "$JA" -X POST "$BASE/api/auth/logout")"

echo
echo "RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
