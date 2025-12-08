# QWeld – Stages & Status

Legend:
- ✅ Done
- ⚠️ Partially done / needs polish
- ⏳ Planned / not implemented yet
- 🧪 Experimental / prototype

## Exam & Practice Flows

### EXAM-1 – Exam mode (full IP blueprint)
- **Status:** ✅
- **Summary:** Full-length exam runs follow the Interprovincial blueprint with timers, autosave/resume, and results/review screens.
- **Implemented in:** `feature-exam` (`ExamViewModel`, `ResultViewModel`, `ReviewViewModel`), `core-domain` (quota distribution, timers), asset blueprints under `content/blueprints/`.
- **Next tasks:**
  - [ ] Add more UI/instrumentation coverage for submit/resume edge cases.
  - [ ] Verify timer stability across background/rotation events.

### EXAM-2 – Practice mode (configurable)
- **Status:** ✅
- **Summary:** Practice sessions allow selecting tasks/blocks and question counts with proportional or even sampling and review filters.
- **Implemented in:** `feature-exam` (`ExamViewModel`, practice config UI/models), `core-domain` samplers.
- **Next tasks:**
  - [ ] Improve task selection UX (multi-select presets, clearer quotas).
  - [ ] Add saved presets/tests for frequent practice mixes.

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
- **Summary:** Russian localization exists but has partial coverage; loaders fall back to English when gaps occur.
- **Implemented in:** `content/questions/ru/` plus locale fallback in `AssetQuestionRepository`.
- **Next tasks:**
  - [ ] Close missing translations and sync blueprint metadata.
  - [ ] Add locale-completeness checks to CI.

## Admin & Debug Tools

### ADMIN-1 – Content info & asset diagnostics
- **Status:** ⚠️
- **Summary:** Internal screens show asset availability and question reports but are not part of the main user flow.
- **Implemented in:** `app-android` admin screens/viewmodels.
- **Next tasks:**
  - [ ] Surface asset version/manifest details for QA.
  - [ ] Harden error displays for missing bundles.

## Question Reporting & Error Handling

### REPORT-1 – Question report pipeline
- **Status:** ⚠️
- **Summary:** Firestore-backed repository exists; UI can submit reports when enabled, but moderation workflow is light.
- **Implemented in:** `core-data` (`FirestoreQuestionReportRepository`), admin/report screens in `app-android`.
- **Next tasks:**
  - [ ] Add offline queue/retry for reports.
  - [ ] Include device/context metadata for triage.

### ERROR-1 – Crash/analytics reporting
- **Status:** ✅
- **Summary:** Crashlytics and Analytics hooks are wired via `app-android` build config and guarded by debug flags.
- **Implemented in:** `app-android` build config, `core-data` analytics helpers.
- **Next tasks:**
  - [ ] Periodically validate Crashlytics symbol upload in CI.

## Testing & QA

### TEST-1 – Domain & content unit tests
- **Status:** ✅
- **Summary:** Domain samplers/quota utilities and content loaders are covered by unit tests and validators.
- **Implemented in:** `feature-exam` tests for loaders/content, `core-domain` unit tests.
- **Next tasks:**
  - [ ] Expand quota distribution edge-case coverage.
  - [ ] Automate snapshot tests for blueprint manifests.

### TEST-2 – UI/instrumentation coverage
- **Status:** ⚠️
- **Summary:** Limited Compose/UI tests exist; navigation and error handling paths need broader coverage.
- **Implemented in:** `feature-exam` UI tests (partial).
- **Next tasks:**
  - [ ] Add end-to-end exam/practice runs with answer submission.
  - [ ] Cover localization toggles and admin/report screens.

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
