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

echo "[changed] BASE_SHA=${BASE} HEAD_SHA=${HEAD}" >&2

all_tmp="/tmp/changed-all.txt"
questions_tmp="/tmp/changed-questions.txt"

: > "${all_tmp}"
: > "${questions_tmp}"

git diff --name-only --diff-filter=ACMR "${BASE}...${HEAD}" | tee "${all_tmp}" | \
  grep -E '^content/questions/[^/]+/[^/]+/.*\\.json$' | tee "${questions_tmp}" > /dev/null || true

if [[ -s "${all_tmp}" ]]; then
  echo "[changed] files captured:" >&2
  sed 's/^/[changed]   /' "${all_tmp}" >&2
fi

if [[ -s "${questions_tmp}" ]]; then
  echo "[changed] question files:" >&2
  sed 's/^/[changed]   /' "${questions_tmp}" >&2
else
  echo "[changed] no question JSONs detected" >&2
fi

cat "${questions_tmp}" 2>/dev/null || true
