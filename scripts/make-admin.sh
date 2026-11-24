#!/usr/bin/env bash
# Promotes an existing user to ADMIN. There is deliberately no API for this.
# Usage: bash scripts/make-admin.sh user@example.com
# The user must sign out and sign in again: the role is stored in the JWT at sign-in.
set -euo pipefail
EMAIL=${1:?usage: bash scripts/make-admin.sh user@example.com}
CONTAINER=${LEDGERLITE_DB_CONTAINER:-ledgerlite-postgres}

docker exec -i "$CONTAINER" psql -U ledgerlite -d ledgerlite -v ON_ERROR_STOP=1 -v email="$EMAIL" <<'SQL'
UPDATE users
   SET role = 'ADMIN'
 WHERE email = lower(:'email')
   AND id <> '00000000-0000-0000-0000-000000000001';
SQL
echo "If the line above says UPDATE 1, $EMAIL is now an admin (sign in again to get an admin token)."
echo "To revoke:  UPDATE users SET role = 'USER' WHERE email = '$EMAIL';"
