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

## Per-task banks: when and how to use
- Run `node scripts/build-questions-dist.mjs` (or `bash scripts/build-questions-dist.sh` if Node.js is unavailable) to produce both the unified `bank.v1.json` and the per-task bundles in `dist/questions/<locale>/tasks/<taskId>.json`.
- Copy the generated `tasks/` directories alongside the unified bank into `app-android/src/main/assets/questions/<locale>/` before assembling the app; the repository loads per-task bundles first, then falls back to the monolithic bank, and finally to raw question files for dev scenarios.
- At runtime the repository lazily loads only the requested tasks (practice mode fetches 1–2 files, IP Mock primes all 15 once) and keeps the most recent six tasks in an in-memory LRU cache while logging the data source and timing via Timber.

## Content Info & dist/index.json
- `node scripts/build-questions-dist.mjs` now also emits `dist/questions/index.json` summarising locale totals, per-task counts, and SHA-256 hashes for the generated banks (see `[dist_index] written=…` in the log).
- Copy the resulting `index.json` into `app-android/src/main/assets/questions/index.json` so the Android app can surface the totals inside **Settings → Content info**.
- On device, the Settings screen shows the locale totals plus an A–D / 1–15 matrix and exposes a **Copy index JSON** button that places the raw document onto the clipboard for quick diagnostics.

## Pre-warm per-task
- IP Mock mode now fires `PrewarmUseCase` from `ExamViewModel.startPrewarmForIpMock`, priming all requested tasks with a lightweight progress bar on the Mode screen before navigation.
- The use case streams task-level progress via `onProgress`, caps parallelism with `Dispatchers.IO.limitedParallelism(3)`, and enforces a 2s timeout per asset while logging `[prewarm_start]`, `[prewarm_step]`, and `[prewarm_done]` markers.
- Missing task bundles trigger a graceful fallback to the unified bank (`bank.v1.json`) without blocking the start button—progress still reaches 100% and the bank is warmed into memory for the upcoming attempt.

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

#### CI dist summary in PR comments
- Workflow `.github/workflows/dist-summary.yml` builds the flattened question banks, validates them when `scripts/validate-dist-banks.mjs` is present, and posts a Markdown summary comment to pull requests with locale totals and per-task counts.
- Reproduce the comment locally with `node scripts/build-questions-dist.mjs && node scripts/print-dist-summary.mjs`.

### F5-A assets & nav
- Place the flattened exam banks under `app-android/src/main/assets/questions/{en,ru}/bank.v1.json`; copy them from `dist/questions/{en,ru}/bank.v1.json` after running the dist builder.
- The Android app boots directly into the exam mode chooser powered by `:feature-exam`; it loads the localized bank on startup and shows a snackbar/toast if the asset is missing.

### F5-D review & explain
- Complete an exam attempt to unlock the Review screen, which lists every answered item with your selection, the correct choice, and the quick rationale from the bank.
- Tapping **Explain** opens a bottom sheet that pulls structured content from `app-android/src/main/assets/explanations/<locale>/<taskId>/<id>__explain_<locale>.json`; the sheet falls back to the quick rationale if the asset is missing.
- Run `./gradlew :feature-exam:test` to verify the asset repository parsing logic before shipping.

### Review filters
- Toggle **Wrong only**, **Flagged only**, or **By task** chips to refine the Review list; chips call into `ReviewViewModel` which recomputes the visible sections via a `StateFlow`.
- Counters above the list display total, wrong, and flagged question counts, updating instantly as filters change.
- Enabling **By task** groups questions under their `taskId` headers (e.g. `A-1`, `B-4`) while preserving the running index used by the cards.
- Every toggle logs `[review_filter]` analytics with the active flags and totals so dashboards can track how the feature is used.

### Developer tooling
- Configure Git hooks so Spotless runs automatically before each commit: `git config core.hooksPath .githooks`.
- Copy `local.properties.sample` to `local.properties` and update `sdk.dir` with your Android SDK path.

### F7 Firebase Auth: setup & debug
- Debug builds include Firebase Authentication with guest, Google, and email/password flows.
- Place the debug `google-services.json` under `app-android/src/debug/` (kept out of source control) to enable Google Sign-In.
- Launching the app now opens the auth flow first; successful sign-in or linking returns to the exam navigator and logs `[auth_signin]`, `[auth_link]`, and `[auth_error]` markers via Timber.

### Firebase Analytics & Crashlytics
- Analytics and crash reporting are wired through the app module with a shared feature flag: `BuildConfig.ENABLE_ANALYTICS`.
- Debug builds disable both collectors by default, so no events or crashes leave the device unless you opt in.
- To re-enable telemetry in debug (for DebugView, Crashlytics verification, etc.), pass `-PenableAnalyticsDebug=true` when invoking Gradle or add `enableAnalyticsDebug=true` to `local.properties` before running `./gradlew :app-android:assembleDebug`.

### Analytics funnel & deficit
- Use Firebase DebugView by running `adb shell setprop debug.firebase.analytics.app com.qweld.app` before launching a debug build (for example, via `./gradlew :app-android:assembleDebug`).
- Users can opt out via **Settings → Analytics**; when disabled, Firebase dispatch is skipped while Timber records `[analytics] skipped=true reason=optout event=<name>`.

