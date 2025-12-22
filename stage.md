# QWeld – Stages & Status

Legend:
- ✅ Done
- ⚠️ Partially done / needs polish
- ⏳ Planned / not implemented yet
- 🧪 Experimental / prototype

## Exam & Practice Flows

### EXAM-1 – Exam mode (full IP blueprint)
- **Status:** ✅
- **Summary:** Full-length exam runs follow the Interprovincial blueprint with timers, autosave/resume, and results/review screens. Added UI/instrumentation coverage around submit/resume plus timer stability checks. **NEW:** Added Room-backed timer persistence for process-death resume via `remaining_time_ms` column in attempts table.
- **Implemented in:** `feature-exam` (`ExamViewModel`, `ResultViewModel`, `ReviewViewModel`, `ResumeUseCase`), `core-domain` (quota distribution, timers), `core-data` (`AttemptEntity`, `AttemptsRepository`), asset blueprints under `content/blueprints/`.
- **Recent additions:**
  - [x] Added `remaining_time_ms` field to `AttemptEntity` for persisting timer state (QWELD_DB_VERSION 5, MIGRATION_4_5)
  - [x] Updated `ResumeUseCase.remainingTime()` to use persisted value with fallback to calculation from `startedAt`
  - [x] Updated `ExamViewModel` timer callbacks to persist remaining time on each tick via `persistRemainingTime()`
  - [x] Added `AttemptDao.updateRemainingTime()` and `AttemptsRepository.updateRemainingTime()` for autosave integration
- **Next tasks:**
  - [x] Exercise timer alignment through full activity recreation/backgrounded emulator runs (beyond unit coverage).
  - [x] Cover Room-backed resume after process death to ensure autosave snapshots and timer restore correctly.
  - [ ] Add comprehensive instrumentation test simulating process kill and resume with correct timer state.

### EXAM-2 – Practice mode (configurable)
- **Status:** ✅
- **Summary:** Practice sessions allow selecting tasks/blocks and question counts with proportional or even sampling and review filters, now with clearer selection helpers (select/clear all, selection count hints) and automatic persistence of the last used setup for quick reuse. **NEW:** Added Room-backed named practice presets with CRUD operations, allowing users to save, update, delete, and reuse frequently used configurations.
- **Implemented in:** `feature-exam` (`ExamViewModel`, practice config UI/models), `core-domain` samplers, `core-data` (`PracticePresetEntity`, `PracticePresetDao`, `PracticePresetsRepository`).
- **Recent additions:**
  - [x] Created `PracticePresetEntity` with fields for name, blocks, taskIds, distribution, size, wrongBiased, createdAt, updatedAt
  - [x] Created `PracticePresetDao` with CRUD operations and Flow-based observation for reactive UI
  - [x] Created `PracticePresetsRepository` with validation, logging, and async operations
  - [x] Added database migration (QWELD_DB_VERSION 6, MIGRATION_5_6) for practice_presets table with unique name index
- **Next tasks:**
  - [x] Improve task selection UX (multi-select presets, clearer quotas).
  - [x] Add saved presets/tests for frequent practice mixes.
  - [ ] Wire `PracticePresetsRepository` into DI (AppModule)
  - [ ] Add UI for preset management (save/update/delete/list) in practice config screen
  - [ ] Implement optional preset export/import via clipboard for sharing
  - [ ] Add comprehensive unit and UI tests for practice presets
 
### EXAM-3 – Adaptive exam mode
- **Status:** ✅
- **Summary:** Adaptive exam mode adjusts question difficulty dynamically based on user performance (correct/incorrect streaks). Beta-only toggle gates adaptive assembly, surfaces in-exam label when active, and policy/assembler behavior carries deterministic unit coverage plus UI smoke test coverage. **NEW:** Enhanced `AdaptiveConfig` with comprehensive KDoc explaining threshold rationale (2-up/1-down asymmetry for hysteresis), added analytics events for adaptive mode usage tracking.
- **Implemented in:** `core-domain` (`AdaptiveExamPolicy`, `DefaultAdaptiveExamPolicy`, `AdaptiveConfig`, `AdaptiveExamAssembler`, `ExamAssemblerFactory`), `feature-exam` (exam flow wiring, UI toggle, beta label, `AdaptiveExamUiTest`), `core-data` (`Analytics` adaptive event extensions).
- **Recent additions:**
  - [x] Updated `AdaptiveConfig` KDoc with detailed rationale for thresholds (correctStreakForIncrease=2, incorrectStreakForDecrease=1, preferMediumWhenRemainingAtOrBelow=2)
  - [x] Added zig-zag prevention explanation and expected difficulty distribution (30-40% EASY, 35-45% MEDIUM, 20-30% HARD)
  - [x] Added analytics extension functions: `logAdaptiveExamToggle()`, `logAdaptiveExamStart()`, `logAdaptiveExamFinish()` with difficulty mix tracking
  - [x] Added `logAdaptivePracticeStart()` for future adaptive practice support
