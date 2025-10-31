#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="logs"
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/validate-questions.txt"
: > "${LOG_FILE}"
exec > >(tee -a "${LOG_FILE}") 2>&1

echo "[policy] start"

SCHEMA="schemas/question.schema.json"
BAN_PATTERN='real exam|red seal exam question|actual exam'

CHANGED_ONLY=false
SEARCH_PATHS=()

usage() {
  cat <<USAGE
Usage: $0 [--changed-only] [paths...]
  --changed-only   Validate only modified question files (defaults to full run otherwise)
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --changed-only)
      CHANGED_ONLY=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --*)
      echo "[questions] ERROR: unknown option: $1" >&2
      exit 1
      ;;
    *)
      SEARCH_PATHS+=("$1")
      shift
      ;;
  esac
done

if [[ ${#SEARCH_PATHS[@]} -eq 0 ]]; then
  SEARCH_PATHS=("content/questions")
fi

if ${CHANGED_ONLY}; then
  base_commit="${BASE_SHA:-${GITHUB_BASE_SHA:-}}"
  head_commit="${HEAD_SHA:-${GITHUB_SHA:-}}"

  if [[ -z "${base_commit:-}" || -z "${head_commit:-}" ]]; then
    if git rev-parse --verify origin/main >/dev/null 2>&1; then
      git fetch --no-tags --prune origin +refs/heads/main:refs/remotes/origin/main >/dev/null 2>&1 || true
      base_commit="$(git merge-base HEAD origin/main)"
      head_commit="$(git rev-parse HEAD)"
    else
      base_commit="$(git rev-parse HEAD)"
      head_commit="$(git rev-parse HEAD)"
    fi
  fi

  export BASE_SHA="${base_commit}"
  export HEAD_SHA="${head_commit}"

  mapfile -t CHANGED_FILES < <(bash scripts/changed-files.sh)
  mapfile -t DIFF_PATHS < <({
    git diff --name-only --diff-filter=ACMRD "${base_commit}...${head_commit}"
    git diff --name-only --diff-filter=ACMRD
  } | sort -u)

  schema_changed=false
  blueprints_changed=false
  for diff_path in "${DIFF_PATHS[@]}"; do
    if [[ "${diff_path}" == content/blueprints/* ]]; then
      blueprints_changed=true
    fi
    if [[ "${diff_path}" == schemas/* || "${diff_path}" == content/schema/* ]]; then
      schema_changed=true
    fi
  done

  if ${schema_changed} || ${blueprints_changed}; then
    echo "[validate] schema or blueprint changed; running full validation"
    CHANGED_ONLY=false
  elif [[ ${#CHANGED_FILES[@]} -eq 0 ]]; then
    echo "[validate] no changed question files; skip"
    exit 0
  else
    tmp_list="$(mktemp)"
    trap 'rm -f "${tmp_list}"' EXIT
    printf '%s\n' "${CHANGED_FILES[@]}" > "${tmp_list}"
    node scripts/validate-questions-batch.mjs --files-from "${tmp_list}"
    rm -f "${tmp_list}"
    trap - EXIT
    exit 0
  fi
fi

mapfile -t FILES < <(find "${SEARCH_PATHS[@]}" -type f -name '*.json' -print | sort)

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "[questions] INFO: no question files found in ${SEARCH_PATHS[*]}"
  exit 0
fi

for file in "${FILES[@]}"; do
  rel_path=$(realpath --relative-to=. "$file")

  if grep -iEq "${BAN_PATTERN}" "$file"; then
    match=$(grep -iE "${BAN_PATTERN}" "$file" | head -n 1)
    echo "[questions] ERROR: banned phrase found: \"${match}\" in ${rel_path}"
    exit 1
  fi

  if ! npx --yes ajv-cli@5 validate --spec=draft7 --strict=false --data --all-errors -s "${SCHEMA}" -d "$file" >/dev/null; then
    echo "[questions] ERROR: schema validation failed for ${rel_path}"
    exit 1
  fi

  correct_id=$(jq -r '.correctId' "$file")
  if [[ -z "${correct_id}" || "${correct_id}" == "null" ]]; then
    echo "[questions] ERROR: missing correctId in ${rel_path}"
    exit 1
  fi

  if ! jq -e --arg id "${correct_id}" '.rationales | has($id)' "$file" >/dev/null; then
    echo "[questions] ERROR: rationale missing for correctId=${correct_id} file=${rel_path}"
    exit 1
  fi

  echo "[questions] file=${rel_path} schema=ok rationale=ok"
done
