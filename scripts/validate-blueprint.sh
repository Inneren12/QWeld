#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

LOG_DIR="${REPO_ROOT}/logs"
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/validate-blueprint.txt"
: > "${LOG_FILE}"
exec > >(tee -a "${LOG_FILE}") 2>&1

echo "[policy] start"

SCHEMA_REL="schemas/welder_blueprint.schema.json"
BLUEPRINT_REL="../../content/blueprints/welder_ip_sk_202404.json"
BLUEPRINT="${REPO_ROOT}/content/blueprints/welder_ip_sk_202404.json"

if (
  cd "${REPO_ROOT}/tools/schema-validation"
  npx ajv validate \
    -s "${SCHEMA_REL}" \
    -d "${BLUEPRINT_REL}" \
    -c ajv-formats \
    --strict=false --all-errors --spec=draft7
); then
  echo "[blueprint] schema validation=ok"
else
  echo "[blueprint] ERROR: schema validation failed"
  exit 1
fi

total_quota=$(jq '[.blocks[].tasks[].quota] | add' "${BLUEPRINT}")
expected_total=$(jq '.questionCount' "${BLUEPRINT}")
task_count=$(jq '[.blocks[].tasks[]] | length' "${BLUEPRINT}")
version=$(jq -r '.blueprintVersion' "${BLUEPRINT}")
block_count=$(jq '.blocks | length' "${BLUEPRINT}")

if [[ "${total_quota}" != "${expected_total}" ]]; then
  echo "[blueprint] ERROR: total=${total_quota} expected=${expected_total}"
  exit 1
fi

if [[ "${task_count}" != "15" ]]; then
  echo "[blueprint] ERROR: task_count=${task_count} expected=15"
  exit 1
fi

if [[ "${block_count}" != "4" ]]; then
  echo "[blueprint] ERROR: blocks=${block_count} expected=4"
  exit 1
fi

if [[ "${version}" != 1.0.* ]]; then
  echo "[blueprint] ERROR: version=${version} does not satisfy 1.0.x policy"
  exit 1
fi

echo "[blueprint] schema=ok total=${total_quota} tasks=${task_count} blocks=${block_count} version=${version}"