- **Next tasks:**
  - [x] Gather user feedback on adaptive difficulty tuning and adjust policy parameters if needed.
  - [ ] Wire analytics events into `ExamViewModel` start/finish flows for adaptive mode
  - [ ] Consider expanding adaptive mode to practice sessions (analytics foundation in place)
  - [ ] Add instrumentation test for adaptive analytics event emission

## Content & Localization

### CONTENT-1 – EN content completeness
- **Status:** ✅
- **Summary:** English blueprints, task bundles, and monolithic banks ship with the app and pass existing validators. Explanation coverage infrastructure in place with `check-explanation-coverage.sh` reporting EN/RU metrics.
- **Implemented in:** `content/blueprints/`, `content/questions/en/`, asset manifests, `scripts/check-explanation-coverage.sh`.
- **Recent additions:**
  - [x] Added `check-explanation-coverage.sh` script to report EN explanation coverage (% of questions with explanations) and RU translation coverage (% of EN explanations with RU translations)
  - [x] Explanation validator detects EN questions missing explanations and warns (doesn't fail) when RU translations are missing
- **Next tasks:**
  - [x] Add explanation coverage metrics and validators
  - [ ] Periodically re-run content validators before releases
  - [ ] Expand explanation coverage where missing (current: ~3.2% EN coverage, 100% RU translation of existing explanations)

### CONTENT-2 – RU coverage
- **Status:** ✅
- **Summary:** Russian localization exists with coverage enforcement via `LocaleCoverageTest` in CI; loaders fall back to English when gaps occur, and coverage threshold gates PRs to prevent regressions.
- **Implemented in:** `content/questions/ru/` plus locale fallback in `AssetQuestionRepository` and `LocaleCoverageTest` in `feature-exam`.
- **Next tasks:**
  - [ ] Close remaining translations to reach 100% EN-RU parity.
  - [ ] Sync blueprint metadata localization (titles, descriptions).

## Admin & Debug Tools

### ADMIN-1 – Content info & asset diagnostics
- **Status:** ✅
- **Summary:** Internal screens show asset availability, manifest/version details, and question reports; includes `ContentManifestDiagnostics` for admin-facing content health checks with locale/task validation and status reporting (OK/WARNING/ERROR).
- **Implemented in:** `app-android` admin screens/viewmodels and `core-data` (`ContentManifestDiagnostics`).
- **Recent additions:**
  - [x] ContentManifestDiagnostics validates required locales (EN/RU) and tasks against manifest
  - [x] Status reporting (OK/WARNING/ERROR) with detailed messages for missing locales/tasks
  - [x] Version and generatedAt timestamp display in Content Info
- **Next tasks:**
  - [ ] Add deeper blueprint-to-manifest cross-checks (e.g., quota-aware coverage hints, blueprint task vs manifest task comparison).
  - [ ] Surface content versioning timeline and update history in admin views (track build history).

### ADMIN-2 – Admin/debug dashboard
- **Status:** ✅
- **Summary:** Debug-only admin dashboard (Settings → Tools, `BuildConfig.DEBUG` gated) surfaces attempt counts, last completion timestamp, failure totals, answer row counts, DB version health, queued question reports count, and recent error summaries from `AppErrorHandler` history.
- **Implemented in:** `app-android` (`AdminDashboardScreen`, `AdminDashboardViewModel`) with guarded navigation; `core-data` attempt/answer/report stats queries and error handler integration.
- **Recent additions:**
  - [x] Recent error summaries integrated from AppErrorHandler history
  - [x] Queued question reports count displayed
  - [x] DB version health indicator
- **Next tasks:**
  - [ ] Layer in recent log snippets (last N log entries from in-memory or temp log file).
  - [ ] Show basic error stats (count of non-fatal errors, last error time) in dedicated section.
  - [ ] Expand health checks with DB migration audit trail (version history, migration timestamps).
  - [ ] Consider adding a hidden gesture for release builds if internal QA needs access.


## Question Reporting & Error Handling

### REPORT-1 – Question report pipeline
- **Status:** ✅
- **Summary:** Firestore-backed repository exists with offline queueing and retries on app start; UI can submit reports when enabled, metadata (question/locale, blueprint/content versions, app/device info, timestamps) is captured for triage, and failed submissions are persisted for later delivery. Admin dashboard now surfaces queued-report counts and error-context hints in report summaries.
- **Implemented in:** `core-data` (`FirestoreQuestionReportRepository`), admin/report screens in `app-android`.
- **Next tasks:**
  - [x] Add offline queue/retry for reports.
  - [x] Include device/context metadata for triage.
  - [x] Testing: payload structure and PII constraints covered by unit tests.

### REPORT-2 – In-app "Report issue" flow for questions
- **Status:** ✅
- **Summary:** User-facing reporting flow surfaces on exam and review screens with a dialog for reasons/comments and snackbar feedback; submissions flow through `QuestionReportRepository` with queued fallback. Admin listing includes summaries, detail views, and badges when a report followed a recent error. Comprehensive test coverage via `QuestionReportUiTest`, `AdminReportsUiTest`, and `ExamViewModelReportingTest`.
- **Implemented in:** `feature-exam` (question/review UI at ExamScreen.kt:402, ReviewScreen.kt:310) using `QuestionReportRepository` from `core-data` and admin views in `app-android`.
- **Recent additions:**
  - [x] In-app "Report issue" action on exam and review screens captures questionId, locale, and optional comment
  - [x] Wired to `QuestionReportRepository` for submission with offline queueing
  - [x] Admin dashboard shows list of reports with counts per question and recent comments
  - [x] Comprehensive UI/unit test coverage (`QuestionReportUiTest`, `AdminReportsUiTest`, `ExamViewModelReportingTest`)
- **Next tasks:**
  - None - flow is complete and tested.
  
### ERROR-1 – Crash/analytics reporting
- **Status:** ✅
- **Summary:** Crashlytics and Analytics hooks are wired via `app-android` build config and guarded by debug flags, with a debug-only Crashlytics test crash entry in Settings to validate symbol uploads. Symbol upload runs automatically during release builds when `google-services.json` is present (via Crashlytics Gradle plugin).
- **Implemented in:** `app-android` build config (Crashlytics Gradle plugin), `core-data` analytics helpers.
- **Recent additions:**
  - [x] Documented Crashlytics symbol upload in `docs/RELEASE_CHECKLIST.md` (automatic during bundleRelease)
  - [x] Symbol upload verification steps added to release checklist
  - [x] Manual network error testing checklist created (`docs/manual_error_network_tests.md`)
- **Next tasks:**
  - [x] Document Crashlytics symbol upload process in release checklist (completed)
  - [ ] Optionally add CI job to verify symbol upload for release builds (requires Firebase credentials in CI).

### ERROR-2 – User-facing error report dialog
- **Status:** ✅
- **Summary:** User-facing error reporting dialog (`AppErrorReportDialog`) captures context and feedback when errors occur. Centralized `AppErrorHandler` routes errors to logs/Crashlytics, tracks recent events for admin diagnostics, and emits UI events to trigger the dialog; submission respects analytics opt-out and PII constraints. Comprehensive test coverage validates analytics ON/OFF gating, and offline/retry scenarios.
- **Implemented in:** `app-android` (`AppErrorHandler`, `AppErrorReportDialog`, wired in `QWeldApp`/`AppNavGraph`) with shared error models in `core-common`; UI/instrumentation coverage via `ErrorDialogUiTest` and unit tests (`AppErrorHandlerTest`). Offline queue/retry tests in `FirestoreQuestionReportRepositoryTest`.
- **Recent additions:**
  - [x] AppErrorHandlerTest validates analytics ON/OFF gating (lines 68-114: analyticsEnabled_reflectsBuildFlagAndUserOptIn, submitReport_respectsAnalyticsToggle)
  - [x] Crashlytics submission respects user analytics opt-out (no reports sent when disabled)
  - [x] FirestoreQuestionReportRepositoryTest validates offline queue/retry scenarios (lines 43-114)
  - [x] Manual network error testing checklist created (`docs/manual_error_network_tests.md`)
  - [x] Firestore security rules documentation created (`docs/firestore_security_notes.md`)
- **Next tasks:**
  - [ ] Propagate handler usage through more feature screens so unexpected errors surface the dialog consistently.
  - [ ] Add more comprehensive UI/instrumentation scenarios (submission success/failure states, network error dialogs).
  - [ ] Continue reviewing Crashlytics payloads to ensure user comments remain free of PII and align with privacy expectations.


## Testing & QA

### TEST-1 – Domain & content unit tests
- **Status:** ✅
- **Summary:** Domain samplers/quota utilities and content loaders are covered by unit tests and validators, including edge-case quota distribution (rounding/large-small quotas). DI configuration and controller contracts have comprehensive regression tests. Blueprint/manifest snapshot tests guard against unintended structural changes.
- **Notes:** Locale fallback for `AssetQuestionRepository` now explicitly covers RU present/missing/corrupt scenarios. Post-DI/refactor regression tests verify bindings and controller behavior.
- **Implemented in:** `feature-exam` tests for loaders/content, `core-domain` unit tests, DI configuration tests (`AppModuleConfigTest`, `ExamModuleConfigTest`), controller tests (`ExamTimerControllerTest`, `ExamPrewarmCoordinatorTest`, `ExamAutosaveControllerTest`), `scripts/generate-blueprint-snapshots.sh` and `tests/snapshots/`.
- **Recent additions:**
  - [x] Added blueprint/manifest snapshot test infrastructure (`generate-blueprint-snapshots.sh`)
  - [x] Snapshot tests verify blueprint metadata, sorted tasks with quotas, and manifest locale totals
  - [x] Documented snapshot update procedure in `tests/snapshots/README.md`
  - [x] Pre-release validation runbook created at `docs/release_checks.md`
- **Next tasks:**
  - [x] Expand quota distribution edge-case coverage
  - [x] Add explicit EN↔RU locale fallback tests for `AssetQuestionRepository`
  - [x] Add DI configuration and controller contract tests post-refactor
  - [x] Automate snapshot tests for blueprint manifests
  - [x] Enforce RU locale coverage via CI gate

### TEST-2 – UI/instrumentation coverage
- **Status:** ✅
- **Summary:** Compose/UI coverage now includes exam submit/resume flows (timer state, answered choices), full practice happy path, localization toggle test, and admin/report screen rendering tests. DI integration test validates complete Hilt graph and singleton scoping.
- **Implemented in:** `feature-exam` UI tests, `app-android` UI tests (`LocaleSwitchUiTest`, `AdminReportsUiTest`, `ErrorDialogUiTest`), DI integration test (`HiltDiIntegrationTest`).
- **Recent additions:**
  - [x] LocaleSwitchUiTest validates EN/RU locale switching and label updates (app-android/src/androidTest/java/com/qweld/app/i18n/LocaleSwitchUiTest.kt)
  - [x] AdminReportsUiTest covers admin report list and detail screen rendering (app-android/src/androidTest/java/com/qweld/app/admin/AdminReportsUiTest.kt)
  - [x] ErrorDialogUiTest validates error report dialog submission flow (app-android/src/androidTest/java/com/qweld/app/error/ErrorDialogUiTest.kt)
- **Next tasks:**
  - [x] Add end-to-end practice runs with answer submission and review.
  - [x] Add DI integration test to verify Hilt bindings work end-to-end.
  - [x] Cover localization toggles and admin/report screens.

### TEST-3 – Regression testing for admin/adaptive/reporting flows
- **Status:** ✅
- **Summary:** Comprehensive regression tests cover admin/debug UI, adaptive exam mode, question reporting, and error reporting flows. Includes unit tests for payload structure/PII, instrumentation for question reporting UI, admin list/detail screens, adaptive exam smoke test, `AppErrorHandler` (Crashlytics gating + UI events), error dialog submission, and queued/offline question reporting.
- **Implemented in:** `feature-exam` UI tests (`QuestionReportUiTest`, `AdaptiveExamUiTest`, `ExamViewModelReportingTest`), domain tests in `core-domain` (`DefaultAdaptiveExamPolicyTest`, `AdaptiveExamAssemblerSamplerTest`), app-android tests (`AdminReportsUiTest`, `ErrorDialogUiTest`, `AppErrorHandlerTest`).
- **Next tasks:**
  - [ ] Manually exercise error paths to confirm Crashlytics/diagnostics and in-app error dialog behave correctly in various network conditions.
  - [ ] Add more edge-case scenarios for adaptive difficulty transitions and queued report retries.

## Architecture & Refactoring

### ARCH-1 – Separation of layers
- **Status:** ✅
- **Summary:** Clean separation between app shell, features, data, and domain layers with shared utilities.
- **Implemented in:** Gradle module graph (`app-android`, `feature-*`, `core-*`).
- **Next tasks:**
  - [x] Keep module boundaries enforced during new feature work (periodic guardrail checks now include an Android-free import scan for `core-domain`).

### ARCH-2 – Dependency injection framework
- **Status:** ✅
- **Summary:** Hilt supplies app/data/exam dependencies via `AppModule` and `ExamModule`, injects Application/Activity (`@HiltAndroidApp`, `@AndroidEntryPoint`), and drives ViewModels via `@HiltViewModel`. Test overrides available via `@TestInstallIn` modules (`TestExamModule`). Comprehensive regression tests verify DI configuration and wiring.
- **Implemented in:** Hilt modules in `app-android` (`AppModule`) and `feature-exam` (`ExamModule`); `QWeldApp`, `MainActivity`, `ExamViewModel` use Hilt injection; `TestExamModule` provides test overrides.
- **Test coverage:**
  - [x] DI configuration tests verify AppModule and ExamModule bindings (`AppModuleConfigTest`, `ExamModuleConfigTest`)
  - [x] Integration test validates complete DI graph and singleton scoping (`HiltDiIntegrationTest`)
  - [x] TestExamModule overrides exercised in instrumentation tests
- **Next tasks:**
  - [ ] Expand DI coverage to remaining ViewModels (admin/result/review ViewModels currently use manual factory patterns).
  - [ ] Migrate any remaining manual providers to Hilt bindings.

### ARCH-3 – ExamViewModel refactor
- **Status:** ✅
- **Summary:** `ExamViewModel` now orchestrates dedicated controllers for timers, prewarm, and autosave/resume instead of owning all behaviors directly. Controllers are injected via Hilt and have comprehensive contract tests.
- **Implemented in:** `feature-exam` ViewModels/controllers (`DefaultExamTimerController`, `DefaultExamPrewarmCoordinator`, `DefaultExamAutosaveController`).
- **Test coverage:**
  - [x] Contract tests for ExamTimerController verify timer start/resume/stop behavior (`ExamTimerControllerTest`)
  - [x] Contract tests for ExamPrewarmCoordinator verify prewarm orchestration and progress tracking (`ExamPrewarmCoordinatorTest`)
  - [x] Contract tests for ExamAutosaveController verify autosave lifecycle and answer recording (`ExamAutosaveControllerTest`)
  - [x] Controllers are DI-wired via ExamModule and tested in integration tests
- **Next tasks:**
  - [ ] Continue expanding controller-level tests as new edge cases are discovered.

## Documentation

### DOCS-1 – Update documentation for new features
- **Status:** ✅
- **Summary:** High-level and internal docs updated to reflect admin/debug tools, adaptive exam mode, question reporting, error reporting, DI framework, and controller refactoring.
- **Implemented in:** `PROJECT_OVERVIEW.md` (architecture, key components, user flows, glossary), `MODULES.md` (layer dependencies, module responsibilities, DI wiring), `CONTENT_GUIDE.md` (RU coverage enforcement), `stage.md` (updated statuses and Next tasks), `FILE_OVERVIEW.md` (new files and importance ratings).
- **Next tasks:**
  - [ ] Add feature-specific docs or ADRs for adaptive policy tuning and DI migration patterns if architectural decisions need deeper documentation.
  - [ ] Update deployment/release docs when adaptive mode exits beta or when admin tools need production access patterns.

