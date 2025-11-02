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

if [[ -d "${REPO_ROOT}/.ci-node/node_modules" ]]; then
  if [[ -z "${NODE_PATH:-}" ]]; then
    export NODE_PATH="${REPO_ROOT}/.ci-node/node_modules"
  else
    export NODE_PATH="${NODE_PATH}:${REPO_ROOT}/.ci-node/node_modules"
  fi
fi

SCHEMA="${SCRIPT_DIR}/schemas/blueprint.schema.json"
BLUEPRINT="${REPO_ROOT}/content/blueprints/welder_ip_sk_202404.json"

if node - "${SCHEMA}" "${BLUEPRINT}" <<'NODE'
const fs = require('fs');
const path = require('path');

let Ajv;
try {
  Ajv = require('ajv');
} catch (error) {
  console.error('[blueprint] ERROR: ajv module is not available');
  process.exit(1);
}

let addFormats = () => {};
try {
  addFormats = require('ajv-formats');
} catch (error) {
  // ajv-formats is optional; continue without it when not present
}

const [, , schemaPath, dataPath] = process.argv;

const loadJson = (filePath) => {
  const absolute = path.resolve(filePath);
  return JSON.parse(fs.readFileSync(absolute, 'utf8'));
};

const schema = loadJson(schemaPath);
const data = loadJson(dataPath);

const ajv = new Ajv({ allErrors: true, strict: false });
addFormats(ajv);

const validate = ajv.compile(schema);
const valid = validate(data);

if (!valid) {
  console.error('[blueprint] schema validation errors:');
  console.error(JSON.stringify(validate.errors, null, 2));
  process.exit(1);
}
NODE
then
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
