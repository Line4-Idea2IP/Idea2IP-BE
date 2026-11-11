#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
release_sha="${1:-}"
if [[ ! "$release_sha" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'Usage: bash deploy/deploy.sh <40-character backend commit SHA>\n' >&2
  exit 2
fi
if [[ ! -r deploy/.env ]]; then
  printf 'Create deploy/.env from deploy/.env.example first.\n' >&2
  exit 2
fi

exec 9>deploy/.deploy.lock
flock -n 9 || { printf 'Another deployment is running.\n' >&2; exit 1; }
compose() {
  RELEASE_SHA="$release_sha" docker compose --env-file deploy/.env "$@"
}

compose config --quiet
compose pull --policy missing postgres
compose pull backend rag

# Verify the desktop model from the same Docker network before replacing services.
compose run --rm --no-deps rag python -c '
import os
import httpx

url = os.environ["IDEA2IP_LLM_BASE_URL"].rstrip("/") + "/models"
with httpx.Client(timeout=10, trust_env=False) as client:
    response = client.get(url)
    response.raise_for_status()
    aliases = {model["id"] for model in response.json()["data"]}
if os.environ["IDEA2IP_LLM_MODEL"] not in aliases:
    raise SystemExit("Configured llama.cpp model alias was not found")
print("llama.cpp is reachable from Docker")
'

if ! compose up -d --pull never --wait --wait-timeout 180; then
  printf 'Deployment did not become healthy. Check docker compose logs.\n' >&2
  if [[ -r deploy/.release ]]; then
    previous_sha="$(cat deploy/.release)"
    if [[ "$previous_sha" =~ ^[0-9a-f]{40}$ && "$previous_sha" != "$release_sha" ]]; then
      printf 'Restoring previous application images: %s\n' "$previous_sha" >&2
      release_sha="$previous_sha"
      compose up -d --pull never --wait --wait-timeout 180
    fi
  fi
  exit 1
fi
printf '%s\n' "$release_sha" > deploy/.release.tmp
mv -f deploy/.release.tmp deploy/.release
printf 'Deployed %s\n' "$release_sha"
