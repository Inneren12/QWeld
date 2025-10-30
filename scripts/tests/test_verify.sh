#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify-structure.sh"

if [[ ! -x "${VERIFY_SCRIPT}" ]]; then
    echo "[test] verify script not executable" >&2
    exit 1
fi

set +e
"${VERIFY_SCRIPT}"
status=$?
set -e

echo "[test] verify-structure.sh exit code: ${status}"

if [[ ${status} -ne 0 ]]; then
    echo "[test] verification failed" >&2
    exit ${status}
fi
