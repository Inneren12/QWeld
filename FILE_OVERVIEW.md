# File Overview

This document gives a high-level overview of important files in the QWeld repo, their roles, and how safe they are to modify.

Importance legend:
- 🔴 Critical – core logic or APIs; changes require extra care and tests.
- 🟡 Important – commonly used support code; safe to modify with tests.
- ⚪ Support – small helpers, UI cosmetics, etc.; generally safe.
- 🧪 Test – test-only code and data.
- ⚙️ Build/CI – Gradle/CI scripts; changes affect the build pipeline.

## app-android/

- `app-android/src/main/java/com/qweld/app/MainActivity.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Launcher activity setting the Compose hierarchy and hosting the app navigation graph.
  - **Edit guidelines:** Safe to adjust navigation wiring/top-level UI; keep intent handling and start destinations intact.
- `app-android/src/main/java/com/qweld/app/navigation/AppNavGraph.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Defines app-level routes and stitches feature destinations together with guards.
  - **Edit guidelines:** Add new routes here; change existing route IDs cautiously to avoid breaking deep links/tests.
- `app-android/src/main/java/com/qweld/app/error/AppErrorHandler.kt`
  - **Importance:** 🟡 Important
  - **Role:** Centralized error handler implementation that logs non-fatal errors, forwards them to Crashlytics (when analytics are enabled), tracks recent error history for admin diagnostics, and emits UI events for error dialogs/reports.
  - **Edit guidelines:** Keep payloads PII-free, avoid heavy work on the main thread, and preserve analytics/Crashlytics gating when adjusting reporting behavior.
- `app-android/src/main/java/com/qweld/app/QWeldApp.kt`
  - **Importance:** 🟡 Important
  - **Role:** Application class initializing logging, locale controller, and analytics toggles.
  - **Edit guidelines:** Keep initialization lightweight; ensure flags match build variants.
- `app-android/src/main/java/com/qweld/app/i18n/LocaleController.kt`
  - **Importance:** 🟡 Important
  - **Role:** Handles locale selection/fallback and exposes locale state to features.
  - **Edit guidelines:** Verify changes against asset locales and DataStore keys.
- `app-android/src/main/java/com/qweld/app/ui/SettingsScreen.kt`
  - **Importance:** 🟡 Important
  - **Role:** User-facing settings (locale, analytics opt-out, etc.).
  - **Edit guidelines:** Keep preference keys consistent with `UserPrefsDataStore`.
- `app-android/src/main/java/com/qweld/app/ui/AppErrorReportDialog.kt`
  - **Importance:** ⚪ Support
  - **Role:** User-facing dialog allowing “Report an app error” submissions with an optional comment and privacy guidance.
  - **Edit guidelines:** Preserve clear copy about optional comments/PII and keep the submit contract aligned with `AppErrorHandler`.
- `app-android/src/main/java/com/qweld/app/di/AppModule.kt`
  - **Importance:** 🟡 Important
  - **Role:** Hilt bindings for app-wide dependencies (analytics, repositories, DB, error handler, question content/explanation repos, and auth). Also exposes dispatcher/prewarm qualifiers.
  - **Edit guidelines:** Keep bindings aligned with module boundaries; prefer adding new DI entries here over recreating manual singletons.
- `app-android/src/main/java/com/qweld/app/di/qualifiers/Qualifiers.kt`
  - **Importance:** ⚪ Support
  - **Role:** Qualifier annotations for shared dispatchers and testable toggles (prewarm flag flows).
  - **Edit guidelines:** Reuse existing qualifiers before adding new ones.
- `app-android/src/main/java/com/qweld/app/admin/QuestionReportsViewModel.kt` and related admin screens
  - **Importance:** 🟡 Important
  - **Role:** Internal tooling for reviewing submitted question reports.
  - **Edit guidelines:** UI changes are safe; be careful with Firestore/reporting toggles.
- `app-android/src/main/java/com/qweld/app/admin/AdminDashboardViewModel.kt` and `AdminDashboardScreen.kt`
  - **Importance:** 🟡 Important
  - **Role:** Debug-only admin dashboard summarizing attempt counts, recent completions, DB health, and system/report health (queued reports, recent errors).
  - **Edit guidelines:** Keep `BuildConfig.DEBUG` gating intact and source stats through repositories instead of direct Room access.
