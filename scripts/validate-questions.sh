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

if [[ $# -gt 0 ]]; then
  SEARCH_PATHS=("$@")
else
  SEARCH_PATHS=("content/questions")
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
