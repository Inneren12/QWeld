#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

LOG_DIR="${REPO_ROOT}/logs"
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/schema-validation.log"
: > "${LOG_FILE}"
exec > >(tee -a "${LOG_FILE}") 2>&1

pushd "${SCRIPT_DIR}" > /dev/null
trap 'popd > /dev/null' EXIT

echo "[schema] validating question indexes against welder blueprint schema"
if npx ajv validate \
  -s schemas/welder_blueprint.schema.json \
  -d "../../app-android/src/main/assets/questions/**/index.json" \
  -c ajv-formats \
  --strict=false --all-errors --spec=draft7; then
  echo "[schema] ajv validation succeeded"
else
  echo "[schema] ERROR: ajv validation failed"
  exit 1
fi
