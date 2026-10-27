#!/usr/bin/env bash
# Seeds two demo users (alice, bob) and uploads each one's sample documents
# through the public API, so ownership comes from their own JWT.
#
# Usage: bash scripts/seed.sh
# Env:   API_URL (default http://localhost:8080), SEED_PASSWORD (default Passw0rd!)
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
SEED_PASSWORD="${SEED_PASSWORD:-Passw0rd!}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Pull "token" out of the JSON login response without needing jq.
extract_token() {
  sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

seed_user() {
  local user="$1"

  # 201 = created, 409 = already exists; both are fine.
  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"email\":\"$user@example.com\",\"password\":\"$SEED_PASSWORD\"}")
  echo "register $user: HTTP $status"

  local token
  token=$(curl -s -X POST "$API_URL/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"password\":\"$SEED_PASSWORD\"}" | extract_token)
  if [[ -z "$token" ]]; then
    echo "login failed for $user" >&2
    exit 1
  fi

  for file in "$ROOT_DIR/sample-docs/$user"/*; do
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/api/files/upload" \
      -H "Authorization: Bearer $token" \
      -F "file=@$file")
    echo "  upload $(basename "$file"): HTTP $status"
  done
}

seed_user alice
seed_user bob
echo "Done. Files are indexed in the background; check index_status in the UI or the files table."
