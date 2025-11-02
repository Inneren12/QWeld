#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
MODULE_DIR="$ROOT_DIR/core-data"
SCHEMA_DIR="$MODULE_DIR/schemas"

if [[ ! -d "$SCHEMA_DIR" ]]; then
  echo "❌ Room schema directory not found at $SCHEMA_DIR" >&2
  exit 1
fi

./gradlew :core-data:kaptDebugKotlin >/dev/null

if ! git -C "$ROOT_DIR" diff --quiet -- "$SCHEMA_DIR"; then
  cat <<'MSG'
❌ Room schema files changed after regeneration.
   Please commit the updated files produced by Room:
     ./gradlew :core-data:kaptDebugKotlin
MSG
  exit 1
fi

echo "✅ Room schemas are up to date."
