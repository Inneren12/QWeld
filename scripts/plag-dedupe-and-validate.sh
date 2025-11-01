#!/usr/bin/env bash
set -euo pipefail
node scripts/plag-dedupe.mjs --dry-run
node scripts/plag-dedupe.mjs --apply
bash scripts/validate-questions.sh
bash scripts/check-quotas.sh --profile content/exam_profiles/welder_exam_2024.json --locales en,ru --mode min --min-multiple 1 --allow-extra
