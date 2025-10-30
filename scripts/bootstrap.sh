#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. && pwd)"

log() {
    local message="$1"
    printf '[bootstrap] %s\n' "$message"
}

declare -a DIRECTORIES=(
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
    "scripts"
    "scripts/tests"
    "docs/standards"
    ".github"
    ".github/ISSUE_TEMPLATE"
    ".github/workflows"
)

declare -A PLACEHOLDER_FILES=(
    ["app-android"]=".gitkeep"
    ["core-model"]=".gitkeep"
    ["core-data"]=".gitkeep"
    ["core-domain"]=".gitkeep"
    ["feature-exam"]=".gitkeep"
    ["feature-practice"]=".gitkeep"
    ["feature-auth"]=".gitkeep"
    ["content/blueprints"]=".gitkeep"
    ["content/questions"]=".gitkeep"
    ["schemas"]=".gitkeep"
    ["tools/generator-python"]=".gitkeep"
    ["docs/standards"]=".gitkeep"
)

for dir in "${DIRECTORIES[@]}"; do
    dir_path="${ROOT_DIR}/${dir}"
    if [[ -d "${dir_path}" ]]; then
        log "exists dir ${dir}"
    else
        mkdir -p "${dir_path}"
        log "created dir ${dir}"
    fi

    if [[ -n "${PLACEHOLDER_FILES[${dir}]:-}" ]]; then
        placeholder="${dir_path}/${PLACEHOLDER_FILES[${dir}]}"
        if [[ -f "${placeholder}" ]]; then
            log "exists file ${dir}/${PLACEHOLDER_FILES[${dir}]}"
        else
            : > "${placeholder}"
            log "created file ${dir}/${PLACEHOLDER_FILES[${dir}]}"
        fi
    fi
done

# Root-level configuration files
readonly FILES=(
    ".editorconfig"
    ".gitignore"
    "LICENSE"
    "CODE_OF_CONDUCT.md"
    "CONTRIBUTING.md"
    ".github/pull_request_template.md"
    ".github/ISSUE_TEMPLATE/epic.md"
    ".github/ISSUE_TEMPLATE/task.md"
    ".github/workflows/ci-meta.yml"
    "scripts/bootstrap.sh"
    "scripts/verify-structure.sh"
    "scripts/tests/test_verify.sh"
)

for file in "${FILES[@]}"; do
    path="${ROOT_DIR}/${file}"
    if [[ -f "${path}" ]]; then
        log "exists file ${file}"
    else
        log "missing file ${file}"
    fi
done