### Settings: privacy & tools
- Open **Settings** from the top app bar overflow to review analytics and practice preferences alongside maintenance tools.
- Privacy includes a single toggle that persists to `UserPrefsDataStore`; disabling analytics keeps Timber logs but stops Firebase event delivery until re-enabled.
- Practice exposes a 5–50 slider/number field for the default question count plus an RU→EN fallback switch (practice only), both stored in the same DataStore.
- Tools provide one-tap log export (SAF), a combined "Clear local attempts" action (wipes Room attempts/answers), and "Clear per-task cache" for the asset repository—each posts a confirmation snackbar and logs `[settings_action]` markers with `result=ok|error`.
- Tools now include an **Accessibility** sub-section with persistent toggles for haptics (enabled by default) and submit click sounds (opt-in) backed by `UserPrefsDataStore`.

#### Clear per-locale cache
- Use **Clear cache EN** / **Clear cache RU** in Settings → Tools to drop only the per-task bundles for the selected locale.
- Successful actions show a "Cleared <locale> cache." snackbar and log `[settings_action] action=clear_cache locale=en|ru result=ok` so telemetry can confirm the flow.

### Haptics & sounds
- Exam submissions fire a soft `HapticFeedbackType.LongPress` when the **Haptics feedback** toggle is on, logging `[ux_feedback] haptics=true|false sounds=true|false event=answer_submit` alongside analytics.
- An optional system click (`View.playSoundEffect(SoundEffectConstants.CLICK)`) supplements the vibration when **Click sound on submit** is enabled.
- Both switches persist via `UserPrefsDataStore` (`hapticsEnabled` defaults to `true`, `soundsEnabled` defaults to `false`) and are consumed by `ExamScreen` before calling `submitAnswer`.

### F7-B: Sign out & guards
- Top app bar exposes an account menu with **Sync** and **Sign out** actions (Sign out appears only for authenticated users).
- Sign out clears the Firebase session via `AuthService.signOut()` and returns to the auth flow while logging `[auth_signout]`.
- `RequireUser` / `RequireNonAnonymous` composables guard routes; the `/sync` placeholder requires a non-anonymous user and logs `[auth_guard]` with the denial reason.

### Exam assembly (F3)
Exam assembly (F3): deterministic seed, anti-cluster, choice balance, weighted PRACTICE.

- Default config: halfLifeCorrect=2.0, noveltyBoost=2.0, minWeight=0.05, maxWeight=4.0, freshDays=14, antiClusterSwaps=10, allowFallbackToEN=false (always false for IP Mock).
- Run the deterministic suite with `./gradlew :core-domain:test`.

### F6-A DB & Stats
- `:core-data` now ships a Room v1 schema (`AttemptEntity`, `AnswerEntity`) with DAOs, repositories, and a `UserStatsRepositoryRoom` implementation powering F3 weighted selection.
- User preferences live in `UserPrefsDataStore` (practice size default 20, EN fallback opt-in, haptics on by default, submit sound opt-in) with flows + edit helpers.
- Run `./gradlew :core-data:test` to verify DAO CRUD and stats aggregation behaviour end-to-end.

### F6-B integration
- `ExamViewModel` persists attempts/answers through `AttemptsRepository` and `AnswersRepository`, logging lifecycle markers for analytics.
- Finishing updates store duration, score, and IP Mock pass thresholds while PRACTICE assembly consumes Room-backed stats.
- Run `./gradlew :feature-exam:test` and `./gradlew :app-android:assembleDebug` to validate the flow.

### Pause/Resume
- On app launch, **Mode** and **Exam** screens query Room for the latest unfinished attempt and surface a "Resume" dialog with Continue/Discard actions.
- Resuming replays the original seed through `ExamAssembler`, merges saved `AnswerEntity` rows, restores the first unanswered index, and restarts the IP Mock timer with the remaining four-hour budget; hitting zero auto-submits and stores the timed-out score.
- Locale mismatches offer a keep/switch toggle (`Switch to <device locale> & rebuild`), logging `[resume_mismatch]` and rerunning assembly in the chosen language before navigation.
- Discarding marks the attempt as aborted (null score) and logs `[resume_discard]`, allowing a fresh start without manual cleanup.

### Confirm Exit
- IP Mock attempts now intercept system Back/Up and show a confirmation dialog so accidental taps do not end the session.
- Choosing **Continue** keeps you in the exam; **Exit** returns to the Mode screen while preserving progress for a later resume.
- Every dialog appearance logs `[confirm_exit] shown=true mode=IPMock`, and decisions emit `[confirm_exit] choice=continue|exit` for analytics parity.

### F6-C: Export attempt JSON
- Use **Export JSON** from the Result or Review screens to launch the system "Save as…" dialog (`QWeld_Attempt_<id>.json`).
- The exported payload follows `qweld.attempt.v1` with nested `qweld.answer.v1` entries, per-block/per-task summaries, and metadata stamped with the app version and export timestamp.
- The exporter reuses Room repositories, logs `[export_attempt]` / `[export_attempt_error]` markers, and is covered by `AttemptExporterTest` (`./gradlew :feature-exam:test`).

### F6-D: In-app log export
- **Export logs** is available from the Result screen and the account menu; it opens the Storage Access Framework so you can save the Timber buffer as TXT or JSON (`QWeld_Logs_<ts>.<ext>`).
- The file is created wherever you point the SAF picker (e.g. Downloads, Drive, or device storage); open the Files app and navigate to that location to retrieve it.
- TXT rows follow `[ts] [tag] message | attrs={...}` with an optional `| error=Type:reason` suffix, while the JSON variant wraps the same entries inside a `qweld.logs.v1` document.

