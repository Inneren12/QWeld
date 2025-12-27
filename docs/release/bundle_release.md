# Android release bundle build notes

Use this guide to produce a reproducible `bundleRelease` build and understand what the task emits.

## Prerequisites
- **google-services.json:**
  - Place the file at `app-android/google-services.json` or set `-PwithGoogleServices=true` when invoking Gradle.
  - When absent, the build **skips** Google Services and Crashlytics plugins (see Gradle log line `CI: google-services.json absent → skipping GMS/Crashlytics`).
- **Secrets:**
  - Local builds: rely on the checked-in signing config and local keystore setup; no additional secrets required.
  - CI builds: provide the base64-encoded `GOOGLE_SERVICES_JSON_B64` so `google-services.json` can be restored for symbol upload.
- **Environment:** Java 21 toolchain is configured via Gradle; no manual override needed unless using a custom JDK path.

## Command
Run from repo root:

```bash
./gradlew :app-android:bundleRelease --no-daemon --stacktrace
```

## Expected outputs
- **AAB:** `app-android/build/outputs/bundle/release/app-release.aab`
- **Mapping file:** `app-android/build/outputs/mapping/release/mapping.txt`
- **Crashlytics mapping upload:**
  - Automatically attempted during `bundleRelease` when `google-services.json` is present.
  - Skipped when the file is missing or `-PwithGoogleServices` is not set. Re-run after restoring the file if symbol upload is needed.

## Notes
- VersionCode defaults to the timestamp-based `autoVersionCode` defined in `app-android/build.gradle.kts`; record the generated value alongside the artifact when sharing.
- Keep the generated AAB and mapping together for release sign-off and potential re-upload to Crashlytics.
