#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"
mkdir -p "${LOG_DIR}"

declare -A QUOTAS=(
  ["A-1"]=4
  ["A-2"]=4
  ["A-3"]=5
  ["A-4"]=4
  ["A-5"]=7
  ["B-6"]=10
  ["B-7"]=15
  ["C-8"]=5
  ["C-9"]=7
  ["C-10"]=5
  ["C-11"]=4
  ["D-12"]=18
  ["D-13"]=21
  ["D-14"]=12
  ["D-15"]=4
)

TASKS=("A-1" "A-2" "A-3" "A-4" "A-5" "B-6" "B-7" "C-8" "C-9" "C-10" "C-11" "D-12" "D-13" "D-14" "D-15")

status=0

for locale in en ru; do
  locale_dir="${ROOT_DIR}/content/questions/${locale}"
  if [[ ! -d "${locale_dir}" ]]; then
    echo "[quotas] ERROR: locale directory not found: ${locale_dir}" >&2
    exit 1
  fi

  declare -A COUNTS=()
  while IFS= read -r -d '' file; do
    task_id="$(basename "$(dirname "${file}")")"
    COUNTS["${task_id}"]=$(( ${COUNTS["${task_id}"]:-0} + 1 ))
  done < <(find "${locale_dir}" -type f -name '*.json' -print0)

  locale_log="${LOG_DIR}/quotas_${locale}.txt"
  tmp_report="$(mktemp)"

  {
    echo "[quotas] locale=${locale}"
    printf "%-6s %6s %6s %8s\n" "Task" "Need" "Have" "Missing"
    printf '%-6s %6s %6s %8s\n' '-----' '----' '----' '-------'

    for task in "${TASKS[@]}"; do
      need=${QUOTAS["${task}"]}
      have=${COUNTS["${task}"]:-0}
      deficit=$(( need - have ))
      if (( deficit < 0 )); then
        missing=0
      else
        missing=${deficit}
      fi
      printf "%-6s %6d %6d %8d\n" "${task}" "${need}" "${have}" "${missing}"
      if (( have < need )); then
        status=1
      fi
    done
  } > "${tmp_report}"

  cat "${tmp_report}"
  echo
  cp "${tmp_report}" "${locale_log}"
  rm -f "${tmp_report}"
  unset COUNTS

done

if [[ ${status} -ne 0 ]]; then
  echo "[quotas] ERROR: quota mismatches detected" >&2
else
  echo "[quotas] OK: all quotas satisfied"
fi

exit ${status}
