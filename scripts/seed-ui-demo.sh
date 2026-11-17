#!/usr/bin/env bash
# Creates two fresh demo users, three accounts and 26 transactions so the UI has data to page through.
# Run from anywhere while the backend is running on :8080.
set -euo pipefail
BASE=http://localhost:8080
PASSWORD='Str0ng!Passw0rd'
SUF=$(date +%s)
JA=$(mktemp); JB=$(mktemp)
trap 'rm -f "$JA" "$JB"' EXIT

xsrf()  { awk '$6=="XSRF-TOKEN"{print $7}' "$1"; }
reg()   { curl -s -o /dev/null -c "$1" -X POST "$BASE/api/auth/register" -H "Content-Type: application/json" \
            -d "{\"email\":\"$2\",\"password\":\"$PASSWORD\"}"; }
post()  { curl -s -b "$1" -c "$1" -X POST "$BASE$2" -H "Content-Type: application/json" \
            -H "X-XSRF-TOKEN: $(xsrf "$1")" ${4:+-H "Idempotency-Key: $4"} -d "$3"; }
field() { grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

A="demo$SUF@example.com"; B="partner$SUF@example.com"
reg "$JA" "$A"; reg "$JB" "$B"

A1=$(post "$JA" /api/accounts '{"name":"Everyday","currency":"USD","initialDeposit":"500.00"}' | field id)
post "$JA" /api/accounts '{"name":"Savings","currency":"USD","initialDeposit":"1250.75"}' > /dev/null
post "$JA" /api/accounts '{"name":"Travel (CAD)","currency":"CAD"}' > /dev/null
B_NUMBER=$(post "$JB" /api/accounts '{"name":"Partner","currency":"USD"}' | field accountNumber)
A_NUMBER=$(curl -s -b "$JA" "$BASE/api/accounts/$A1" | field accountNumber)
B1=$(curl -s -b "$JB" "$BASE/api/accounts" | field id)

for i in $(seq 1 23); do
  post "$JA" /api/transfers "{\"sourceAccountId\":\"$A1\",\"destinationAccountNumber\":\"$B_NUMBER\",\"amount\":\"1.00\",\"description\":\"Seed payment $i\"}" "seed-$SUF-a$i" > /dev/null
done
for i in 1 2 3; do
  post "$JB" /api/transfers "{\"sourceAccountId\":\"$B1\",\"destinationAccountNumber\":\"$A_NUMBER\",\"amount\":\"0.50\",\"description\":\"Refund $i\"}" "seed-$SUF-b$i" > /dev/null
done

echo "Demo users created (password for both: $PASSWORD)"
echo "  $A   <- 3 accounts, 2 deposits + 26 transfers"
echo "  $B   <- 1 account (partner)"
echo "Sign in at http://localhost:5173"
