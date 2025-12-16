# Contributing to QWeld

Thank you for your interest in contributing! This repository is structured as a
monorepo containing Android applications, domain logic, and supporting tools.

## Getting Started

1. Clone the repository and checkout the `feature/bootstrap` branch strategy.
2. Run `scripts/bootstrap.sh` to ensure the directory structure exists.
3. Execute `scripts/verify-structure.sh` to validate the bootstrap state before
   submitting changes.

### Platform-Specific Configuration (Windows)

If you're developing on Windows and need to configure a custom temporary directory for sqlite-jdbc or other build tools:

- **Do NOT** add OS-specific paths (like `C:/b/gradle-tmp`) to the repo-level `gradle.properties`
- Instead, configure them in your **user-level** `gradle.properties`:
  - Location: `%USERPROFILE%\.gradle\gradle.properties`
  - Example:
    ```properties
    org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -Djava.io.tmpdir=C:/your/custom/tmpdir
    ```
- Alternatively, set environment variables:
  - `JAVA_TOOL_OPTIONS` or `GRADLE_OPTS` in your system environment

This keeps the repo configuration platform-agnostic and prevents CI failures on Linux/macOS runners.

## Development Workflow

- Follow the Kotlin coding conventions and keep modules independent.
- Ensure new scripts are POSIX-compatible Bash with `set -euo pipefail`.
- Add automated checks to the CI workflows when introducing new tooling.

## Content Development

When working on question content, blueprints, or explanations:

- Refer to [CONTENT_GUIDE.md](CONTENT_GUIDE.md) for content authoring guidelines
- Run validation scripts before committing:
  - `bash scripts/validate-questions.sh` - Validate question schema and rationales
  - `bash scripts/validate-blueprint.sh` - Validate blueprint structure
  - `bash scripts/check-quotas.sh` - Verify sufficient questions per task
  - `bash scripts/check-explanation-coverage.sh` - Check explanation coverage
  - `bash scripts/generate-blueprint-snapshots.sh verify` - Ensure no unintended structural changes

## Pre-Release Validation

Before releasing a new version:

- Follow the [Pre-Release Validation Checklist](docs/release_checks.md)
- Ensure all automated validators pass
- Perform recommended manual checks (sample questions, UI strings, explanations)
- Run snapshot tests to detect unintended blueprint/manifest changes

## Pull Requests

- Link related issues and provide context in the pull request description.
- Keep commits focused and descriptive.
- Run the verification scripts locally before pushing.

## Community Standards

Please adhere to our [Code of Conduct](CODE_OF_CONDUCT.md) in all interactions.
