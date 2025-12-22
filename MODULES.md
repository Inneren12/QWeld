# Gradle Modules

Module names follow the `app-android`, `feature-*`, and `core-*` pattern. Feature modules hold UI and navigation for user-facing flows, while core modules provide shared domain/data logic. Benchmarks and tools live alongside but outside the main app graph.

## Layer dependencies (high level)
- `app-android` depends on feature modules and all core layers; provides Hilt DI bindings via `AppModule`.
- `feature-*` modules depend on `core-domain`, `core-data`, `core-common`, and `core-model`; declare feature-specific DI modules (e.g., `ExamModule`).
- `core-data` depends on `core-domain` and `core-common` for models and utilities.
- `core-domain` is pure Kotlin/logic with no Android dependency and feeds both data and feature layers.
- `core-common` and `core-model` sit at the bottom as shared utilities and lightweight models.
- **DI wiring:** Hilt modules bind repositories, use cases, controllers, and ViewModels; test modules (`@TestInstallIn`) override bindings for instrumentation.

## app-android
- **Type:** Android application
- **Responsibilities:** Application entry point, Compose navigation host, BuildConfig/version stamping, analytics flags, asset packaging/verification (`verifyAssets`), centralized error handling, and admin/debug tools. Houses bundled blueprints, question banks, per-task bundles, and index manifests.
- **Key entry points / files:** `MainActivity` (launcher), asset README, app-level build script with Crashlytics/Analytics toggles, `AppModule.kt` (Hilt DI bindings).
- **Key classes:**
  - `MainActivity` – launcher activity that sets the Compose hierarchy and hosts the nav graph.
  - `AppNavGraph` / `AppNavGuards` – top-level navigation routes and guards that stitch feature destinations.
  - `QWeldApp` – Application class (Hilt-enabled with `@HiltAndroidApp`) that seeds logging, locale controller, analytics toggles, and error handler.
  - `AppErrorHandler` – centralized non-fatal error handler that logs errors, forwards to Crashlytics (when analytics enabled), tracks recent error history, and emits UI events for error dialogs.
  - `AppModule` – Hilt module binding app-wide dependencies (analytics, repositories, DB, error handler, question/explanation repos, auth services).
  - `AdminDashboardViewModel` / `AdminDashboardScreen` – debug-only admin dashboard (Settings → Tools) showing attempt stats, DB health, queued reports, and recent errors; ViewModel is Hilt-injected.
  - `QuestionReportsViewModel` / `QuestionReportsScreen` – debug-only question report listing and detail views for content triage.
  - `AppErrorReportDialog` – user-facing dialog for reporting app errors with optional comments.
- **Depends on:** `feature-exam`, `feature-auth`, `core-domain`, `core-data`, `core-model`, `core-common`.

## feature-exam
- **Type:** Android library (Compose feature)
- **Responsibilities:** Exam and practice UI/screens, navigation graph, ViewModels for attempt lifecycle (injected via `@HiltViewModel`), explanation/review flows, asset-backed question repository with locale fallback, blueprint loader, timers, pre-warm logic, adaptive exam assembly, and analytics hooks.
- **Key entry points / folders:** `navigation/ExamNavGraph.kt`, data layer (`AssetQuestionRepository`, `BlueprintJsonLoader`), models (UI/result/review), explanation repository, controllers (`ExamTimerController`, `ExamPrewarmCoordinator`, `ExamAutosaveController`), DI module (`ExamModule.kt`), and tests that validate content coverage, loaders, and adaptive behavior.
- **Key classes:**
  - `ExamViewModel` – orchestrates exam and practice attempts by delegating to specialized controllers for timers, prewarm, and autosave; drives assembly (standard or adaptive), navigation, and persistence.
  - `ExamTimerController` / `ExamPrewarmCoordinator` / `ExamAutosaveController` – focused controllers handling timer ticking/expiry, bundle preloading, and autosave lifecycle independently.
  - `ResultViewModel` / `ReviewViewModel` – Hilt-injected ViewModels for results presentation and answer review filtering; read the latest result via a scoped holder.
  - `AssetQuestionRepository` – loads task bundles/monolithic banks with locale fallback and caching.
  - `PrewarmUseCase` / `PrewarmController` – preloads bundles before launching an attempt to avoid jank.
  - `BlueprintJsonLoader` – parses blueprint JSONs and resolves block/task metadata for loaders.
  - `ExamModule` – Hilt module binding exam-specific dependencies (blueprint resolver, timers, prewarm/resume use cases/controllers).
  - `TestExamModule` – Hilt test overrides (`@TestInstallIn`) for deterministic fakes in instrumentation.
- **Depends on:** `core-domain`, `core-data`, `core-common` plus Compose, Navigation, Hilt, and serialization libraries.

