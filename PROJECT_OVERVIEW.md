# Project Overview

## Project Summary
- **QWeld** is an Android app that helps welders prepare for the Interprovincial Red Seal exam with full exam simulations and configurable practice sessions.
- Users pick between **Exam** mode (full-length attempt following the official blueprint) or **Practice** mode (custom task/size mixes) to build confidence before test day.
- Exam construction follows published blueprints and localized question banks (English/Russian) stored as JSON; the app assembles sessions from these assets at runtime.

## Tech Stack & Platforms
- **Platform:** Android (minSdk 26, target/compileSdk 35) written in **Kotlin** with **Jetpack Compose** UI and Navigation.
- **Core libraries:** coroutines, Compose Material3, Room for attempts storage, DataStore for user preferences, kotlinx-serialization for JSON parsing, Timber logging, and **Hilt** for dependency injection.
- **Firebase:** Auth, Analytics, and Crashlytics are available when `google-services.json` is present; analytics can be disabled for debug builds and are gated at runtime by the user's analytics preference.
- **CI/CD:** GitHub Actions runs formatting (Spotless), linting (Detekt), unit tests, and `assembleDebug` via `.github/workflows/android.yml`. Separate workflows validate blueprints/questions, enforce RU locale coverage, and publish dist summaries for content PRs.

## Architecture Overview
- **App shell (`app-android`)** hosts the Compose navigation graph, wires feature modules via **Hilt DI**, injects BuildConfig flags (version metadata, analytics switches), packages localized assets, and provides centralized error handling.
- **Features (`feature-exam`, `feature-auth`)** own UI screens, navigation destinations, and ViewModels (injected via `@HiltViewModel`) for the exam/practice and authentication flows.
- **Domain (`core-domain`)** provides the exam assembly mechanics: blueprint loading, quota distribution, random number generators, weighted samplers, timers, helper math, and **adaptive exam policies** that adjust difficulty based on user performance.
- **Data (`core-data`)** supplies persistence and integrations: Room database for attempts/answers/queued reports, DataStore-backed user preferences, optional Firestore question reporting with offline queueing, and content repositories that read bundled assets.
- **Common/model layers** expose shared utilities (`core-common`, including error handler interfaces) and simple models (`core-model`).
- **Dependency injection:** Hilt modules (`AppModule`, `ExamModule`) wire core dependencies and feature-specific components with test overrides available for instrumentation.
- **Exam assembly pipeline:** blueprint JSON + localized question bank/per-task bundles → domain assembler (quota distribution, shufflers, RNG, optional adaptive sampler) → feature ViewModels orchestrate controllers (timers, prewarm, autosave) that render questions, capture answers, and write attempts to Room.

### Layer relationships
- **UI shell:** `app-android` hosts the top-level navigation graph, theme, and settings/admin surfaces while consuming feature modules.
- **Features:** `feature-exam` (and `feature-auth`) present user flows and depend on domain/data/common for business logic and persistence.
- **Domain:** `core-domain` encapsulates pure exam logic; it is consumed by data repositories and feature ViewModels without Android dependencies.
- **Data:** `core-data` bridges storage and integrations, depending on `core-domain`/`core-common` and exposing repositories to features.
- **Shared:** `core-common` and `core-model` provide utilities and light models used across all layers.

### Key components
- **ExamViewModel** – orchestrates exam and practice attempts by delegating to specialized controllers for timers, prewarm, and autosave; drives assembly, navigation, and persistence.
- **ExamTimerController / ExamPrewarmCoordinator / ExamAutosaveController** – focused controllers extracted from ExamViewModel that handle timer ticking/expiry, bundle preloading, and autosave lifecycle independently.
- **ResultViewModel** – summarizes attempt outcomes and drives post-attempt review flows.
- **AssetQuestionRepository** – loads question content from bundled assets with locale fallback, caching, and task-bundle preference.
- **AdaptiveExamPolicy / AdaptiveExamAssembler** – implements adaptive exam mode that adjusts question difficulty based on user performance (correct/incorrect streaks); enabled via beta toggle.
- **AppErrorHandler** – centralized non-fatal error handler that logs errors, forwards to Crashlytics (when analytics enabled), tracks recent error history for admin diagnostics, and emits UI events for user-facing error dialogs.
- **AdminDashboardViewModel / QuestionReportsViewModel** – debug-only admin tools that surface attempt stats, DB health, queued question reports, and recent errors for internal QA and content triage.
- **PrewarmUseCase / PrewarmController** – preloads task bundles before an attempt to avoid UI stalls when entering the exam.
- **BlueprintJsonLoader** – reads and parses blueprint JSON files describing blocks, tasks, quotas, and metadata.
- **UserPrefsDataStore** – persists user preferences such as locale, practice defaults, analytics toggles, and adaptive exam opt-in.
- **FirestoreQuestionReportRepository** – submits question issue reports to Firestore when remote reporting is enabled, with offline queueing and retry on app start.

