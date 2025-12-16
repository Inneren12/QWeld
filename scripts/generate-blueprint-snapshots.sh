#!/usr/bin/env bash
set -euo pipefail

# Blueprint/Manifest Snapshot Generator
# Generates deterministic summaries of blueprint + manifest structure
# Used for regression testing to detect unintended content changes

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

BLUEPRINTS_DIR="${ROOT_DIR}/content/blueprints"
MANIFEST_FILE="${ROOT_DIR}/app-android/src/main/assets/questions/index.json"
SNAPSHOTS_DIR="${ROOT_DIR}/tests/snapshots"

MODE="${1:-verify}"

usage() {
  cat <<USAGE
Usage: $0 [MODE]
  MODE:
    verify   - Compare current state against snapshots (default, exits 1 on mismatch)
    update   - Update snapshot files with current state
    show     - Display current snapshot without comparing or updating
USAGE
}

case "${MODE}" in
  verify|update|show)
    ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    echo "ERROR: Unknown mode '${MODE}'" >&2
    usage
    exit 1
    ;;
esac

mkdir -p "${SNAPSHOTS_DIR}"

echo "[snapshot] mode=${MODE}"
echo "[snapshot] blueprints_dir=${BLUEPRINTS_DIR}"
echo "[snapshot] manifest=${MANIFEST_FILE}"
echo "[snapshot] snapshots_dir=${SNAPSHOTS_DIR}"
echo ""

# Function to generate deterministic summary for a blueprint
generate_blueprint_snapshot() {
  local blueprint_file="$1"
  local blueprint_name
  blueprint_name=$(basename "${blueprint_file}" .json)

  if [[ ! -f "${blueprint_file}" ]]; then
    echo "ERROR: Blueprint file not found: ${blueprint_file}" >&2
    return 1
  fi

  # Extract metadata
  local bp_id bp_version policy_version question_count
  bp_id=$(jq -r '.id // "unknown"' "${blueprint_file}")
  bp_version=$(jq -r '.blueprintVersion // "unknown"' "${blueprint_file}")
  policy_version=$(jq -r '.policyVersion // "unknown"' "${blueprint_file}")
  question_count=$(jq -r '.questionCount // 0' "${blueprint_file}")

  # Calculate total quota
  local total_quota
  total_quota=$(jq '[.blocks[].tasks[].quota] | add' "${blueprint_file}")

  # Print snapshot to stdout
  cat <<SNAPSHOT
# Blueprint Snapshot: ${blueprint_name}
# Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

[metadata]
id=${bp_id}
blueprintVersion=${bp_version}
policyVersion=${policy_version}
questionCount=${question_count}

[blocks]
$(jq -r '.blocks[] | "block=\(.id) # \(.title)"' "${blueprint_file}" | sort)

[tasks]
$(jq -r '.blocks[].tasks[] | "task=\(.id) quota=\(.quota) # \(.title)"' "${blueprint_file}" | sort)

[summary]
totalTasks=$(jq '[.blocks[].tasks[]] | length' "${blueprint_file}")
totalQuota=${total_quota}
SNAPSHOT
}

# Function to generate manifest snapshot
generate_manifest_snapshot() {
  if [[ ! -f "${MANIFEST_FILE}" ]]; then
    echo "ERROR: Manifest file not found: ${MANIFEST_FILE}" >&2
    return 1
  fi

  # Extract schema
  local schema
  schema=$(jq -r '.schema // "unknown"' "${MANIFEST_FILE}")

  # Print header
  cat <<HEADER
# Manifest Snapshot
# Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

[metadata]
schema=${schema}

HEADER

  # Extract per-locale totals and task counts (sorted)
  jq -r '.locales | keys[]' "${MANIFEST_FILE}" | sort | while read -r locale; do
    local total
    total=$(jq -r ".locales.\"${locale}\".total" "${MANIFEST_FILE}")

    echo "[locale:${locale}]"
    echo "total=${total}"
    echo ""

    echo "[tasks:${locale}]"
    jq -r ".locales.\"${locale}\".tasks | to_entries[] | \"\(.key)=\(.value)\"" "${MANIFEST_FILE}" | sort
    echo ""
  done
}

# Process all blueprints
mapfile -t blueprint_files < <(find "${BLUEPRINTS_DIR}" -type f -name '*.json' | sort)

