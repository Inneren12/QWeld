# Privacy & Telemetry Validation

## Expected behavior
- **Release default:** `BuildConfig.ENABLE_ANALYTICS` is `true` for release builds and seeds the default analytics preference; analytics/crash reporting run only when this build flag AND the user's opt-in are both true.
- **User opt-out:** The `analyticsEnabled` DataStore preference is the single source of truth. When toggled off, the app disables Firebase Analytics collection and Crashlytics forwarding.
- **Runtime gating:** `MainActivity` listens to `analyticsEnabled` and updates both the analytics wrapper and `AppErrorHandler`; the error handler also re-checks the combined flag before recording non-fatals or sending reports.

## What is sent (high level)
- Anonymous usage analytics events (exam start/finish, answer submit, review/explanation fetch, adaptive toggles) are logged only when enabled.
- Crash reports include app version/build type, error kind/screen/action, and optional short user comment (capped at 500 chars). Recent log snippets may be attached when available.

## Forbidden PII
- Do **not** attach email, name, free-form question text, or identifiers in analytics parameters or error extras.
- User comments are truncated and sanitized before sending; avoid pasting personal data when testing.

## Manual verification (release build)
1. Build a release bundle with `google-services.json` present: `./gradlew :app-android:bundleRelease --no-daemon --stacktrace`.
2. Install the release APK from `app-android/build/outputs/apk/release/` (or extract from the bundle) onto a device with network access.
3. Launch the app with analytics **on** (default). Start an exam and watch logcat for `[analytics] event=` entries; ensure Crashlytics collection is enabled in logcat (`Crashlytics collection enabled`).
4. In Settings → Privacy, toggle Analytics **off**. Confirm subsequent actions only log `[analytics] skipped=true reason=optout` and that error dialogs show "reports disabled" messaging when submitting.
5. Toggle Analytics back **on** and confirm `[analytics] event=` entries resume; trigger an app error (e.g., force a handled exception) and verify Crashlytics receives the non-fatal after re-enable.
