#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="logs"
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/validate-explanations.txt"
: > "${LOG_FILE}"
exec > >(tee -a "${LOG_FILE}") 2>&1

echo "[explain] start"

SCHEMA="schemas/explanation.schema.json"
SEARCH_ROOT="content/explanations"

mapfile -t FILES < <(find "${SEARCH_ROOT}" -type f -name '*.json' -print | sort)

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "[explain] INFO: no explanation files found under ${SEARCH_ROOT}"
  exit 0
fi

for file in "${FILES[@]}"; do
  rel_path=$(realpath --relative-to=. "$file")

  if ! npx --yes ajv-cli@5 validate --spec=draft7 --strict=false --all-errors -s "${SCHEMA}" -d "$file" >/dev/null 2>&1; then
    echo "[explain] file=${rel_path} ERROR: schema validation failed"
    exit 1
  fi

  locale=$(jq -r '.locale' "$file")
  id=$(jq -r '.id' "$file")
  task_id=$(jq -r '.questionRef.taskId' "$file")

  if [[ -z "${locale}" || "${locale}" == "null" || -z "${id}" || "${id}" == "null" || -z "${task_id}" || "${task_id}" == "null" ]]; then
    echo "[explain] file=${rel_path} ERROR: missing locale/id/questionRef.taskId"
    exit 1
  fi

  expected_paths=(
    "content/questions/${locale}/${task_id}/${id}__${locale}.json"
    "content/questions/${locale}/${task_id}/${id}__question_${locale}.json"
    "content/questions/${locale}/${task_id}/${id}.json"
  )

  question_found=false
  for expected_path in "${expected_paths[@]}"; do
    if [[ -f "${expected_path}" ]]; then
      question_found=true
      break
    fi
  done

  if [[ "${question_found}" != "true" ]]; then
    echo "[explain] file=${rel_path} ERROR: missing question=${expected_paths[0]}"
    exit 1
  fi

  echo "[explain] file=${rel_path} ok"
done
