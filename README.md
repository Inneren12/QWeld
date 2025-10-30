# QWeld

## Structure & scripts

- Monorepo modules: `app-android`, `core-model`, `core-data`, `core-domain`, feature modules (`feature-exam`, `feature-practice`, `feature-auth`).
- Content and schema assets live in `content/blueprints`, `content/questions`, and `schemas`.
- Tooling resides in `tools/generator-python` and shared standards in `docs/standards`.
- Bootstrap with `scripts/bootstrap.sh` and validate with `scripts/verify-structure.sh` or `scripts/tests/test_verify.sh`.
- Run `bash scripts/validate-blueprint.sh` to lint blueprints against the JSON schema and quota totals.

## Policy v1.0 & Blueprint
- Policy: see `docs/content-policy.md` (version 1.0) and blueprint rules in `docs/blueprint-rules.md`.
- Active blueprint: `content/blueprints/welder_ip_sk_202404.json` (blueprintVersion 1.0.0, policyVersion 1.0).
- Run validators locally with `bash scripts/validate-blueprint.sh` and `bash scripts/validate-questions.sh`; both scripts emit logs in `logs/` used by CI (`.github/workflows/policy.yml`).
