#!/usr/bin/env bash
set -euo pipefail

echo "[policy] start"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AJV_BIN="${AJV_BIN:-${ROOT}/.ci-node/node_modules/.bin/ajv}"

if [[ ! -x "$AJV_BIN" ]]; then
  echo "[policy] ajv not found, installing locally into .ci-node..."
  npm install ajv@8 ajv-formats@3 ajv-cli@5 \
    --no-save --no-audit --no-fund --no-package-lock \
    --prefix "${ROOT}/.ci-node"
fi

AJV_PACKAGE="${ROOT}/.ci-node/node_modules/ajv/package.json"
if [[ -f "$AJV_PACKAGE" ]]; then
  AJV_VERSION=$(node -e "console.log(require(process.argv[1]).version)" "$AJV_PACKAGE" 2>/dev/null || true)
  if [[ -n "${AJV_VERSION}" ]]; then
    echo "[policy] using ajv@${AJV_VERSION}"
  else
    echo "[policy] using ajv (version unknown)"
  fi
else
  echo "[policy] ajv package metadata not found"
fi

SCHEMA="${ROOT}/assets/blueprints/schema/welder_blueprint.schema.json"
if [[ ! -f "$SCHEMA" ]]; then
  echo "[policy] ERROR: schema not found: $SCHEMA" >&2
  exit 1
fi

# Проверяем все index.json, лежащие рядом с бандлами в assets
DATA_GLOB="${ROOT}/app-android/src/main/assets/questions/**/index.json"

# --load-formats включает ajv-formats (через -c)
"$AJV_BIN" validate \
  -s "$SCHEMA" \
  -d "$DATA_GLOB" \
  -c ajv-formats \
  --strict=false --all-errors --spec=draft2020

echo "[policy] ok"