- `app-android/src/androidTest/java/com/qweld/app/admin/AdminReportsUiTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Compose instrumentation coverage for navigating the admin reports list and verifying report details/comments in the detail view.
  - **Edit guidelines:** Keep scenarios short with fake repositories to avoid network dependence.
- `app-android/src/androidTest/java/com/qweld/app/error/ErrorDialogUiTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Exercises the user-facing “Report app error” dialog to assert visibility, comment input, and submission wiring via a fake crash reporter.
  - **Edit guidelines:** Keep the composable host minimal and deterministic to avoid emulator flakiness.
- `app-android/src/test/java/com/qweld/app/error/AppErrorHandlerTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Unit coverage for the centralized AppErrorHandler logging, analytics gating, and UI event emission contracts.
  - **Edit guidelines:** Use fakes for crash reporting/logging to keep assertions deterministic.
- `app-android/build.gradle.kts`
  - **Importance:** ⚙️ Build/CI
  - **Role:** App module build config, Crashlytics/Analytics wiring, asset packaging tasks (`verifyAssets`).
  - **Edit guidelines:** Change only when updating dependencies/build behavior; watch CI.

## feature-exam/

- `feature-exam/src/main/java/com/qweld/app/feature/exam/navigation/ExamNavGraph.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Feature-level navigation graph for exam and practice flows.
  - **Edit guidelines:** Add destinations here when expanding flows; keep route constants stable.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/vm/ExamViewModel.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Central ViewModel orchestrating exam/practice assembly, navigation, and coordination of the timer/prewarm/autosave controllers.
  - **Edit guidelines:** Prefer delegating new responsibilities into focused controllers; maintain timer/autosave invariants and adaptive mode behavior.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/vm/ExamTimerController.kt`
  - **Importance:** 🟡 Important
  - **Role:** Timer controller abstraction plus default implementation wrapping `TimerController`, drives tick updates/expiry callbacks for IP exams.
  - **Edit guidelines:** Keep tick cadence stable (1s) and duration formatting consistent with prior behavior; surface updates via callbacks only.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/vm/ExamPrewarmCoordinator.kt`
  - **Importance:** 🟡 Important
  - **Role:** Coordinates IP exam prewarm runs, tracks progress/readiness state, and wraps `PrewarmController`/`PrewarmUseCase` work.
  - **Edit guidelines:** Preserve task selection logic and progress math; keep state emissions lightweight for UI consumption.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/vm/ExamAutosaveController.kt`
  - **Importance:** 🟡 Important
  - **Role:** Manages autosave lifecycle (preparing, ticking, flushing) around `AutosaveController` for attempt persistence.
  - **Edit guidelines:** Keep autosave intervals and flushing semantics unchanged; prefer constructor injection for testability.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/vm/ResultViewModel.kt` and `ReviewViewModel.kt`
  - **Importance:** 🟡 Important
  - **Role:** Drive results summary and review filtering/search.
  - **Edit guidelines:** Keep derived stats consistent with persisted attempts.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/vm/PrewarmUseCase.kt` and `PrewarmController.kt`
  - **Importance:** 🟡 Important
  - **Role:** Preloads question bundles before entering an attempt to avoid UI stalls.
  - **Edit guidelines:** Ensure coroutine scope/dispatcher usage stays aligned with UI lifecycle.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/data/AssetQuestionRepository.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Loads task bundles/monolithic banks from assets with locale fallback and caching.
  - **Edit guidelines:** Keep manifest paths and fallback order aligned with `content/` structure; add tests for new behaviors.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/data/BlueprintJsonLoader.kt`
  - **Importance:** 🟡 Important
  - **Role:** Parses blueprint JSON into domain models for assembly.
  - **Edit guidelines:** Validate version/locale handling when changing parsing logic.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/explain/ExplanationRepositoryImpl.kt`
  - **Importance:** 🟡 Important
  - **Role:** Reads per-question explanations from assets and exposes them to UI.
  - **Edit guidelines:** Maintain consistency with question IDs/locales.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/ui/*.kt`
  - **Importance:** ⚪ Support
  - **Role:** Compose screens and dialogs for exam/practice, review, explanations, and reporting.
  - **Edit guidelines:** UI tweaks are safe; keep state contracts with ViewModels intact.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/di/ExamModule.kt`
  - **Importance:** 🟡 Important
  - **Role:** Hilt bindings for exam-specific dependencies (blueprint resolver, timers, prewarm/resume use cases/controllers).
  - **Edit guidelines:** Keep bindings pure/Kotlin-friendly; prefer updating this module when introducing new exam controllers/use cases.
- `feature-exam/src/androidTest/java/com/qweld/app/feature/exam/di/TestExamModule.kt`
  - **Importance:** 🧪 Test
  - **Role:** Hilt test overrides for exam bindings (blueprints, timers, prewarm/resume) to enable deterministic fakes in instrumentation.
  - **Edit guidelines:** Extend/replace bindings here rather than editing production modules for test needs.
- `feature-exam/src/test/...`
  - **Importance:** 🧪 Test
  - **Role:** Unit/UI tests for exam flows, loaders, and content validation.
  - **Edit guidelines:** Extend freely; ensure fixtures match content manifests.
- `feature-exam/src/test/java/com/qweld/app/feature/exam/data/AssetQuestionRepositoryLocaleFallbackTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Verifies EN↔RU locale fallback behavior for `AssetQuestionRepository`, including missing/corrupt RU assets.
  - **Edit guidelines:** Keep fixtures aligned with manifest expectations; preserve clear assertions for fallback vs corruption.
