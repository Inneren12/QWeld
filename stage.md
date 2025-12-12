# QWeld – Stages & Status

Legend:
- ✅ Done
- ⚠️ Partially done / needs polish
- ⏳ Planned / not implemented yet
- 🧪 Experimental / prototype

## Exam & Practice Flows

### EXAM-1 – Exam mode (full IP blueprint)
- **Status:** ✅
- **Summary:** Full-length exam runs follow the Interprovincial blueprint with timers, autosave/resume, and results/review screens. Added UI/instrumentation coverage around submit/resume plus timer stability checks.
- **Implemented in:** `feature-exam` (`ExamViewModel`, `ResultViewModel`, `ReviewViewModel`), `core-domain` (quota distribution, timers), asset blueprints under `content/blueprints/`.
- **Next tasks:**
  - [x] Exercise timer alignment through full activity recreation/backgrounded emulator runs (beyond unit coverage).
  - [ ] Cover Room-backed resume after process death to ensure autosave snapshots and timer restore correctly.

### EXAM-2 – Practice mode (configurable)
- **Status:** ✅
- **Summary:** Practice sessions allow selecting tasks/blocks and question counts with proportional or even sampling and review filters, now with clearer selection helpers (select/clear all, selection count hints) and automatic persistence of the last used setup for quick reuse.
- **Implemented in:** `feature-exam` (`ExamViewModel`, practice config UI/models), `core-domain` samplers.
- **Next tasks:**
  - [x] Improve task selection UX (multi-select presets, clearer quotas).
  - [x] Add saved presets/tests for frequent practice mixes.
  - [ ] Expand preset management beyond “last used” (named presets, sharing).
 
### EXAM-3 – Adaptive exam mode
- **Status:** ⏳
- **Summary:** Adaptive exam mode that adjusts the difficulty of subsequent questions based on the user’s performance (correct/incorrect streaks).
- **Implemented in:** Planned for `core-domain` (adaptive sampler/strategy) and `feature-exam` (exam flow wiring and UI toggle).
- **Next tasks:**
  - [ ] Design adaptive rules: initial difficulty level, step size for increasing/decreasing difficulty, and min/max bounds.
  - [ ] Implement adaptive question selection in the exam assembly pipeline using existing RNG/samplers.
  - [ ] Add tests for key scenarios (streaks of correct answers, streaks of incorrect answers, alternating answers) to verify difficulty transitions.
  - [ ] Add a user-facing toggle/flag for enabling adaptive mode (beta-only at first).

## Content & Localization

### CONTENT-1 – EN content completeness
- **Status:** ✅
- **Summary:** English blueprints, task bundles, and monolithic banks ship with the app and pass existing validators.
- **Implemented in:** `content/blueprints/`, `content/questions/en/`, asset manifests.
- **Next tasks:**
  - [ ] Periodically re-run content validators before releases.
  - [ ] Expand explanation coverage where missing.

### CONTENT-2 – RU coverage
- **Status:** ⚠️
- **Summary:** Russian localization exists but has partial coverage; loaders fall back to English when gaps occur, and CI now runs a locale coverage gate to prevent regressions.
- **Implemented in:** `content/questions/ru/` plus locale fallback in `AssetQuestionRepository`.
- **Next tasks:**
  - [ ] Close missing translations and sync blueprint metadata.
  - [x] Add locale-completeness checks to CI.

## Admin & Debug Tools

### ADMIN-1 – Content info & asset diagnostics
- **Status:** ⚠️
- **Summary:** Internal screens show asset availability, manifest/version details, and question reports but are not part of the main user flow.
- **Implemented in:** `app-android` admin screens/viewmodels.
- **Next tasks:**
  - [x] Surface asset version/manifest details for QA.
  - [x] Harden error displays for missing bundles.
  - [ ] Add deeper blueprint-to-manifest cross-checks (e.g., quota-aware coverage hints).

### ADMIN-2 – Admin/debug dashboard
- **Status:** ⏳
- **Summary:** Dedicated protected admin/debug panel that surfaces logs, basic usage stats, and storage state to simplify live diagnostics and feature verification.
- **Implemented in:** Planned for `app-android` (admin screens/viewmodels) and `core-data` (exposing stats from DB/logs where available).
- **Next tasks:**
  - [ ] Define access model for the admin panel (debug build gate, hidden gesture, or auth-gated entry).
  - [ ] Implement an admin screen showing recent log events, high-level user activity (attempt counts, completion rates), and basic DB state (total attempts, last attempt time).
  - [ ] Add navigation entry to the admin/debug screen (hidden or debug-only) and verify it does not appear in normal user flows.


## Question Reporting & Error Handling

### REPORT-1 – Question report pipeline
- **Status:** ✅
- **Summary:** Firestore-backed repository exists with offline queueing and retries on app start; UI can submit reports when enabled, metadata (question/locale, blueprint/content versions, app/device info, timestamps) is captured for triage, and failed submissions are persisted for later delivery.
- **Implemented in:** `core-data` (`FirestoreQuestionReportRepository`), admin/report screens in `app-android`.
- **Next tasks:**
  - [x] Add offline queue/retry for reports.
  - [x] Include device/context metadata for triage.
  - [x] Testing: payload structure and PII constraints covered by unit tests.

