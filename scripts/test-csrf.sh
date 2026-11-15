#!/usr/bin/env bash
BASE=http://localhost:8080
JAR=$(mktemp)
EMAIL="cal$(date +%s)@example.com"
PASSWORD='Str0ng!Passw0rd'
CODE='-s -o /dev/null -w %{http_code}'

curl -s -o /dev/null -c "$JAR" -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"

# Authenticated GET, saving any cookie changes like a browser would
echo "GET /me      -> $(curl $CODE -b "$JAR" -c "$JAR" "$BASE/api/auth/me")   (expect 200)"

XSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' "$JAR")
if [ -z "$XSRF" ]; then echo "XSRF-TOKEN cookie was WIPED after /me (bug present)"; else echo "XSRF-TOKEN cookie survived /me (good)"; fi

echo "POST /logout -> $(curl $CODE -b "$JAR" -c "$JAR" -X POST -H "X-XSRF-TOKEN: $XSRF" "$BASE/api/auth/logout")   (expect 204)"
rm -f "$JAR"