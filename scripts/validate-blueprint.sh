#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DEFAULT_BLUEPRINT="$ROOT_DIR/content/blueprints/welder_ip_2024.json"
DEFAULT_SCHEMA="$ROOT_DIR/schemas/blueprint.schema.json"
BLUEPRINT_PATH="${1:-$DEFAULT_BLUEPRINT}"
SCHEMA_PATH="${2:-$DEFAULT_SCHEMA}"
LOG_FILE="${BLUEPRINT_LOG_FILE:-$ROOT_DIR/logs/blueprint-validate.txt}"

mkdir -p "$(dirname "$LOG_FILE")"
: > "$LOG_FILE"

log() {
  echo "$1" | tee -a "$LOG_FILE"
}

SCHEMA_CMD_OUTPUT=$(mktemp)
trap 'rm -f "$SCHEMA_CMD_OUTPUT"' EXIT

if ! npx --yes ajv-cli validate -s "$SCHEMA_PATH" -d "$BLUEPRINT_PATH" >"$SCHEMA_CMD_OUTPUT" 2>&1; then
  cat "$SCHEMA_CMD_OUTPUT" | tee -a "$LOG_FILE"
  exit 1
fi
log "[blueprint] schema ok"

TOTAL=$(jq -r '.totalQuestions' "$BLUEPRINT_PATH")
BLOCK_COUNT=$(jq -r '.blocks | length' "$BLUEPRINT_PATH")
TASK_COUNT=$(jq -r '[.blocks[].tasks | length] | add' "$BLUEPRINT_PATH")
QUOTA_SUM=$(jq -r '[.blocks[].tasks[].quota] | add' "$BLUEPRINT_PATH")

if [[ "$QUOTA_SUM" != "$TOTAL" ]]; then
  log "[blueprint] ERROR: quota mismatch (totalQuestions=$TOTAL quotaSum=$QUOTA_SUM)"
  exit 1
fi

log "[blueprint] total=$TOTAL blocks=$BLOCK_COUNT tasks=$TASK_COUNT ok"
