# Project Overview

## Project Summary
- **QWeld** is an Android app that helps welders prepare for the Interprovincial Red Seal exam with full exam simulations and configurable practice sessions.
- Users pick between **Exam** mode (full-length attempt following the official blueprint) or **Practice** mode (custom task/size mixes) to build confidence before test day.
- Exam construction follows published blueprints and localized question banks (English/Russian) stored as JSON; the app assembles sessions from these assets at runtime.

## Tech Stack & Platforms
- **Platform:** Android (minSdk 26, target/compileSdk 35) written in **Kotlin** with **Jetpack Compose** UI and Navigation.
- **Core libraries:** coroutines, Compose Material3, Room for attempts storage, DataStore for user preferences, kotlinx-serialization for JSON parsing, and Timber logging.
- **Firebase:** Auth, Analytics, and Crashlytics are available when `google-services.json` is present; analytics can be disabled for debug builds and are gated at runtime by the user’s analytics preference.
- **CI/CD:** GitHub Actions runs formatting (Spotless), linting (Detekt), unit tests, and `assembleDebug` via `.github/workflows/android.yml`. Separate workflows validate blueprints/questions and publish dist summaries for content PRs.

## Architecture Overview
- **App shell (`app-android`)** hosts the Compose navigation graph, wires feature modules, injects BuildConfig flags (version metadata, analytics switches), and packages localized assets.
- **Features (`feature-exam`, `feature-auth`)** own UI screens, navigation destinations, and ViewModels for the exam/practice and authentication flows.
- **Domain (`core-domain`)** provides the exam assembly mechanics: blueprint loading, quota distribution, random number generators, weighted samplers, timers, and helper math.
- **Data (`core-data`)** supplies persistence and integrations: Room database for attempts/answers, DataStore-backed user preferences, optional Firestore question reporting, and content repositories that read bundled assets.
- **Common/model layers** expose shared utilities (`core-common`) and simple models (`core-model`).
- **Exam assembly pipeline:** blueprint JSON + localized question bank/per-task bundles → domain assembler (quota distribution, shufflers, RNG) → feature ViewModels render questions, capture answers, and write attempts to Room.

### Layer relationships
- **UI shell:** `app-android` hosts the top-level navigation graph, theme, and settings/admin surfaces while consuming feature modules.
- **Features:** `feature-exam` (and `feature-auth`) present user flows and depend on domain/data/common for business logic and persistence.
- **Domain:** `core-domain` encapsulates pure exam logic; it is consumed by data repositories and feature ViewModels without Android dependencies.
- **Data:** `core-data` bridges storage and integrations, depending on `core-domain`/`core-common` and exposing repositories to features.
- **Shared:** `core-common` and `core-model` provide utilities and light models used across all layers.

### Key components
- **ExamViewModel** – orchestrates exam and practice attempts: assembly, timers, autosave/resume, navigation, and persistence.
- **ResultViewModel** – summarizes attempt outcomes and drives post-attempt review flows.
- **AssetQuestionRepository** – loads question content from bundled assets with locale fallback, caching, and task-bundle preference.
- **PrewarmUseCase / PrewarmController** – preloads task bundles before an attempt to avoid UI stalls when entering the exam.
- **BlueprintJsonLoader** – reads and parses blueprint JSON files describing blocks, tasks, quotas, and metadata.
- **UserPrefsDataStore** – persists user preferences such as locale, practice defaults, and analytics toggles.
- **FirestoreQuestionReportRepository** – submits question issue reports to Firestore when remote reporting is enabled.

## Key User Flows
- **Exam mode:** Home/Mode selector → choose exam → pre-warm assets if needed → full 125-question attempt with timers → submission → results and review (per-question answers, rationales, explanations where present).
- **Practice mode:** Open practice sheet → pick blocks/tasks and question count → sampler builds a weighted set (proportional or even) → run practice session → finish → review with filtering (wrong/flagged/by task) and explanations.
- **Content lifecycle:** JSON blueprints and question banks live in `content/`; build scripts flatten them into `dist/` and `app-android/src/main/assets/`. On-device repositories load per-task bundles first, fall back to monolithic banks, and finally to raw files for dev scenarios.
- **Reporting:** Attempts and per-question answers persist locally via Room; optional Firestore reporting uploads question reports when enabled.

## Repository Layout (high level)
- `app-android/` – Android application module, assets, navigation host, and build config.
- `feature-exam/` – Exam/practice UI, content loaders, review/explanation flows, navigation graph.
- `feature-auth/` – Authentication screens and Firebase integration helpers.
- `core-domain/` – Domain logic (quota distribution, RNG, timers, samplers).
- `core-data/` – Persistence, preferences, Firestore reporting, asset-backed content repositories.
- `core-common/` / `core-model/` – Shared utilities and simple models.
- `content/` – Source-of-truth blueprints, question banks, explanations.
- `assets/` and `app-android/src/main/assets/` – Bundled runtime assets (blueprints, question banks, per-task bundles, index manifests).
- `.github/` – CI workflows for Android builds, content validators, and dist summaries.

## Build & Run (Developer Quickstart)
1. Install **JDK/Android Studio** with Android SDK 35; copy `local.properties.sample` to `local.properties` and set `sdk.dir`.
2. Ensure question assets are present under `app-android/src/main/assets/questions/` (run `node scripts/build-questions-dist.mjs` and copy from `dist/questions/` if missing).
3. Build from the command line with `./gradlew assembleDebug` (or `./gradlew spotlessCheck detekt test assembleDebug` to mirror CI).
4. Deploy the debug APK from `app-android/build/outputs/apk/debug/` to an emulator/device; analytics are disabled in debug by default unless overridden.

## Glossary
- **Blueprint** – JSON file describing the exam structure, including blocks, tasks, quotas, total question count, and metadata (`version`, `locale`, `policyVersion`).
- **Block** – High-level grouping of tasks in a blueprint (e.g., sections A–D) used for quota distribution and practice selection presets.
- **Task** – Smallest structural unit in a blueprint (e.g., A-1); maps to subsets of the question bank and task bundles.
- **Question Bank** – Localized JSON set of questions used to build exam/practice sessions; present as monolithic banks and per-task bundles.
- **Task Bundle** – Per-task aggregated question bundle (prebuilt during dist generation) that the asset repository prefers for efficient loading.
- **Attempt** – A single exam or practice run containing user answers, timers, autosave snapshots, and a final result.
- **Exam Mode** – Full-length session strictly following blueprint quotas, timers, and block sequencing.
- **Practice Mode** – Configurable session with user-chosen tasks, quotas, and counts; less strict than exam mode.
- **Locale Fallback** – Logic that falls back from a requested locale (e.g., `ru`) to the default (`en`) when localized content is missing or incomplete.
- **Question Bank Manifest** – Index files that describe available locales, banks, and bundles for loaders to resolve correct assets.
- **Question Report** – Report about a problematic question (content issue, ambiguity, translation) sent to Firestore for moderation.
- **Admin / Debug tools** – Internal screens and utilities for content inspection, logs, and question reports; not part of the main user flow.

## Future Directions (short)
- Broader localization coverage and content parity checks beyond EN/RU.
- Adaptive practice/exam tuning using performance data.
- Improved content editing and validation tooling for authors.
- Richer reporting/analytics dashboards while preserving opt-in controls.
- Expanded accessibility/support tooling (haptics/sounds toggles are present; further refinements expected).
