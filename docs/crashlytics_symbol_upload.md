# Crashlytics Symbol Upload Runbook

## Prerequisites
- `app-android/google-services.json` is present (or `-PwithGoogleServices=true` is passed). Without it, the Crashlytics plugin and mapping upload tasks are skipped.
- Use a release build; debug builds keep mapping uploads disabled.

## Release bundle steps
1. Run `./gradlew :app-android:bundleRelease --no-daemon --stacktrace`.
2. Gradle will generate `app-android/build/outputs/mapping/release/mapping.txt` and invoke `uploadCrashlyticsMappingFileRelease` (gated to run only when Google services are available).
3. Capture the build output for the upload task; it should report a successful upload or surface any HTTP errors.

## How to confirm the upload
- In Firebase console: open **Crashlytics → Release management → Symbols** and verify the latest version code/build time has an uploaded mapping.
- Alternatively, rerun a release build and confirm `uploadCrashlyticsMappingFileRelease` executes (not `SKIPPED`) in the Gradle task graph.

## Troubleshooting
- **Upload task skipped:** Ensure `google-services.json` exists in `app-android/` or pass `-PwithGoogleServices=true`. Re-run the build.
- **HTTP/network errors:** Re-run the bundle command with `--info` to surface HTTP status; check that the device/network allows outbound requests to Firebase endpoints.
- **Mismatched version code:** Delete old build outputs, then rebuild to regenerate `mapping.txt` with the current version code (auto-generated via the UTC timestamp-based `autoVersionCode`).