## Key User Flows
- **Exam mode:** Home/Mode selector → choose exam → (optional: enable adaptive mode via beta toggle) → pre-warm assets if needed → full 125-question attempt with timers → submission → results and review (per-question answers, rationales, explanations where present).
- **Adaptive exam mode:** When enabled, exam difficulty adjusts dynamically based on performance: consecutive correct answers increase difficulty (harder questions), consecutive incorrect answers decrease difficulty (easier questions), providing a personalized challenge level.
- **Practice mode:** Open practice sheet → pick blocks/tasks and question count → sampler builds a weighted set (proportional or even) → run practice session → finish → review with filtering (wrong/flagged/by task) and explanations.
- **Question reporting:** During exam/review, users can report problematic questions (content issue, ambiguity, translation) via "Report issue" action; reports include question ID, locale, blueprint/app metadata, and optional comment, then queue for Firestore submission with offline retry.
- **Error reporting:** When unexpected errors occur, a user-facing dialog offers "Send report" with optional comment; submissions attach context to Crashlytics (respecting analytics opt-out) and recent error history appears in admin dashboard.
- **Admin/debug tools:** Settings → Tools (debug builds only) → Admin Dashboard surfaces attempt counts, DB health, queued reports, and recent errors; Question Reports screen lists submitted reports with comments and error context for content triage.
- **Content lifecycle:** JSON blueprints and question banks live in `content/`; build scripts flatten them into `dist/` and `app-android/src/main/assets/`. On-device repositories load per-task bundles first, fall back to monolithic banks, and finally to raw files for dev scenarios.
- **Reporting:** Attempts and per-question answers persist locally via Room; optional Firestore reporting uploads question reports when enabled, with offline queueing and retry on app start.

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
- **Adaptive Exam Mode** – Variant of exam mode that adjusts question difficulty dynamically based on user performance (correct/incorrect streaks); enabled via beta toggle and uses `AdaptiveExamPolicy` to determine difficulty transitions.
- **Practice Mode** – Configurable session with user-chosen tasks, quotas, and counts; less strict than exam mode.
- **Locale Fallback** – Logic that falls back from a requested locale (e.g., `ru`) to the default (`en`) when localized content is missing or incomplete.
- **Question Bank Manifest** – Index files that describe available locales, banks, and bundles for loaders to resolve correct assets.
- **Question Report** – Report about a problematic question (content issue, ambiguity, translation) sent to Firestore for moderation; includes question ID, locale, blueprint/app/device metadata, and optional user comment; queued locally when offline.
- **Error Handler** – Centralized `AppErrorHandler` that captures non-fatal errors, logs them, forwards to Crashlytics (when analytics enabled), tracks recent error history, and emits UI events for user-facing error dialogs.
- **Admin Dashboard** – Debug-only internal screen (Settings → Tools) that surfaces attempt counts, DB health, queued question reports, recent errors, and system diagnostics for QA and content triage.
- **Admin / Debug tools** – Internal screens and utilities for content inspection, logs, question reports, and error diagnostics; gated by `BuildConfig.DEBUG` and not part of the main user flow.
- **DI / Dependency Injection** – Hilt-based dependency injection framework wiring app-wide and feature-specific components (modules: `AppModule`, `ExamModule`) with test overrides for instrumentation.

## Future Directions (short)
- Broader localization coverage and content parity checks beyond EN/RU.
- Refinement of adaptive exam policies and expanding adaptive mode to practice sessions.
- Improved content editing and validation tooling for authors.
- Richer reporting/analytics dashboards while preserving opt-in controls.
- Expanded accessibility/support tooling (haptics/sounds toggles are present; further refinements expected).
- Migration of remaining ViewModels and services to Hilt DI.
- Enhanced admin dashboard features (log snippets, migration audit trail, integrity checks).
