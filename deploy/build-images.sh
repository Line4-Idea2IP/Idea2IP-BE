#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
release_sha="$(git rev-parse HEAD)"
rag_sha="$(tr -d '\r\n' < deploy/rag-revision.txt)"
[[ "$rag_sha" =~ ^[0-9a-f]{40}$ ]]

rag_dir="$(mktemp -d)"
trap 'rm -rf -- "$rag_dir"' EXIT
git -C "$rag_dir" init --quiet
git -C "$rag_dir" fetch --quiet --depth=1 https://github.com/Line4-Idea2IP/RAG.git "$rag_sha"
test "$(git -C "$rag_dir" rev-parse FETCH_HEAD)" = "$rag_sha"
mkdir "$rag_dir/context"
paths="$(git -C "$rag_dir" ls-tree -r --name-only FETCH_HEAD -- \
  pyproject.toml constraints-tested.txt idea2ip samples/legal_catalog.json)"
# Read only required blobs, including on hosts that reject Windows metadata names in the tree.
while IFS= read -r path; do
  mkdir -p "$rag_dir/context/$(dirname "$path")"
  git -C "$rag_dir" show "FETCH_HEAD:$path" > "$rag_dir/context/$path"
done <<< "$paths"

# In GitHub Actions the layer cache is kept between runs, so the dependency layers are reused instead of
# downloaded again from Maven Central and PyPI. Elsewhere this is a plain docker build.
build() {
  local scope="$1"; shift
  if [[ -n "${ACTIONS_RUNTIME_TOKEN:-}" ]]; then
    docker buildx build --load --cache-from "type=gha,scope=$scope" --cache-to "type=gha,mode=max,scope=$scope" "$@"
  else
    docker build "$@"
  fi
}

build idea2ip-be --label "org.opencontainers.image.revision=$release_sha" \
  -t "ghcr.io/line4-idea2ip/idea2ip-be:$release_sha" .
build idea2ip-rag --label "org.opencontainers.image.revision=$rag_sha" \
  -f deploy/rag.Dockerfile -t "ghcr.io/line4-idea2ip/idea2ip-rag:$release_sha" "$rag_dir/context"

RELEASE_SHA="$release_sha" POSTGRES_PASSWORD=ci-placeholder POSTGRES_ADMIN_PASSWORD=ci-placeholder JWT_SECRET=ci-placeholder \
  BRIEF_AI_TOKEN=ci-placeholder IDEA2IP_LLM_BASE_URL=http://localhost:8081/v1 \
  docker compose config --quiet
