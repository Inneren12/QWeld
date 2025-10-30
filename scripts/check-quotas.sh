#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"
mkdir -p "${LOG_DIR}"

usage() {
  cat <<'USAGE'
Usage: scripts/check-quotas.sh [options]

Options:
  --blueprint <path>   Path to the blueprint JSON file.
  --content <path>     Root directory with locale folders (default: content/questions).
  --locales <list>     Comma-separated locales to check (default: en,ru).
  --non-strict         Do not fail when extra tasks exist in content.
  --help               Show this help message and exit.

Priority for resolving the blueprint path:
  1. --blueprint CLI argument
  2. $BLUEPRINT environment variable
  3. content/blueprints/active.json if it exists
  4. content/blueprints/welder_ip_sk_202404.json
USAGE
}

error() {
  echo "[quotas] ERROR: $1" >&2
}

log_line() {
  local message="$1"
  echo "${message}"
  if [[ -n "${REPORT_FILE:-}" ]]; then
    echo "${message}" >> "${REPORT_FILE}"
  fi
}

# Defaults
cli_blueprint=""
content_root="content/questions"
locales_arg="en,ru"
strict_mode=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --blueprint)
      if [[ $# -lt 2 ]]; then
        error "--blueprint requires a path argument"
        usage
        exit 1
      fi
      cli_blueprint="$2"
      shift 2
      ;;
    --content)
      if [[ $# -lt 2 ]]; then
        error "--content requires a path argument"
        usage
        exit 1
      fi
      content_root="$2"
      shift 2
      ;;
    --locales)
      if [[ $# -lt 2 ]]; then
        error "--locales requires a comma-separated list"
        usage
        exit 1
      fi
      locales_arg="$2"
      shift 2
      ;;
    --non-strict)
      strict_mode=0
      shift
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      error "unknown option: $1"
      usage
      exit 1
      ;;
  esac
done

resolve_blueprint() {
  local blueprint_path=""

  if [[ -n "${cli_blueprint}" ]]; then
    blueprint_path="${cli_blueprint}"
  elif [[ -n "${BLUEPRINT:-}" ]]; then
    blueprint_path="${BLUEPRINT}"
  elif [[ -f "${ROOT_DIR}/content/blueprints/active.json" ]]; then
    blueprint_path="${ROOT_DIR}/content/blueprints/active.json"
  else
    blueprint_path="${ROOT_DIR}/content/blueprints/welder_ip_sk_202404.json"
  fi

  if [[ "${blueprint_path}" != /* ]]; then
    blueprint_path="${ROOT_DIR}/${blueprint_path}"
  fi

  echo "${blueprint_path}"
}

BLUEPRINT_PATH="$(resolve_blueprint)"

if [[ ! -f "${BLUEPRINT_PATH}" ]]; then
  error "blueprint not found: ${BLUEPRINT_PATH}"
  exit 1
fi

if [[ "${content_root}" != /* ]]; then
  content_root="${ROOT_DIR}/${content_root}"
fi

if [[ ! -d "${content_root}" ]]; then
  error "content directory not found: ${content_root}"
  exit 1
fi

IFS=',' read -r -a raw_locales <<< "${locales_arg}"
declare -a LOCALES=()
for locale in "${raw_locales[@]}"; do
  locale="${locale//[[:space:]]/}"
  if [[ -n "${locale}" ]]; then
    LOCALES+=("${locale}")
  fi
done

if [[ ${#LOCALES[@]} -eq 0 ]]; then
  error "no locales specified"
  exit 1
fi

declare -a quota_pairs=()

if command -v jq >/dev/null 2>&1; then
  if ! mapfile -t quota_pairs < <(jq -r '(
      .. | objects
      | select(((.type? // "") == "task") and has("id") and has("quota"))
      | "\(.id) \(.quota)"
    ) // empty' "${BLUEPRINT_PATH}"); then
    error "failed to extract task quotas from blueprint via jq"
    exit 1
  fi
  if [[ ${#quota_pairs[@]} -eq 0 ]]; then
    if ! mapfile -t quota_pairs < <(jq -r '(.. | objects | select(has("id") and has("quota")) | "\(.id) \(.quota)")' "${BLUEPRINT_PATH}"); then
      error "failed to extract task quotas from blueprint via jq"
      exit 1
    fi
  fi
else
  if ! command -v node >/dev/null 2>&1; then
    error "neither jq nor node is available to parse blueprint"
    exit 1
  fi
  if ! mapfile -t quota_pairs < <(node -e '
    const fs = require("fs");
    const path = process.argv[1];
    const data = JSON.parse(fs.readFileSync(path, "utf8"));
    function *walk(value) {
      if (value && typeof value === "object") {
        if (Object.prototype.hasOwnProperty.call(value, "id") && Object.prototype.hasOwnProperty.call(value, "quota")) {
          yield [value.id, value.quota];
        }
        for (const key of Object.keys(value)) {
          yield* walk(value[key]);
        }
      }
    }
    const seen = new Set();
    for (const [id, quota] of walk(data)) {
      if (!seen.has(id)) {
        console.log(`${id} ${quota}`);
        seen.add(id);
      }
    }
  ' "${BLUEPRINT_PATH}"); then
    error "failed to extract task quotas from blueprint via node"
    exit 1
  fi
fi

if [[ ${#quota_pairs[@]} -eq 0 ]]; then
  error "no task quotas discovered in blueprint ${BLUEPRINT_PATH}"
  exit 1
fi

declare -A EXPECTED=()
declare -a TASK_IDS=()
for entry in "${quota_pairs[@]}"; do
  [[ -z "${entry}" ]] && continue
  read -r task_id quota_value <<< "${entry}"
  if [[ -z "${task_id:-}" || -z "${quota_value:-}" ]]; then
    continue
  fi
  if [[ -n "${EXPECTED[$task_id]+x}" ]]; then
    continue
  fi
  EXPECTED["$task_id"]="${quota_value}"
  TASK_IDS+=("${task_id}")
done

if [[ ${#TASK_IDS[@]} -eq 0 ]]; then
  error "no task quotas discovered in blueprint ${BLUEPRINT_PATH}"
  exit 1
fi

relative_blueprint="${BLUEPRINT_PATH}"
if [[ "${relative_blueprint}" == "${ROOT_DIR}/"* ]]; then
  relative_blueprint="${relative_blueprint#${ROOT_DIR}/}"
fi

status=0

for locale in "${LOCALES[@]}"; do
  locale_dir="${content_root}/${locale}"
  if [[ ! -d "${locale_dir}" ]]; then
    error "locale directory not found: ${locale_dir}"
    exit 1
  fi

  declare -A COUNTS=()
  while IFS= read -r -d '' file; do
    task_id="$(basename "$(dirname "${file}")")"
    COUNTS["${task_id}"]=$(( ${COUNTS["${task_id}"]:-0} + 1 ))
  done < <(find "${locale_dir}" -type f -name '*.json' -print0)

  report_file="${LOG_DIR}/quotas_${locale}.txt"
  REPORT_FILE="${report_file}"
  : > "${REPORT_FILE}"
  log_line "[quotas] locale=${locale} blueprint=${relative_blueprint} strict=$([[ ${strict_mode} -eq 1 ]] && echo true || echo false)"
  for task_id in "${TASK_IDS[@]}"; do
    expected="${EXPECTED["$task_id"]}"
    actual="${COUNTS["$task_id"]:-0}"
    if [[ "${actual}" == "${expected}" ]]; then
      log_line "[quota:${locale}] ${task_id} expected=${expected} got=${actual}"
    else
      log_line "[quota:${locale}] ${task_id} expected=${expected} got=${actual} status=MISMATCH"
      status=1
    fi
  done

  for task_id in "${!COUNTS[@]}"; do
    if [[ -z "${EXPECTED[$task_id]+x}" ]]; then
      log_line "[quota:${locale}] EXTRA task not in blueprint: ${task_id} count=${COUNTS["$task_id"]}"
      if [[ ${strict_mode} -eq 1 ]]; then
        status=1
      fi
    fi
  done

  unset REPORT_FILE
  unset COUNTS

done

if [[ ${status} -eq 0 ]]; then
  echo "[quotas] OK: all quotas satisfied for locales: ${LOCALES[*]}"
else
  if [[ ${strict_mode} -eq 1 ]]; then
    echo "[quotas] ERROR: quota mismatches detected (strict mode)" >&2
  else
    echo "[quotas] ERROR: quota mismatches detected" >&2
  fi
fi

exit ${status}
