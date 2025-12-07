# Gradle Modules

Module names follow the `app-android`, `feature-*`, and `core-*` pattern. Feature modules hold UI and navigation for user-facing flows, while core modules provide shared domain/data logic. Benchmarks and tools live alongside but outside the main app graph.

## app-android
- **Type:** Android application
- **Responsibilities:** Application entry point, Compose navigation host, BuildConfig/version stamping, analytics flags, and asset packaging/verification (`verifyAssets`). Houses bundled blueprints, question banks, per-task bundles, and index manifests.
- **Key entry points / files:** `MainActivity` (launcher), asset README, app-level build script with Crashlytics/Analytics toggles.
- **Depends on:** `feature-exam`, `feature-auth`, `core-domain`, `core-data`, `core-model`, `core-common`.

## feature-exam
- **Type:** Android library (Compose feature)
- **Responsibilities:** Exam and practice UI/screens, navigation graph, ViewModels for attempt lifecycle, explanation/review flows, asset-backed question repository with locale fallback, blueprint loader, timers, pre-warm logic, and analytics hooks.
- **Key entry points / folders:** `navigation/ExamNavGraph.kt`, data layer (`AssetQuestionRepository`, `BlueprintJsonLoader`), models (UI/result/review), explanation repository, and tests that validate content coverage and loaders.
- **Depends on:** `core-domain`, `core-data`, `core-common` plus Compose, Navigation, and serialization libraries.

## feature-auth
- **Type:** Android library (Compose feature)
- **Responsibilities:** Authentication flows (guest/Google/email-password), account linking, and related UI screens plus helper services for Firebase Auth integration.
- **Key entry points / folders:** `AuthService`, `FirebaseAuthService`, `GoogleCredentialSignInManager`, UI screens under `ui/` (e.g., `SignInScreen`, `LinkAccountScreen`).
- **Depends on:** `core-domain` (shared models), `core-common`, Firebase Auth, Compose Navigation.

## core-domain
- **Type:** Android/Kotlin library (pure domain logic)
- **Responsibilities:** Exam assembly engine: quota distribution, random number generators (PCG32), weighted sampling, shufflers, timers, and task/block mapping helpers.
- **Key entry points / folders:** `exam/` utilities (`QuotaDistributor`, `WeightedSampler`, `TaskBlockMapper`), `Outcome` sealed model, timer utilities.
- **Depends on:** Kotlin stdlib only; consumed by data/feature modules.

## core-data
- **Type:** Android library (data layer)
- **Responsibilities:** Persistence and integrations: Room database for attempts/answers, DataStore-based user preferences, optional Firestore-backed question reporting, analytics flags, and asset-driven repositories shared with features.
- **Key entry points / folders:** `data/db` (Room DAOs/entities, `QWeldDb`), `data/prefs` (`UserPrefsDataStore`, `UserPrefs`), `data/reports` (Firestore/question reporting), `data/` repositories.
- **Depends on:** `core-domain`, `core-common`, Room, DataStore, Timber, Firebase Analytics/Firestore.

## core-common
- **Type:** Android library (utilities)
- **Responsibilities:** Shared environment/config helpers and logging utilities used across modules.
- **Key entry points / folders:** `AppEnv`, `logging/Logx.kt`.
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