### REPORT-2 – In-app “Report issue” flow for questions
- **Status:** ⏳
- **Summary:** User-facing reporting flow that lets candidates flag problematic questions directly from exam/review screens, feeding into the existing Firestore pipeline.
- **Implemented in:** Planned for `feature-exam` (question/review UI) using `QuestionReportRepository` from `core-data` and admin views in `app-android`.
- **Next tasks:**
  - [ ] Add a “Report issue” action on question and review screens that captures question ID, locale, and an optional free-text comment.
  - [ ] Wire the UI action to `QuestionReportRepository` so reports are sent (or queued) with enough context for moderation.
  - [ ] Extend the admin/debug dashboard with a list of reported questions (count, latest reports, comments) to support content triage.

### ERROR-1 – Crash/analytics reporting
- **Status:** ✅
- **Summary:** Crashlytics and Analytics hooks are wired via `app-android` build config and guarded by debug flags, with a debug-only Crashlytics test crash entry in Settings to validate symbol uploads.
- **Implemented in:** `app-android` build config, `core-data` analytics helpers.
- **Next tasks:**
  - [ ] Periodically validate Crashlytics symbol upload in CI.

### ERROR-2 – User-facing error report dialog
- **Status:** ⏳
- **Summary:** A user-visible error reporting dialog layered on top of Crashlytics/analytics to capture context and feedback when something goes wrong.
- **Implemented in:** Planned for a central error handler in `app-android` and logging/diagnostics helpers in `core-data`.
- **Next tasks:**
  - [ ] Introduce a central error handling path that can surface a friendly dialog when non-fatal errors occur in critical flows.
  - [ ] Add a “Send report” action that attaches a short user comment and high-level context to Crashlytics/logging, respecting analytics/diagnostics opt-out.
  - [ ] Verify that no PII or sensitive data is logged, and that error reporting behavior matches privacy expectations.


## Testing & QA

### TEST-1 – Domain & content unit tests
- **Status:** ✅
- **Summary:** Domain samplers/quota utilities and content loaders are covered by unit tests and validators, including edge-case quota distribution (rounding/large-small quotas).
- **Notes:** Locale fallback for `AssetQuestionRepository` now explicitly covers RU present/missing/corrupt scenarios.
- **Implemented in:** `feature-exam` tests for loaders/content, `core-domain` unit tests.
- **Next tasks:**
  - [x] Expand quota distribution edge-case coverage.
  - [x] Add explicit EN↔RU locale fallback tests for `AssetQuestionRepository`.
  - [ ] Automate snapshot tests for blueprint manifests.
  - [x] Enforce RU locale coverage via CI gate.

### TEST-2 – UI/instrumentation coverage
- **Status:** ✅
- **Summary:** Compose/UI coverage now includes exam submit/resume flows (timer state, answered choices) and a full practice happy path alongside the exam happy path, asserting result screens and score labels.
- **Implemented in:** `feature-exam` UI tests (partial).
- **Next tasks:**
  - [x] Add end-to-end practice runs with answer submission and review.
  - [ ] Cover localization toggles and admin/report screens.

### TEST-3 – Regression testing for admin/adaptive/reporting flows
- **Status:** ⏳
- **Summary:** Comprehensive regression tests for the new admin/debug UI, adaptive exam mode, question reporting, and error reporting flows. Payload structure/PII coverage for question reports now lives in unit tests; UI/admin coverage still pending.
- **Implemented in:** Planned across `feature-exam` UI tests, domain tests in `core-domain`, and integration tests for reporting/error handling.
- **Next tasks:**
  - [ ] Add end-to-end tests covering an adaptive exam run (difficulty changes as expected for different answer patterns).
  - [ ] Add tests for the admin/debug screen (data visibility, access control) and question reporting UI (report creation, basic validation).
  - [ ] Manually exercise error paths to confirm Crashlytics/diagnostics and the in-app error dialog behave correctly.

## Architecture & Refactoring

### ARCH-1 – Separation of layers
- **Status:** ✅
- **Summary:** Clean separation between app shell, features, data, and domain layers with shared utilities.
- **Implemented in:** Gradle module graph (`app-android`, `feature-*`, `core-*`).
- **Next tasks:**
  - [ ] Keep module boundaries enforced during new feature work.

### ARCH-2 – Dependency injection framework
- **Status:** ⏳
- **Summary:** Current setup uses manual wiring; no dedicated DI framework is enabled yet.
- **Implemented in:** Manual constructors/providers.
- **Next tasks:**
  - [ ] Evaluate lightweight DI (e.g., Koin/Hilt) for ViewModels and repositories.

### ARCH-3 – ExamViewModel refactor
- **Status:** ⚠️
- **Summary:** `ExamViewModel` handles many responsibilities (assembly, timers, autosave, navigation) and would benefit from splitting.
- **Implemented in:** `feature-exam` ViewModels/controllers.
- **Next tasks:**
  - [ ] Extract timers/prewarm/autosave into focused controllers.
  - [ ] Add contract tests around the split to avoid regressions.

## Documentation

### DOCS-1 – Update documentation for new features
- **Status:** ⏳
- **Summary:** Keep high-level and internal docs in sync with the new admin/debug tools, adaptive exam mode, question reporting, and error reporting behavior.
- **Implemented in:** `PROJECT_OVERVIEW.md`, `MODULES.md`, `CONTENT_GUIDE.md`, `stage.md`, `FILE_OVERVIEW.md` (and any feature-specific docs).
- **Next tasks:**
  - [ ] Document how the adaptive exam mode works (difficulty rules, enabling/disabling, limitations) and where it is implemented.
  - [ ] Describe the admin/debug panel (how to open it, what data it shows, and who should use it).
  - [ ] Document the question reporting flow (UI entry points, how reports are stored, how to review them) and the in-app error report dialog.
  - [ ] Add any necessary notes on Crashlytics/analytics configuration and deployment steps for these features.

