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

## Android app skeleton

### Build & Locales (EN/RU)
- Prerequisites: Java 21 and Android SDK configured locally (Android Studio Giraffe+ recommended).
- Clone the repo, then run `./gradlew spotlessCheck detekt test assembleDebug` to format-check, lint, test, and build the debug APK.
- Install the generated `app-android/build/outputs/apk/debug/app-android-debug.apk` onto a device/emulator running API 24+.
- Switch the device system language between English and Russian to see "Hello QWeld" / "Привет, QWeld" on the main screen.
- Launching the app logs startup information via Timber using the unified format (`[app] start ...` and `[ui] screen=Main ...`).

### CI
- GitHub Actions workflow `.github/workflows/android.yml` runs the same Gradle tasks (`spotlessCheck detekt test assembleDebug`) on every push/PR and uploads the debug APK artifact.
