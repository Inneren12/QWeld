#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"
DIST_ROOT="${ROOT_DIR}/dist/questions"
mkdir -p "${LOG_DIR}" "${DIST_ROOT}"

for locale in en ru; do
  src_dir="${ROOT_DIR}/content/questions/${locale}"
  if [[ ! -d "${src_dir}" ]]; then
    echo "[dist] ERROR: locale directory not found: ${src_dir}" >&2
    exit 1
  fi

  mapfile -t files < <(find "${src_dir}" -type f -name '*.json' -print | sort)
  output_dir="${DIST_ROOT}/${locale}"
  mkdir -p "${output_dir}"
  output_file="${output_dir}/bank.v1.json"
  locale_log="${LOG_DIR}/build_dist_${locale}.txt"

  {
    echo "[dist] locale=${locale}"
    echo "[dist] source_dir=${src_dir}"
    echo "[dist] files_count=${#files[@]}"
    echo "[dist] output=${output_file}"
  } | tee "${locale_log}"

  if [[ ${#files[@]} -eq 0 ]]; then
    jq -n '[]' > "${output_file}"
    continue
  fi

  jq -s '.' "${files[@]}" > "${output_file}"
done

echo "[dist] OK: question banks generated under ${DIST_ROOT}"
