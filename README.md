# QWeld

## Structure & scripts

- Monorepo modules: `app-android`, `core-model`, `core-data`, `core-domain`, feature modules (`feature-exam`, `feature-practice`, `feature-auth`).
- Content and schema assets live in `content/blueprints`, `content/questions`, and `schemas`.
- Tooling resides in `tools/generator-python` and shared standards in `docs/standards`.
- Bootstrap with `scripts/bootstrap.sh` and validate with `scripts/verify-structure.sh` or `scripts/tests/test_verify.sh`.
- Run `bash scripts/validate-blueprint.sh` to lint blueprints against the JSON schema and quota totals.

## Content tooling (F4)

Use the combined fixer to populate missing `familyId` values and enforce the Russian terminology glossary:

```bash
# Dry-run (default behaviour)
bash scripts/content-fix.sh

# Apply changes in-place
bash scripts/content-fix.sh --apply
```

The script installs Poetry dependencies, runs `qw_fix_familyid.py`, and executes the RU terminology linter `qw_ru_lint.py`, writing a detailed report to `logs/ru_lint.txt` with unified diffs stored under `logs/diffs/ru_lint/`.

## Policy v1.0 & Blueprint
- Policy: see `docs/content-policy.md` (version 1.0) and blueprint rules in `docs/blueprint-rules.md`.
- Active blueprint: `content/blueprints/welder_ip_sk_202404.json` (blueprintVersion 1.0.0, policyVersion 1.0).
- Run validators locally with `bash scripts/validate-blueprint.sh` and `bash scripts/validate-questions.sh`; both scripts emit logs in `logs/` used by CI (`.github/workflows/content-validators.yml`).

## Question quotas & distribution banks
- Verify the per-task quotas defined in the active blueprint with `bash scripts/check-quotas.sh`; the script writes locale-specific reports to `logs/quotas_en.txt` and `logs/quotas_ru.txt` and fails if any totals diverge.
- Generate the flattened question banks consumed by the app with `bash scripts/build-questions-dist.sh`; the resulting bundles are stored under `dist/questions/{en,ru}/bank.v1.json` and the run metadata is logged in `logs/build_dist_{en,ru}.txt`.
- CI publishes the quota logs and the dist outputs as artifacts via `.github/workflows/content-validators.yml` (`content-validation-logs` and `questions-dist`).

## Explanations schema & how to validate
- Schema: `schemas/explanation.schema.json` defines the required structure for explanation articles (metadata, steps, incorrect choices, and optional references/media blocks).
- Validate locally with `bash scripts/validate-explanations.sh`; the script will emit `logs/validate-explanations.txt` mirroring CI output and will fail if the linked question JSON is missing.
- CI runs `scripts/validate-explanations.sh` alongside the question and blueprint checks via `.github/workflows/content-validators.yml`.

## Android app skeleton

### Build & Locales (EN/RU)
- Prerequisites: Java 21 and Android SDK configured locally (Android Studio Giraffe+ recommended).
- Clone the repo, then run `./gradlew spotlessCheck detekt test assembleDebug` to format-check, lint, test, and build the debug APK.
- Install the generated `app-android/build/outputs/apk/debug/app-android-debug.apk` onto a device/emulator running API 24+.
- Switch the device system language between English and Russian to see "Hello QWeld" / "Привет, QWeld" on the main screen.
- Launching the app logs startup information via Timber using the unified format (`[app] start ...` and `[ui] screen=Main ...`).

### CI
- GitHub Actions workflow `.github/workflows/android.yml` provisions the Android SDK, runs `spotlessCheck detekt test assembleDebug --no-daemon --stacktrace`, and uploads the debug APK artifact on every push/PR.

### F5-A assets & nav
- Place the flattened exam banks under `app-android/src/main/assets/questions/{en,ru}/bank.v1.json`; copy them from `dist/questions/{en,ru}/bank.v1.json` after running the dist builder.
- The Android app boots directly into the exam mode chooser powered by `:feature-exam`; it loads the localized bank on startup and shows a snackbar/toast if the asset is missing.

### F5-D review & explain
- Complete an exam attempt to unlock the Review screen, which lists every answered item with your selection, the correct choice, and the quick rationale from the bank.
- Tapping **Explain** opens a bottom sheet that pulls structured content from `app-android/src/main/assets/explanations/<locale>/<taskId>/<id>__explain_<locale>.json`; the sheet falls back to the quick rationale if the asset is missing.
- Run `./gradlew :feature-exam:test` to verify the asset repository parsing logic before shipping.

### Developer tooling
- Configure Git hooks so Spotless runs automatically before each commit: `git config core.hooksPath .githooks`.
- Copy `local.properties.sample` to `local.properties` and update `sdk.dir` with your Android SDK path.

### F7 Firebase Auth: setup & debug
- Debug builds include Firebase Authentication with guest, Google, and email/password flows.
- Place the debug `google-services.json` under `app-android/src/debug/` (kept out of source control) to enable Google Sign-In.
- Launching the app now opens the auth flow first; successful sign-in or linking returns to the exam navigator and logs `[auth_signin]`, `[auth_link]`, and `[auth_error]` markers via Timber.

### Exam assembly (F3)
Exam assembly (F3): deterministic seed, anti-cluster, choice balance, weighted PRACTICE.

- Default config: halfLifeCorrect=2.0, noveltyBoost=2.0, minWeight=0.05, maxWeight=4.0, freshDays=14, antiClusterSwaps=10, allowFallbackToEN=false (always false for IP Mock).
- Run the deterministic suite with `./gradlew :core-domain:test`.

### F6-A DB & Stats
- `:core-data` now ships a Room v1 schema (`AttemptEntity`, `AnswerEntity`) with DAOs, repositories, and a `UserStatsRepositoryRoom` implementation powering F3 weighted selection.
- User preferences live in `UserPrefsDataStore` (practice size default 20, EN fallback opt-in) with flows + edit helpers.
- Run `./gradlew :core-data:test` to verify DAO CRUD and stats aggregation behaviour end-to-end.

### F6-B integration
- `ExamViewModel` persists attempts/answers through `AttemptsRepository` and `AnswersRepository`, logging lifecycle markers for analytics.
- Finishing updates store duration, score, and IP Mock pass thresholds while PRACTICE assembly consumes Room-backed stats.
- Run `./gradlew :feature-exam:test` and `./gradlew :app-android:assembleDebug` to validate the flow.

### F6-C: Export attempt JSON
- Use **Export JSON** from the Result or Review screens to launch the system "Save as…" dialog (`QWeld_Attempt_<id>.json`).
- The exported payload follows `qweld.attempt.v1` with nested `qweld.answer.v1` entries, per-block/per-task summaries, and metadata stamped with the app version and export timestamp.
- The exporter reuses Room repositories, logs `[export_attempt]` / `[export_attempt_error]` markers, and is covered by `AttemptExporterTest` (`./gradlew :feature-exam:test`).

