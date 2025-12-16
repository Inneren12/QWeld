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
- **Summary:** English blueprints, task bundles, and monolithic banks ship with the app and pass existing validators.
- **Implemented in:** `content/blueprints/`, `content/questions/en/`, asset manifests.
- **Next tasks:**
  - [ ] Periodically re-run content validators before releases.
  - [ ] Expand explanation coverage where missing.

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
- **Summary:** Internal screens show asset availability, manifest/version details, and question reports; includes `ContentManifestDiagnostics` for admin-facing content health checks.
- **Implemented in:** `app-android` admin screens/viewmodels and `core-data` (`ContentManifestDiagnostics`).
- **Next tasks:**
  - [ ] Add deeper blueprint-to-manifest cross-checks (e.g., quota-aware coverage hints).
  - [ ] Surface content versioning timeline and update history in admin views.

### ADMIN-2 – Admin/debug dashboard
- **Status:** ✅
- **Summary:** Debug-only admin dashboard (Settings → Tools, `BuildConfig.DEBUG` gated) surfaces attempt counts, last completion timestamp, failure totals, answer row counts, DB version health, queued question reports count, and recent error summaries.
- **Implemented in:** `app-android` (`AdminDashboardScreen`, `AdminDashboardViewModel`) with guarded navigation; `core-data` attempt/answer/report stats queries and error handler integration.
- **Next tasks:**
  - [ ] Layer in recent log snippets and expanded error context.
  - [ ] Expand health checks beyond version (integrity checks, migration audit trail).
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

### REPORT-2 – In-app “Report issue” flow for questions
- **Status:** ⚠️
- **Summary:** User-facing reporting flow now surfaces on exam and review screens with a dialog for reasons/comments and snackbar feedback; submissions flow through `QuestionReportRepository` with queued fallback. Admin listing now includes summaries, detail views, and badges when a report followed a recent error.
- **Implemented in:** `feature-exam` (question/review UI) using `QuestionReportRepository` from `core-data` and admin views in `app-android`.
- **Next tasks:**
  - [x] Extend the admin/debug dashboard with a list of reported questions (count, latest reports, comments) to support content triage.
  - [x] Add a “Report issue” action on question and review screens that captures question ID, locale, and an optional free-text comment.
  - [x] Wire the UI action to `QuestionReportRepository` so reports are sent (or queued) with enough context for moderation.
  
### ERROR-1 – Crash/analytics reporting
- **Status:** ✅
- **Summary:** Crashlytics and Analytics hooks are wired via `app-android` build config and guarded by debug flags, with a debug-only Crashlytics test crash entry in Settings to validate symbol uploads.
- **Implemented in:** `app-android` build config, `core-data` analytics helpers.
- **Next tasks:**
  - [ ] Periodically validate Crashlytics symbol upload in CI.

### ERROR-2 – User-facing error report dialog
- **Status:** ✅
- **Summary:** User-facing error reporting dialog (`AppErrorReportDialog`) captures context and feedback when errors occur. Centralized `AppErrorHandler` routes errors to logs/Crashlytics, tracks recent events for admin diagnostics, and emits UI events to trigger the dialog; submission respects analytics opt-out and PII constraints.
- **Implemented in:** `app-android` (`AppErrorHandler`, `AppErrorReportDialog`, wired in `QWeldApp`/`AppNavGraph`) with shared error models in `core-common`; UI/instrumentation coverage via `ErrorDialogUiTest` and unit tests (`AppErrorHandlerTest`).
- **Next tasks:**
  - [ ] Propagate handler usage through more feature screens so unexpected errors surface the dialog consistently.
  - [ ] Add more comprehensive UI/instrumentation scenarios (opt-out gating, submission success/failure states).
  - [ ] Continue reviewing Crashlytics payloads to ensure user comments remain free of PII and align with privacy expectations.


## Testing & QA

### TEST-1 – Domain & content unit tests
- **Status:** ✅
- **Summary:** Domain samplers/quota utilities and content loaders are covered by unit tests and validators, including edge-case quota distribution (rounding/large-small quotas). DI configuration and controller contracts have comprehensive regression tests.
- **Notes:** Locale fallback for `AssetQuestionRepository` now explicitly covers RU present/missing/corrupt scenarios. Post-DI/refactor regression tests verify bindings and controller behavior.
- **Implemented in:** `feature-exam` tests for loaders/content, `core-domain` unit tests, DI configuration tests (`AppModuleConfigTest`, `ExamModuleConfigTest`), controller tests (`ExamTimerControllerTest`, `ExamPrewarmCoordinatorTest`, `ExamAutosaveControllerTest`).
- **Next tasks:**
  - [x] Expand quota distribution edge-case coverage.
  - [x] Add explicit EN↔RU locale fallback tests for `AssetQuestionRepository`.
  - [x] Add DI configuration and controller contract tests post-refactor.
  - [ ] Automate snapshot tests for blueprint manifests.
  - [x] Enforce RU locale coverage via CI gate.

### TEST-2 – UI/instrumentation coverage
- **Status:** ✅
- **Summary:** Compose/UI coverage now includes exam submit/resume flows (timer state, answered choices) and a full practice happy path alongside the exam happy path, asserting result screens and score labels. DI integration test validates complete Hilt graph and singleton scoping.
- **Implemented in:** `feature-exam` UI tests (partial), DI integration test (`HiltDiIntegrationTest`).
- **Next tasks:**
  - [x] Add end-to-end practice runs with answer submission and review.
  - [x] Add DI integration test to verify Hilt bindings work end-to-end.
  - [ ] Cover localization toggles and admin/report screens.

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

