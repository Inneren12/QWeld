#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE_SHA:-${GITHUB_BASE_SHA:-}}"
HEAD="${HEAD_SHA:-${GITHUB_SHA:-}}"

if [[ -z "${BASE:-}" || -z "${HEAD:-}" ]]; then
  if git rev-parse --verify origin/main >/dev/null 2>&1; then
    git fetch --no-tags --prune origin +refs/heads/main:refs/remotes/origin/main >/dev/null 2>&1 || true
    BASE="$(git merge-base HEAD origin/main)"
    HEAD="$(git rev-parse HEAD)"
  else
    BASE="$(git rev-parse HEAD)"
    HEAD="$(git rev-parse HEAD)"
  fi
fi

{
  git diff --name-only --diff-filter=ACMR "${BASE}...${HEAD}"
  git diff --name-only --diff-filter=ACMR
} \
  | sort -u \
  | grep -E '^content/questions/[^/]+/[^/]+/.*\.json$' || true
