#!/usr/bin/env bash
set -euo pipefail

# Explanation Coverage Checker for EN/RU
# Reports:
# - % of EN questions with explanations
# - % of EN explanations that have RU translations
# Warns (doesn't fail) when RU explanations are missing

LOG_DIR="logs"
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/explanation-coverage.txt"
: > "${LOG_FILE}"
exec > >(tee -a "${LOG_FILE}") 2>&1

echo "[explain-coverage] start"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

QUESTIONS_DIR="${ROOT_DIR}/content/questions"
EXPLANATIONS_DIR="${ROOT_DIR}/content/explanations"

# Arrays to track coverage
declare -A en_questions=()
declare -A en_explanations=()
declare -A ru_explanations=()
declare -A missing_en_explanations=()
declare -A missing_ru_explanations=()

echo "[explain-coverage] Scanning EN questions..."

# Scan all EN questions and extract base IDs
if [[ -d "${QUESTIONS_DIR}/en" ]]; then
  while IFS= read -r -d '' question_file; do
    if [[ -f "${question_file}" ]]; then
      # Extract question ID from JSON
      question_id=$(jq -r '.id // empty' "${question_file}" 2>/dev/null || true)
      if [[ -n "${question_id}" && "${question_id}" != "null" ]]; then
        # Remove Q- prefix to get base ID for explanation matching
        base_id="${question_id#Q-}"
        task_id=$(jq -r '.taskId // empty' "${question_file}" 2>/dev/null || true)
        en_questions["${base_id}"]="${task_id}"
      fi
    fi
  done < <(find "${QUESTIONS_DIR}/en" -type f -name '*.json' -print0)
fi

echo "[explain-coverage] Found ${#en_questions[@]} EN questions"

# Scan EN explanations
echo "[explain-coverage] Scanning EN explanations..."
if [[ -d "${EXPLANATIONS_DIR}/en" ]]; then
  while IFS= read -r -d '' explain_file; do
    if [[ -f "${explain_file}" ]]; then
      explain_id=$(jq -r '.id // empty' "${explain_file}" 2>/dev/null || true)
      if [[ -n "${explain_id}" && "${explain_id}" != "null" ]]; then
        en_explanations["${explain_id}"]=1
      fi
    fi
  done < <(find "${EXPLANATIONS_DIR}/en" -type f -name '*.json' -print0)
fi

echo "[explain-coverage] Found ${#en_explanations[@]} EN explanations"

# Scan RU explanations
echo "[explain-coverage] Scanning RU explanations..."
if [[ -d "${EXPLANATIONS_DIR}/ru" ]]; then
  while IFS= read -r -d '' explain_file; do
    if [[ -f "${explain_file}" ]]; then
      explain_id=$(jq -r '.id // empty' "${explain_file}" 2>/dev/null || true)
      if [[ -n "${explain_id}" && "${explain_id}" != "null" ]]; then
        ru_explanations["${explain_id}"]=1
      fi
    fi
  done < <(find "${EXPLANATIONS_DIR}/ru" -type f -name '*.json' -print0)
fi

echo "[explain-coverage] Found ${#ru_explanations[@]} RU explanations"

# Check EN questions missing explanations
echo ""
echo "[explain-coverage] ========================================"
echo "[explain-coverage] EN Questions Missing Explanations"
echo "[explain-coverage] ========================================"

for question_id in "${!en_questions[@]}"; do
  if [[ -z "${en_explanations[${question_id}]:-}" ]]; then
    task_id="${en_questions[${question_id}]}"
    missing_en_explanations["${question_id}"]="${task_id}"
    echo "[explain-coverage]   MISSING: Q-${question_id} (task: ${task_id})"
  fi
done

# Check EN explanations missing RU translations
echo ""
echo "[explain-coverage] ========================================"
echo "[explain-coverage] EN Explanations Missing RU Translations"
echo "[explain-coverage] ========================================"

for explain_id in "${!en_explanations[@]}"; do
  if [[ -z "${ru_explanations[${explain_id}]:-}" ]]; then
    missing_ru_explanations["${explain_id}"]=1
    echo "[explain-coverage]   WARNING: ${explain_id} has EN but missing RU"
  fi
done

# Calculate coverage percentages
total_en_questions=${#en_questions[@]}
total_en_with_explanations=$(( total_en_questions - ${#missing_en_explanations[@]} ))
total_en_explanations=${#en_explanations[@]}
total_ru_explanations=${#ru_explanations[@]}

if [[ ${total_en_questions} -gt 0 ]]; then
  en_coverage_pct=$(awk "BEGIN {printf \"%.1f\", (${total_en_with_explanations} / ${total_en_questions}) * 100}")
else
  en_coverage_pct="0.0"
fi

if [[ ${total_en_explanations} -gt 0 ]]; then
  ru_translation_pct=$(awk "BEGIN {printf \"%.1f\", (${total_ru_explanations} / ${total_en_explanations}) * 100}")
else
  ru_translation_pct="0.0"
fi

# Print summary
echo ""
echo "[explain-coverage] ========================================"
echo "[explain-coverage] COVERAGE SUMMARY"
echo "[explain-coverage] ========================================"
echo "[explain-coverage] Total EN questions:           ${total_en_questions}"
echo "[explain-coverage] EN questions with explanations: ${total_en_with_explanations}"
echo "[explain-coverage] EN questions missing explanations: ${#missing_en_explanations[@]}"
echo "[explain-coverage] EN explanation coverage:      ${en_coverage_pct}%"
echo ""
echo "[explain-coverage] Total EN explanations:        ${total_en_explanations}"
echo "[explain-coverage] RU translations available:    ${total_ru_explanations}"
echo "[explain-coverage] RU translations missing:      ${#missing_ru_explanations[@]}"
echo "[explain-coverage] RU translation coverage:      ${ru_translation_pct}%"
echo "[explain-coverage] ========================================"

# Exit with success (warnings don't fail the build)
# In the future, you can add --fail-on-missing flag to enforce thresholds
echo ""
echo "[explain-coverage] OK: Coverage check complete (warnings logged)"
exit 0
