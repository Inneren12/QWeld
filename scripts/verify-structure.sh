#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. && pwd)"

log() {
    local status="$1"
    local target="$2"
    printf '[verify] %s: %s\n' "$status" "$target"
}

declare -a REQUIRED_PATHS=(
    "app-android"
    "core-model"
    "core-data"
    "core-domain"
    "feature-exam"
    "feature-practice"
    "feature-auth"
    "content/blueprints"
    "content/questions"
    "schemas"
    "tools/generator-python"
    "docs/standards"
    "scripts"
    "scripts/tests"
    ".github/workflows"
    ".github/workflows/ci-meta.yml"
    ".github/ISSUE_TEMPLATE"
    ".editorconfig"
    ".gitignore"
    "LICENSE"
    "CODE_OF_CONDUCT.md"
    "CONTRIBUTING.md"
    ".github/pull_request_template.md"
    ".github/ISSUE_TEMPLATE/epic.md"
    ".github/ISSUE_TEMPLATE/task.md"
    "scripts/bootstrap.sh"
    "scripts/verify-structure.sh"
    "scripts/tests/test_verify.sh"
)

missing=0

for path in "${REQUIRED_PATHS[@]}"; do
    target="${ROOT_DIR}/${path}"
    if [[ -d "${target}" || -f "${target}" ]]; then
        log "OK" "${path}"
    else
        log "MISSING" "${path}"
        missing=1
    fi
done

exit ${missing}