## feature-auth
- **Type:** Android library (Compose feature)
- **Responsibilities:** Authentication flows (guest/Google/email-password), account linking, and related UI screens plus helper services for Firebase Auth integration.
- **Key entry points / folders:** `AuthService`, `FirebaseAuthService`, `GoogleCredentialSignInManager`, UI screens under `ui/` (e.g., `SignInScreen`, `LinkAccountScreen`).
- **Depends on:** `core-domain` (shared models), `core-common`, Firebase Auth, Compose Navigation.

## core-domain
- **Type:** Android/Kotlin library (pure domain logic)
- **Responsibilities:** Exam assembly engine: quota distribution, random number generators (PCG32), weighted sampling, shufflers, timers, task/block mapping helpers, and adaptive exam policies for difficulty-based question selection.
- **Key entry points / folders:** `exam/` utilities (`QuotaDistributor`, `WeightedSampler`, `TaskBlockMapper`, `ExamAssembler`, `ExamAssemblerFactory`), `adaptive/` policies (`AdaptiveExamPolicy`, `AdaptiveExamAssembler`), `Outcome` sealed model, timer utilities.
- **Key classes:**
  - `QuotaDistributor` – allocates question counts across blocks/tasks based on blueprint quotas.
  - `WeightedSampler` / `PCGRandom` – deterministic RNG and weighted selection utilities used for building attempts.
  - `TaskBlockMapper` – maps task IDs to block metadata for UI and scoring.
  - `TimerState` utilities – provide elapsed/remaining timer calculations for attempts.
  - `ExamAssembler` / `ExamAssemblerFactory` – builds exam/practice sessions; factory creates standard or adaptive assemblers.
  - `AdaptiveExamPolicy` / `DefaultAdaptiveExamPolicy` – defines difficulty bands, streak thresholds, and hysteresis rules for adaptive mode.
  - `AdaptiveExamAssembler` – adaptive sampler that adjusts difficulty based on user performance (correct/incorrect streaks).
- **Depends on:** Kotlin stdlib only; consumed by data/feature modules.

## core-data
- **Type:** Android library (data layer)
- **Responsibilities:** Persistence and integrations: Room database for attempts/answers/queued reports, DataStore-based user preferences, optional Firestore-backed question reporting with offline queueing and retry, analytics flags, and asset-driven repositories shared with features.
- **Key entry points / folders:** `data/db` (Room DAOs/entities, `QWeldDb`), `data/prefs` (`UserPrefsDataStore`, `UserPrefs`), `data/reports` (Firestore/question reporting, payload builders, retry use cases), `data/content` (manifest diagnostics), `data/` repositories.
- **Key classes:**
  - `QWeldDb` with `AttemptDao` / `QuestionAnswerDao` / `QueuedQuestionReportDao` – persists attempts, answers, progress snapshots, and queued question reports for offline retry.
  - `UserPrefsDataStore` – stores locale, practice presets, analytics toggles, and adaptive exam opt-in via DataStore.
  - `FirestoreQuestionReportRepository` – pushes question issue reports to Firestore when enabled; queues locally when offline and retries on app start.
  - `QuestionReportPayloadBuilder` / `ReportEnvironmentMetadata` – builds sanitized Firestore payloads with locale, blueprint, app, and device metadata (PII-free).
  - `RetryQueuedQuestionReportsUseCase` – retries queued reports with configurable attempt/batch limits.
  - `AssetQuestionRepository` and related content repos – shared asset readers for question banks/bundles.
  - `ContentManifestDiagnostics` – reads aggregated content manifest and computes admin-facing diagnostics/statuses.
- **Depends on:** `core-domain`, `core-common`, Room, DataStore, Timber, Firebase Analytics/Firestore.

## core-common
- **Type:** Android library (utilities)
- **Responsibilities:** Shared environment/config helpers, logging utilities, and error handler interfaces used across modules.
- **Key entry points / folders:** `AppEnv`, `logging/Logx.kt`, `error/AppErrorHandler.kt` (interface and shared error models).
- **Key classes:**
  - `AppErrorHandler` interface – defines non-fatal error handling contracts (history, UI events, Crashlytics/report submission hooks).
  - Shared error models – platform-agnostic, PII-free error representations for cross-module error handling.
- **Depends on:** Kotlin/AndroidX core.

## core-model
- **Type:** Kotlin library (models)
- **Responsibilities:** Small shared model surface currently limited to greeting/sample placeholders; reserved for cross-module model growth.
- **Key entry points / folders:** `GreetingProvider`.
- **Depends on:** Kotlin stdlib.

## benchmarks-jvm
- **Type:** JMH benchmark module (JVM)
- **Responsibilities:** Placeholder for performance benchmarks (configured with the JMH Gradle plugin); no source files yet.
- **Key entry points / folders:** N/A (scaffold only).
- **Depends on:** JMH plugin; isolated from Android modules.
