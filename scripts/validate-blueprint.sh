#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="logs"
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/validate-blueprint.txt"
: > "${LOG_FILE}"
exec > >(tee -a "${LOG_FILE}") 2>&1

echo "[policy] start"

SCHEMA="schemas/blueprint.schema.json"
BLUEPRINT="content/blueprints/welder_ip_sk_202404.json"

if ! npx --yes ajv-cli@5 validate --spec=draft7 --strict=false --all-errors -s "${SCHEMA}" -d "${BLUEPRINT}"; then
  echo "[blueprint] ERROR: schema validation failed"
  exit 1
fi

total_quota=$(jq '[.blocks[].tasks[].quota] | add' "${BLUEPRINT}")
expected_total=$(jq '.questionCount' "${BLUEPRINT}")
task_count=$(jq '[.blocks[].tasks[]] | length' "${BLUEPRINT}")
version=$(jq -r '.blueprintVersion' "${BLUEPRINT}")

if [[ "${total_quota}" != "${expected_total}" ]]; then
  echo "[blueprint] ERROR: total=${total_quota} expected=${expected_total}"
  exit 1
fi

if [[ "${version}" != 1.0.* ]]; then
  echo "[blueprint] ERROR: version=${version} does not satisfy 1.0.x policy"
  exit 1
fi

echo "[blueprint] schema=ok total=${total_quota} tasks=${task_count} version=${version}"
