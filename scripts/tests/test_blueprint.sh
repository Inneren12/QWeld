#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
VALIDATE_SCRIPT="$ROOT_DIR/scripts/validate-blueprint.sh"
BLUEPRINT="$ROOT_DIR/content/blueprints/welder_ip_2024.json"

printf 'Running blueprint validation (positive case)...\n'
BLUEPRINT_LOG_FILE="$ROOT_DIR/logs/test-blueprint-positive.txt" "$VALIDATE_SCRIPT" "$BLUEPRINT"

printf 'Running blueprint validation (negative case)...\n'
TEMP_BLUEPRINT=$(mktemp --suffix .json)
trap 'rm -f "$TEMP_BLUEPRINT"' EXIT
jq '.totalQuestions = (.totalQuestions + 1)' "$BLUEPRINT" > "$TEMP_BLUEPRINT"
if BLUEPRINT_LOG_FILE="$ROOT_DIR/logs/test-blueprint-negative.txt" "$VALIDATE_SCRIPT" "$TEMP_BLUEPRINT"; then
  echo "Expected validation failure for modified blueprint" >&2
  exit 1
else
  echo "Negative case failed as expected."
fi
