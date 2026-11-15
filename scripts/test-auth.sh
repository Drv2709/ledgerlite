#!/usr/bin/env bash
# P2 auth smoke test. Run from anywhere while the app is running on :8080.

BASE=http://localhost:8080
JAR=$(mktemp)
EMAIL="ben$(date +%s)@example.com"
PASSWORD='Str0ng!Passw0rd'

redact() { sed -E 's/LL_TOKEN=[^;]*/LL_TOKEN=<redacted>/'; }

echo "=== A. Register (expect 201, LL_TOKEN HttpOnly, no token/hash in body) ==="
curl -s -i -c "$JAR" -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | redact
echo; echo

echo "=== B. /me with cookie (expect 200) ==="
curl -s -i -b "$JAR" "$BASE/api/auth/me" | redact
echo; echo

echo "=== C. Logout WITHOUT CSRF header (expect 403) ==="
curl -s -i -b "$JAR" -X POST "$BASE/api/auth/logout" | redact
echo; echo

echo "=== D. Logout WITH CSRF header (expect 204, LL_TOKEN Max-Age=0) ==="
XSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' "$JAR")
curl -s -i -b "$JAR" -c "$JAR" -X POST -H "X-XSRF-TOKEN: $XSRF" "$BASE/api/auth/logout" | redact
echo; echo

echo "=== E. /me after logout (expect 401) ==="
curl -s -i -b "$JAR" "$BASE/api/auth/me" | redact
echo

rm -f "$JAR"