- `feature-exam/src/test/java/com/qweld/app/feature/exam/data/LocaleCoverageTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Computes EN→RU locale coverage from source content, logs per-task gaps, and optionally enforces a minimum coverag
e threshold (via `localeCoverage.ru.min`).
  - **Edit guidelines:** Keep parsing tolerant of arrays/singleton question files; adjust thresholds in CI when coverage shifts.
- `feature-exam/src/androidTest/java/com/qweld/app/feature/exam/ui/ExamSubmitResumeTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Compose instrumentation coverage for exam submit/resume flow assertions and timer label persistence.
  - **Edit guidelines:** Extend with additional edge cases for lifecycle/resume behavior.
- `feature-exam/src/androidTest/java/com/qweld/app/feature/exam/ui/ExamTimerLifecycleTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Compose instrumentation coverage validating exam timer stability across backgrounding, configuration changes, and resume-from-remaining scenarios.
  - **Edit guidelines:** Keep scenarios short/deterministic (use fake clocks/dispatchers) to avoid flakiness in CI emulators.
- `feature-exam/src/androidTest/java/com/qweld/app/feature/exam/ui/PracticeHappyPathTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Compose instrumentation happy-path coverage for practice runs (answering questions and reaching results).
  - **Edit guidelines:** Keep scenario short/deterministic; extend with additional practice UI assertions if flows expand.
- `feature-exam/src/androidTest/java/com/qweld/app/feature/exam/ui/QuestionReportUiTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** UI test for the question reporting flow from an exam screen, covering dialog interactions and comment submission.
  - **Edit guidelines:** Use lightweight state and strings to avoid flakiness from full exam navigation.
- `feature-exam/src/androidTest/java/com/qweld/app/feature/exam/ui/AdaptiveExamUiTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Compose instrumentation smoke test for adaptive exam mode that toggles adaptive on, answers a short attempt, and verifies the adaptive label and results rendering.
  - **Edit guidelines:** Keep the attempt small and deterministic; avoid heavy navigation stacks to limit emulator flakiness.
