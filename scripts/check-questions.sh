#!/usr/bin/env bash
set -euo pipefail

node scripts/migrate-questions.mjs --dry-run
bash scripts/validate-questions.sh
