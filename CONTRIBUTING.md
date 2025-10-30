# Contributing to QWeld

Thank you for your interest in contributing! This repository is structured as a
monorepo containing Android applications, domain logic, and supporting tools.

## Getting Started

1. Clone the repository and checkout the `feature/bootstrap` branch strategy.
2. Run `scripts/bootstrap.sh` to ensure the directory structure exists.
3. Execute `scripts/verify-structure.sh` to validate the bootstrap state before
   submitting changes.

## Development Workflow

- Follow the Kotlin coding conventions and keep modules independent.
- Ensure new scripts are POSIX-compatible Bash with `set -euo pipefail`.
- Add automated checks to the CI workflows when introducing new tooling.

## Pull Requests

- Link related issues and provide context in the pull request description.
- Keep commits focused and descriptive.
- Run the verification scripts locally before pushing.

## Community Standards

Please adhere to our [Code of Conduct](CODE_OF_CONDUCT.md) in all interactions.