if [[ ${#blueprint_files[@]} -eq 0 ]]; then
  echo "ERROR: No blueprint files found in ${BLUEPRINTS_DIR}" >&2
  exit 1
fi

status=0

for blueprint_file in "${blueprint_files[@]}"; do
  blueprint_name=$(basename "${blueprint_file}" .json)
  snapshot_file="${SNAPSHOTS_DIR}/blueprint_${blueprint_name}.snapshot"

  echo "[snapshot] processing blueprint: ${blueprint_name}"

  current_snapshot=$(generate_blueprint_snapshot "${blueprint_file}")

  case "${MODE}" in
    show)
      echo ""
      echo "${current_snapshot}"
      echo ""
      ;;
    update)
      echo "${current_snapshot}" > "${snapshot_file}"
      echo "[snapshot] updated: ${snapshot_file}"
      ;;
    verify)
      if [[ ! -f "${snapshot_file}" ]]; then
        echo "[snapshot] ERROR: Snapshot file missing: ${snapshot_file}" >&2
        echo "[snapshot] Run with 'update' mode to create it" >&2
        status=1
        continue
      fi

      existing_snapshot=$(cat "${snapshot_file}")

      # Compare snapshots (ignoring timestamp lines)
      current_normalized=$(echo "${current_snapshot}" | grep -v "^# Generated:")
      existing_normalized=$(echo "${existing_snapshot}" | grep -v "^# Generated:")

      if [[ "${current_normalized}" != "${existing_normalized}" ]]; then
        echo "[snapshot] ERROR: Blueprint snapshot mismatch for ${blueprint_name}" >&2
        echo "[snapshot] Expected: ${snapshot_file}" >&2
        echo "[snapshot] Run 'diff ${snapshot_file} <(./scripts/generate-blueprint-snapshots.sh show)' to see changes" >&2
        echo "[snapshot] Run './scripts/generate-blueprint-snapshots.sh update' to update snapshots intentionally" >&2
        status=1
      else
        echo "[snapshot] OK: ${blueprint_name}"
      fi
      ;;
  esac
done

# Process manifest
echo ""
echo "[snapshot] processing manifest: index.json"

manifest_snapshot=$(generate_manifest_snapshot)
manifest_snapshot_file="${SNAPSHOTS_DIR}/manifest.snapshot"

case "${MODE}" in
  show)
    echo ""
    echo "${manifest_snapshot}"
    echo ""
    ;;
  update)
    echo "${manifest_snapshot}" > "${manifest_snapshot_file}"
    echo "[snapshot] updated: ${manifest_snapshot_file}"
    ;;
  verify)
    if [[ ! -f "${manifest_snapshot_file}" ]]; then
      echo "[snapshot] ERROR: Manifest snapshot file missing: ${manifest_snapshot_file}" >&2
      echo "[snapshot] Run with 'update' mode to create it" >&2
      status=1
    else
      existing_manifest=$(cat "${manifest_snapshot_file}")

      # Compare snapshots (ignoring timestamp lines)
      current_normalized=$(echo "${manifest_snapshot}" | grep -v "^# Generated:")
      existing_normalized=$(echo "${existing_manifest}" | grep -v "^# Generated:")

      if [[ "${current_normalized}" != "${existing_normalized}" ]]; then
        echo "[snapshot] ERROR: Manifest snapshot mismatch" >&2
        echo "[snapshot] Expected: ${manifest_snapshot_file}" >&2
        echo "[snapshot] Run 'diff ${manifest_snapshot_file} <(./scripts/generate-blueprint-snapshots.sh show | tail -n +$(grep -n \"# Manifest Snapshot\" <(./scripts/generate-blueprint-snapshots.sh show) | cut -d: -f1))' to see changes" >&2
        echo "[snapshot] Run './scripts/generate-blueprint-snapshots.sh update' to update snapshots intentionally" >&2
        status=1
      else
        echo "[snapshot] OK: manifest"
      fi
    fi
    ;;
esac

echo ""
if [[ ${status} -eq 0 ]]; then
  case "${MODE}" in
    verify)
      echo "[snapshot] SUCCESS: All snapshots match"
      ;;
    update)
      echo "[snapshot] SUCCESS: All snapshots updated"
      ;;
    show)
      echo "[snapshot] SUCCESS: Snapshots displayed"
      ;;
  esac
else
  echo "[snapshot] FAILURE: Snapshot mismatches detected" >&2
fi

exit ${status}
