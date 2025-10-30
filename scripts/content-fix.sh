#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)
TOOLS_DIR="${REPO_ROOT}/tools/content-tools"
LOG_DIR="${REPO_ROOT}/logs"
REPORT_DIR="${LOG_DIR}/diffs/ru_lint"

APPLY_FLAG=""
if [[ "${1:-}" == "--apply" ]]; then
  APPLY_FLAG="--apply"
elif [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--apply]" >&2
  exit 1
fi

mkdir -p "${LOG_DIR}"

cd "${TOOLS_DIR}"
poetry install >/dev/null

poetry run python qw_fix_familyid.py ${APPLY_FLAG}
poetry run python qw_ru_lint.py ${APPLY_FLAG} --report-dir "${REPORT_DIR}" | tee "${LOG_DIR}/ru_lint.txt"
