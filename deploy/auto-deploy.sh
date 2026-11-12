#!/usr/bin/env bash
# Pull-based CD for the mini PC: deploy the newest main commit whose image build and publish succeeded.
# Runs from a systemd timer (deploy/systemd). It only reads public GitHub data, so no inbound access
# and no GitHub-side secrets are needed. The actual rollout, health checks and rollback stay in deploy.sh.
#
# Optional environment (e.g. /etc/idea2ip/auto-deploy.env):
#   GITHUB_TOKEN             read-only token; raises the GitHub API rate limit
#   IDEA2IP_DEPLOY_WEBHOOK   Discord/Slack-compatible webhook for deploy results
#   IDEA2IP_SKIP_BACKUP=1    skip the pg_dump taken before each deploy
#   IDEA2IP_DRY_RUN=1        report what would be deployed without changing anything
set -euo pipefail

REPO="${IDEA2IP_REPO:-Line4-Idea2IP/Idea2IP-BE}"
WORKFLOW="${IDEA2IP_WORKFLOW:-docker.yml}"
IMAGES=(ghcr.io/line4-idea2ip/idea2ip-be ghcr.io/line4-idea2ip/idea2ip-rag)
BACKUPS_KEPT=10

log() { printf '%s %s\n' "$(date -Iseconds)" "$*"; }

notify() {
  [[ -n "${IDEA2IP_DEPLOY_WEBHOOK:-}" ]] || return 0
  local text="[Idea2IP] $1"
  curl -fsS --max-time 10 -H 'Content-Type: application/json' \
    -d "{\"content\":\"${text//\"/\\\"}\",\"text\":\"${text//\"/\\\"}\"}" \
    "$IDEA2IP_DEPLOY_WEBHOOK" >/dev/null || log "webhook notification failed"
}

latest_release() {
  local url="https://api.github.com/repos/$REPO/actions/workflows/$WORKFLOW/runs?branch=main&event=push&status=success&per_page=1"
  local auth=()
  [[ -n "${GITHUB_TOKEN:-}" ]] && auth=(-H "Authorization: Bearer $GITHUB_TOKEN")
  # A successful push run on main means both images were published with this commit SHA as the tag.
  local body
  body="$(curl -fsSL --max-time 20 -H 'Accept: application/vnd.github+json' "${auth[@]}" "$url")" || return 0
  grep -m1 -o '"head_sha": *"[0-9a-f]\{40\}"' <<< "$body" | grep -o '[0-9a-f]\{40\}' || true
}

compose() {
  RELEASE_SHA="$1" docker compose --env-file deploy/.env "${@:2}"
}

backup_database() {
  local current="$1"
  [[ "${IDEA2IP_SKIP_BACKUP:-}" == 1 || -z "$current" ]] && return 0
  # Hibernate updates the schema on start, and image rollback does not restore data.
  mkdir -p -m 700 deploy/backups
  local file
  file="deploy/backups/idea2ip-$(date +%Y%m%d-%H%M%S)-${current:0:7}.sql.gz"
  # Explicit checks: errexit does not apply inside the caller's `if`. pipefail reports a pg_dump failure.
  if ! compose "$current" exec -T postgres pg_dump -U postgres -d idea2ip | gzip > "$file.tmp"; then
    rm -f "$file.tmp"
    return 1
  fi
  mv -f "$file.tmp" "$file" || return 1
  log "database backup: $file"
  ls -1t deploy/backups/idea2ip-*.sql.gz | tail -n +$((BACKUPS_KEPT + 1)) | xargs -r rm -f --
}

prune_images() {
  local keep_new="$1" keep_old="$2" image
  for image in "${IMAGES[@]}"; do
    docker image ls --format '{{.Repository}}:{{.Tag}}' "$image" \
      | grep -v -e ":$keep_new\$" ${keep_old:+-e ":$keep_old\$"} \
      | xargs -r docker image rm >/dev/null 2>&1 || true
  done
}

main() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  if [[ -e deploy/.hold ]]; then
    log "deploy/.hold exists; automatic deploys are paused"
    return 0
  fi
  [[ -r deploy/.env ]] || { log "deploy/.env is missing"; return 1; }

  local sha current
  sha="$(latest_release)"
  if [[ ! "$sha" =~ ^[0-9a-f]{40}$ ]]; then
    log "no successful main release found (GitHub API unreachable or rate limited)"
    return 0
  fi
  current="$(cat deploy/.release 2>/dev/null || true)"
  [[ "$sha" == "$current" ]] && return 0
  if [[ -r deploy/.failed ]] && grep -qx "$sha" deploy/.failed; then
    log "skipping $sha: it failed before; remove it from deploy/.failed to retry"
    return 0
  fi
  if command -v flock >/dev/null && ! flock -n deploy/.deploy.lock true; then
    log "another deployment is running"
    return 0
  fi
  if [[ "${IDEA2IP_DRY_RUN:-}" == 1 ]]; then
    log "dry run: would deploy $sha (current: ${current:-none})"
    return 0
  fi

  log "deploying $sha (current: ${current:-none})"
  # Back up with the running release's compose file, before anything changes; retried on the next run.
  if ! backup_database "$current"; then
    log "database backup failed; deploy postponed"
    notify "DB 백업 실패로 ${sha:0:7} 배포를 미뤘습니다."
    return 1
  fi
  # Check out the release so compose.yaml and deploy.sh match its images; deploy/.env is untracked.
  git fetch --quiet origin
  git -c advice.detachedHead=false checkout --quiet --detach "$sha"

  if bash deploy/deploy.sh "$sha"; then
    log "deployed $sha"
    prune_images "$sha" "$current"
    notify "배포 완료: ${sha:0:7}"
  else
    printf '%s\n' "$sha" >> deploy/.failed
    # deploy.sh restores the previous images; keep the checkout consistent with what is running.
    [[ -n "$current" ]] && git -c advice.detachedHead=false checkout --quiet --detach "$current" || true
    log "deploy of $sha failed; still running ${current:-nothing}"
    notify "배포 실패: ${sha:0:7} (현재 ${current:0:7} 유지). 미니 PC의 journalctl -u idea2ip-deploy를 확인하세요."
    return 1
  fi
}

# Parsed fully before running, so checking out a new version of this file mid-run is safe.
main "$@"
exit $?
