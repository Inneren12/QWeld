#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"
mkdir -p "${LOG_DIR}" 

BLUEPRINT_REL="content/blueprints/welder_ip_sk_202404.json"
PROFILE_PATH=""
LOCALES_STR="en,ru"
MODE="min"
MIN_MULTIPLE=1
ALLOW_EXTRA=true
CHANGED_ONLY=false

usage() {
  cat <<USAGE
Usage: $0 [options]
  --changed-only             Only check tasks affected by modified questions
  --profile <path>           Exam profile file (optional, may override blueprint)
  --blueprint <path>         Blueprint JSON path
  --locales <list>           Comma-separated locales (default: en,ru)
  --mode <mode>              Validation mode (min or exact, default: min)
  --min-multiple <n>         Multiply blueprint quotas by this factor (default: 1)
  --allow-extra              Allow counts greater than required (default)
  --no-allow-extra           Fail if counts exceed requirements
  -h, --help                 Show this message
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --changed-only)
      CHANGED_ONLY=true
      shift
      ;;
    --profile)
      PROFILE_PATH="$2"
      shift 2
      ;;
    --blueprint)
      BLUEPRINT_REL="$2"
      shift 2
      ;;
    --locales)
      LOCALES_STR="$2"
      shift 2
      ;;
    --mode)
      MODE="$2"
      shift 2
      ;;
    --min-multiple)
      MIN_MULTIPLE="$2"
      shift 2
      ;;
    --allow-extra)
      ALLOW_EXTRA=true
      shift
      ;;
    --no-allow-extra)
      ALLOW_EXTRA=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --*)
      echo "[quotas] ERROR: unknown option: $1" >&2
      exit 1
      ;;
    *)
      echo "[quotas] ERROR: unexpected positional argument: $1" >&2
      exit 1
      ;;
  esac
done