- `feature-exam/src/test/java/com/qweld/app/feature/exam/vm/ExamViewModelReportingTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Unit tests for queued/offline question reporting behavior, ensuring UI events surface and errors are routed through the AppErrorHandler.
  - **Edit guidelines:** Keep repository fakes deterministic and avoid expanding exam assembly complexity.

## feature-auth/

- `feature-auth/src/main/java/com/qweld/app/feature/auth/AuthService.kt`
  - **Importance:** 🟡 Important
  - **Role:** Abstraction for authentication actions consumed by UI screens.
  - **Edit guidelines:** Keep API stable for consumers; document new auth flows.
- `feature-auth/src/main/java/com/qweld/app/feature/auth/firebase/FirebaseAuthService.kt`
  - **Importance:** 🟡 Important
  - **Role:** Firebase-backed implementation handling sign-in/link flows.
  - **Edit guidelines:** Validate auth providers/scopes when modifying.
- `feature-auth/src/main/java/com/qweld/app/feature/auth/GoogleCredentialSignInManager.kt`
  - **Importance:** 🟡 Important
  - **Role:** Manages Google credential retrieval and token exchange.
  - **Edit guidelines:** Keep intent contracts and request codes stable.
- `feature-auth/src/main/java/com/qweld/app/feature/auth/ui/*.kt`
  - **Importance:** ⚪ Support
  - **Role:** Compose screens for sign-in and account linking.
  - **Edit guidelines:** UI adjustments are safe; avoid breaking auth callback wiring.

## core-domain/

- `core-domain/src/main/java/com/qweld/app/domain/exam/ExamAssembler.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Builds exam/practice sessions by applying quotas, shufflers, and samplers to question sources.
  - **Edit guidelines:** Keep deterministic behavior; update tests when changing assembly rules.
- `core-domain/src/main/java/com/qweld/app/domain/exam/ExamAssemblerFactory.kt`
  - **Importance:** 🟡 Important
  - **Role:** Creates standard or adaptive assemblers without changing existing call sites; keeps adaptive mode opt-in.
  - **Edit guidelines:** Preserve defaults so existing exam/practice wiring stays non-adaptive unless explicitly requested.
- `core-domain/src/main/java/com/qweld/app/domain/exam/QuotaDistributor.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Allocates question counts across blocks/tasks according to blueprint quotas and practice configs.
  - **Edit guidelines:** Cover edge cases (rounding, overflow) with tests.
- `core-domain/src/main/java/com/qweld/app/domain/exam/TaskBlockMapper.kt`
  - **Importance:** 🟡 Important
  - **Role:** Maps tasks to blocks and exposes metadata used by UI and scoring.
  - **Edit guidelines:** Keep mappings in sync with blueprint versions.
- `core-domain/src/main/java/com/qweld/app/domain/exam/TimerController.kt`
  - **Importance:** 🟡 Important
  - **Role:** Timer utilities for tracking elapsed/remaining time per attempt.
  - **Edit guidelines:** Ensure time math aligns with UI expectations and autosave/resume logic.
- `core-domain/src/main/java/com/qweld/app/domain/exam/util/{WeightedSampler,RandomProvider,Pcg32,Shufflers}.kt`
  - **Importance:** 🟡 Important
  - **Role:** Deterministic RNG and sampling utilities used during assembly.
  - **Edit guidelines:** Maintain determinism and seeding behavior; adjust tests when tuning algorithms.
- `core-domain/src/main/java/com/qweld/app/domain/adaptive/AdaptiveExamPolicy.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Design specification and scaffolding for EXAM-3 adaptive exam policy (difficulty bands, hysteresis rules, and state tracking).
  - **Edit guidelines:** Keep comments and TODOs aligned with stage.md; avoid wiring changes until the adaptive flag is implemented.
  - **Key classes:** `AdaptiveState`, `DefaultAdaptiveExamPolicy`, `AdaptiveExamAssembler` (adaptive sampler that stays opt-in).
- `core-domain/src/test/...`
  - **Importance:** 🧪 Test
  - **Role:** Coverage for quota distribution, RNG, and sampler correctness.
  - **Edit guidelines:** Extend freely to guard new logic.
- `core-domain/src/test/java/com/qweld/app/domain/exam/QuotaDistributorEdgeCaseTest.kt`
  - **Importance:** 🧪 Test
  - **Role:** Edge-case coverage for quota distribution rounding, even splits, and guardrails against invalid allocations.
  - **Edit guidelines:** Keep fixtures deterministic and totals aligned with blueprint expectations.

## core-data/

- `core-data/src/main/java/com/qweld/app/data/db/QWeldDb.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Room database definition including entities and migrations for attempts/answers.
  - **Edit guidelines:** Update version/migrations carefully; keep schema in sync with DAOs.
- `core-data/src/main/java/com/qweld/app/data/db/entities/QueuedQuestionReportEntity.kt`
  - **Importance:** 🟡 Important
  - **Role:** Room entity storing queued question reports for offline retries (REPORT-1).
  - **Edit guidelines:** Add migrations when altering columns; keep payload format aligned with `QueuedQuestionReportPayload`.
- `core-data/src/main/java/com/qweld/app/data/db/dao/QueuedQuestionReportDao.kt`
  - **Importance:** 🟡 Important
  - **Role:** DAO for enqueuing, listing, and updating queued question reports before retrying uploads.
  - **Edit guidelines:** Keep ordering/indexes consistent with retry policy; update migrations/schema on query changes.
- `core-data/src/main/java/com/qweld/app/data/db/dao/{AttemptDao,AnswerDao}.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Read/write APIs for persisted attempts and answers.
  - **Edit guidelines:** Add queries with care; update tests and migrations when schema changes.
- `core-data/src/main/java/com/qweld/app/data/repo/{AttemptsRepository,AnswersRepository,UserStatsRepositoryRoom}.kt`
  - **Importance:** 🟡 Important
  - **Role:** Repository layer exposing persistence operations to features.
  - **Edit guidelines:** Keep transaction boundaries and threading consistent.
- `core-data/src/main/java/com/qweld/app/data/prefs/UserPrefsDataStore.kt`
  - **Importance:** 🔴 Critical
  - **Role:** Stores user preferences (locale, practice presets, analytics opt-out, adaptive exam opt-in) in DataStore.
  - **Edit guidelines:** Keep keys stable; handle migrations when renaming fields.
- `core-data/src/main/java/com/qweld/app/data/content/questions/{IndexParser,AssetIntegrityGuard}.kt`
  - **Importance:** 🟡 Important
  - **Role:** Validates and resolves question bundle manifests and asset integrity checks.
  - **Edit guidelines:** Preserve manifest schema compatibility.
- `core-data/src/main/java/com/qweld/app/data/content/ContentManifestDiagnostics.kt`
  - **Importance:** 🟡 Important
  - **Role:** Reads the aggregated content manifest (`questions/index.json`) and computes admin-facing diagnostics/statuses.
  - **Edit guidelines:** Keep required locale/task lists in sync with supported content; update diagnostics tests when rules change.
- `core-data/src/main/java/com/qweld/app/data/reports/FirestoreQuestionReportRepository.kt`
  - **Importance:** 🟡 Important
  - **Role:** Sends question issue reports to Firestore when enabled.
  - **Edit guidelines:** Guard network calls/credentials; respect user privacy toggles.
- `core-data/src/main/java/com/qweld/app/data/reports/QuestionReportPayloadBuilder.kt`
  - **Importance:** 🟡 Important
  - **Role:** Builds sanitized Firestore payloads for question reports, enriching them with locale, blueprint, app, and device metadata.
  - **Edit guidelines:** Keep payload free of PII; update reporting tests when adding or removing fields.
- `core-data/src/main/java/com/qweld/app/data/reports/ReportEnvironmentMetadata.kt`
  - **Importance:** 🟡 Important
  - **Role:** Supplies app/device/environment metadata providers used to enrich question report payloads (version/build type, Android version, device model).
  - **Edit guidelines:** Keep defaults PII-free and limited to coarse environment details.
- `core-data/src/main/java/com/qweld/app/data/reports/RetryQueuedQuestionReportsUseCase.kt`
  - **Importance:** 🟡 Important
  - **Role:** Thin use case invoking queued report retries with configurable attempt/batch limits.
  - **Edit guidelines:** Keep defaults aligned with repository retry behavior; prefer invoking through this wrapper from app startup triggers.
- `core-data/src/main/java/com/qweld/app/data/analytics/Analytics.kt`
  - **Importance:** 🟡 Important
  - **Role:** Analytics hooks and event logging wrappers.
  - **Edit guidelines:** Avoid logging PII; keep debug flags in mind.
- `core-data/src/test/...`
  - **Importance:** 🧪 Test
  - **Role:** Tests for repositories, DataStore, and content loaders.
  - **Edit guidelines:** Safe to extend; ensure deterministic fixtures.

## core-common/

- `core-common/src/main/java/com/qweld/app/common/AppEnv.kt`
  - **Importance:** 🟡 Important
  - **Role:** Environment/config helpers shared across modules.
  - **Edit guidelines:** Keep API minimal to avoid tight coupling.
- `core-common/src/main/java/com/qweld/app/common/logging/Logx.kt`
  - **Importance:** ⚪ Support
  - **Role:** Logging utilities wrapping Timber.
  - **Edit guidelines:** Safe to adjust formatting; avoid heavy dependencies here.
- `core-common/src/main/java/com/qweld/app/common/error/AppErrorHandler.kt`
  - **Importance:** ⚪ Support
  - **Role:** Shared error models and `AppErrorHandler` interface defining non-fatal error handling contracts (history, UI events, Crashlytics/report submission hooks).
  - **Edit guidelines:** Keep types platform-agnostic and PII-free; preserve backward-compatible defaults when extending the model surface.

## core-model/

- `core-model/src/main/java/com/qweld/app/model/GreetingProvider.kt`
  - **Importance:** ⚪ Support
  - **Role:** Placeholder/shared model class.
  - **Edit guidelines:** Minimal impact; safe to extend as shared models grow.

## content/

- `content/blueprints/welder_ip_2024.json` and `welder_ip_sk_202404.json`
  - **Importance:** 🔴 Critical
  - **Role:** Source-of-truth blueprints for exam assembly (blocks, tasks, quotas, metadata).
  - **Edit guidelines:** Validate with content scripts; keep version/policy metadata accurate.
- `content/questions/en/*`
  - **Importance:** 🔴 Critical
  - **Role:** English per-task question banks; each JSON contains prompts/answers/rationales.
  - **Edit guidelines:** Keep IDs stable; run validators after edits.
- `content/questions/ru/*`
  - **Importance:** 🔴 Critical
  - **Role:** Russian localized question sets with locale fallback to English when missing.
  - **Edit guidelines:** Ensure translation quality and ID parity with English files.
- `content/questions/sample_welder_question.json`
  - **Importance:** 🧪 Test
  - **Role:** Sample question used for tests/examples.
  - **Edit guidelines:** Safe to adjust for documentation/tests.
- `assets/` and `app-android/src/main/assets/`
  - **Importance:** 🟡 Important
  - **Role:** Bundled runtime assets (blueprints, task bundles, manifests) used by loaders.
  - **Edit guidelines:** Regenerate via scripts when updating source content.

## .github/

- `.github/workflows/android.yml`
  - **Importance:** ⚙️ Build/CI
  - **Role:** Main CI pipeline running formatting, lint, unit tests, and assembleDebug.
  - **Edit guidelines:** Update when build matrix or required checks change.
- `.github/workflows/content-validators.yml` and `content-validators-full.yml`
  - **Importance:** ⚙️ Build/CI
  - **Role:** Validate blueprints/questions for schema and locale coverage.
  - **Edit guidelines:** Keep in sync with content schema changes.
- `.github/workflows/dist-summary.yml`
  - **Importance:** ⚙️ Build/CI
  - **Role:** Publishes dist summaries for content PRs.
  - **Edit guidelines:** Adjust only when dist generation changes.
- `.github/workflows/ui-smoke.yml`
  - **Importance:** ⚙️ Build/CI
  - **Role:** Optional UI smoke tests.
  - **Edit guidelines:** Update device/emulator settings carefully.

## benchmarks-jvm/

- `benchmarks-jvm/build.gradle.kts`
  - **Importance:** ⚙️ Build/CI
  - **Role:** Gradle config for JMH benchmarks scaffold.
  - **Edit guidelines:** Safe to extend when adding benchmarks; isolated from app code.

## tools/ and scripts/

- `scripts/build-questions-dist.mjs`
  - **Importance:** 🟡 Important
  - **Role:** Builds question bundle distributions for packaging into assets.
  - **Edit guidelines:** Verify Node dependencies and output paths when modifying.
- `tools/` helpers
  - **Importance:** ⚪ Support
  - **Role:** Misc utilities for development/validation.
  - **Edit guidelines:** Safe to evolve as workflows grow.