LOCALES_STR="${LOCALES_STR// /}"
IFS=',' read -r -a LOCALES <<< "${LOCALES_STR}" || true
if [[ ${#LOCALES[@]} -eq 0 ]]; then
  echo "[quotas] ERROR: no locales provided" >&2
  exit 1
fi

if [[ ! "${MIN_MULTIPLE}" =~ ^[0-9]+$ ]]; then
  echo "[quotas] ERROR: --min-multiple must be a non-negative integer" >&2
  exit 1
fi

case "${MODE}" in
  min|exact)
    ;;
  *)
    echo "[quotas] ERROR: unsupported mode '${MODE}'" >&2
    exit 1
    ;;
 esac

BLUEPRINT_PATH="${BLUEPRINT_REL}"
if [[ ! -f "${BLUEPRINT_PATH}" ]]; then
  if [[ -f "${ROOT_DIR}/${BLUEPRINT_REL}" ]]; then
    BLUEPRINT_PATH="${ROOT_DIR}/${BLUEPRINT_REL}"
  fi
fi

if [[ -n "${PROFILE_PATH}" && -f "${PROFILE_PATH}" ]]; then
  blueprint_candidate=$(jq -r 'try .blueprintPath // .blueprint // .blueprintId // empty' "${PROFILE_PATH}")
  if [[ -n "${blueprint_candidate}" && "${blueprint_candidate}" != "null" ]]; then
    if [[ -f "${blueprint_candidate}" ]]; then
      BLUEPRINT_PATH="${blueprint_candidate}"
    elif [[ -f "${ROOT_DIR}/${blueprint_candidate}" ]]; then
      BLUEPRINT_PATH="${ROOT_DIR}/${blueprint_candidate}"
    elif [[ -f "${ROOT_DIR}/content/blueprints/${blueprint_candidate}.json" ]]; then
      BLUEPRINT_PATH="${ROOT_DIR}/content/blueprints/${blueprint_candidate}.json"
    fi
  fi
elif [[ -n "${PROFILE_PATH}" && ! -f "${PROFILE_PATH}" ]]; then
  echo "[quotas] WARNING: profile not found: ${PROFILE_PATH}" >&2
fi

if [[ ! -f "${BLUEPRINT_PATH}" ]]; then
  echo "[quotas] ERROR: blueprint file not found: ${BLUEPRINT_PATH}" >&2
  exit 1
fi

mapfile -t ALL_TASKS < <(jq -r '.blocks[].tasks[].id' "${BLUEPRINT_PATH}")

if [[ ${#ALL_TASKS[@]} -eq 0 ]]; then
  echo "[quotas] ERROR: no tasks defined in blueprint ${BLUEPRINT_PATH}" >&2
  exit 1
fi

declare -A QUOTAS=()
while IFS=$'\t' read -r task_id quota; do
  QUOTAS["${task_id}"]="${quota}"
done < <(jq -r '.blocks[].tasks[] | "\(.id)\t\(.quota)"' "${BLUEPRINT_PATH}")

status=0

declare -A CHANGED_TASKS=()
if ${CHANGED_ONLY}; then
  base_commit="${BASE_SHA:-${GITHUB_BASE_SHA:-}}"
  head_commit="${HEAD_SHA:-${GITHUB_SHA:-}}"
  if [[ -z "${base_commit:-}" || -z "${head_commit:-}" ]]; then
    if git rev-parse --verify origin/main >/dev/null 2>&1; then
      git fetch --no-tags --prune origin +refs/heads/main:refs/remotes/origin/main >/dev/null 2>&1 || true
      base_commit="$(git merge-base HEAD origin/main)"
      head_commit="$(git rev-parse HEAD)"
    else
      base_commit="$(git rev-parse HEAD)"
      head_commit="$(git rev-parse HEAD)"
    fi
  fi

  mapfile -t DIFF_PATHS < <({
    git diff --name-only --diff-filter=ACMRD "${base_commit}...${head_commit}"
    git diff --name-only --diff-filter=ACMRD
  } | sort -u)

  blueprint_changed=false
  for diff_path in "${DIFF_PATHS[@]}"; do
    if [[ "${diff_path}" == content/blueprints/* ]]; then
      blueprint_changed=true
      break
    fi
  done

  if ${blueprint_changed}; then
    echo "[quotas] blueprint changed; running full check"
    CHANGED_ONLY=false
  else
    for diff_path in "${DIFF_PATHS[@]}"; do
      if [[ "${diff_path}" =~ ^content/questions/([^/]+)/([^/]+)/[^/]+\.json$ ]]; then
        locale="${BASH_REMATCH[1]}"
        task_id="${BASH_REMATCH[2]}"
        CHANGED_TASKS["${locale}:${task_id}"]=1
      fi
    done
    changed_count=0
    for locale in "${LOCALES[@]}"; do
      for task in "${ALL_TASKS[@]}"; do
        key="${locale}:${task}"
        if [[ -n "${CHANGED_TASKS[$key]:-}" ]]; then
          ((changed_count+=1))
        fi
      done
    done
    if (( changed_count == 0 )); then
      echo "[quotas] no changed question tasks; skip"
      exit 0
    fi
  fi
fi

for locale in "${LOCALES[@]}"; do
  locale_dir="${ROOT_DIR}/content/questions/${locale}"
  if [[ ! -d "${locale_dir}" ]]; then
    echo "[quotas] ERROR: locale directory not found: ${locale_dir}" >&2
    exit 1
  fi

  locale_log="${LOG_DIR}/quotas_${locale}.txt"
  tmp_report="$(mktemp)"

  {
    echo "[quotas] locale=${locale}"
    if ${CHANGED_ONLY}; then
      tasks_to_check=()
      for task in "${ALL_TASKS[@]}"; do
        key="${locale}:${task}"
        if [[ -n "${CHANGED_TASKS[$key]:-}" ]]; then
          tasks_to_check+=("${task}")
        fi
      done
    else
      tasks_to_check=("${ALL_TASKS[@]}")
    fi

    if [[ ${#tasks_to_check[@]} -eq 0 ]]; then
      echo "[quotas] INFO: no tasks to check for locale=${locale}"
    else
      printf "%-6s %6s %6s %8s\n" "Task" "Need" "Have" "Missing"
      printf '%-6s %6s %6s %8s\n' '-----' '----' '----' '-------'

      for task in "${tasks_to_check[@]}"; do
        quota="${QUOTAS["${task}"]:-}"
        if [[ -z "${quota}" ]]; then
          echo "[quotas] ERROR: task ${task} missing in blueprint" >&2
          status=1
          printf "%-6s %6s %6s %8s\n" "${task}" "N/A" "N/A" "N/A"
          continue
        fi

        need=$(( quota * MIN_MULTIPLE ))
        task_dir="${locale_dir}/${task}"
        if [[ -d "${task_dir}" ]]; then
          have=$(find "${task_dir}" -maxdepth 1 -type f -name '*.json' -print | wc -l | tr -d ' ')
        else
          have=0
        fi
        missing=$(( need > have ? need - have : 0 ))

        if [[ "${MODE}" == "min" ]]; then
          if (( have < need )); then
            status=1
          elif [[ "${ALLOW_EXTRA}" != "true" ]] && (( have > need )); then
            status=1
          fi
        elif [[ "${MODE}" == "exact" ]]; then
          if (( have != need )); then
            status=1
          fi
        fi

        printf "%-6s %6d %6d %8d\n" "${task}" "${need}" "${have}" "${missing}"
      done
    fi
  } > "${tmp_report}"

  cat "${tmp_report}"
  cp "${tmp_report}" "${locale_log}"
  rm -f "${tmp_report}"
done

if [[ ${status} -ne 0 ]]; then
  echo "[quotas] ERROR: quota mismatches detected" >&2
else
  echo "[quotas] OK: all quotas satisfied"
fi

exit ${status}
