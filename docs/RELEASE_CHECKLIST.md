# QWeld Release Checklist (Android)

Use this runbook for every Google Play promotion from Internal testing to Production. Tick each step and capture evidence (screenshots, build logs) before moving on.

## 1. Prep the build
- [ ] Confirm `main` is green (CI) and branch is up to date.
- [ ] Update versionName/versionCode in `app-android/build.gradle.kts` (see [versioning_policy.md](versioning_policy.md)).
- [ ] Update policy dates (Privacy, Data Safety form) if wording changed.
- [ ] Regenerate changelog / release notes.

## 2. Build artifacts
- [ ] `./gradlew :app-android:bundleRelease` (details in [docs/release/bundle_release.md](release/bundle_release.md))
  - Note: Crashlytics mapping file upload runs automatically during this task when `google-services.json` is present
  - Verify symbol upload succeeded by checking Firebase Console → Crashlytics → Mappings for the build version
  - CI workflow (`.github/workflows/android.yml`) conditionally enables symbol upload when `GOOGLE_SERVICES_JSON_B64` secret is set
- [ ] `./gradlew :core-model:publishAllPublicationsToMavenLocal` (if model/schema updates)
- [ ] Verify release bundle is signed with the Play key (use `apksigner verify`).

## 3. Content pipeline
- [ ] `./gradlew :tools:buildDist` to regenerate content bundle.
- [ ] Sync generated assets into `app-android/src/main/assets/` (use `./gradlew :tools:syncToAssets`).
- [ ] Run `./gradlew :app-android:verifyAssets` to ensure hashes match.
- [ ] Commit asset manifest changes and tag release candidate.

## 4. QA sanity
- [ ] Install the generated `.aab` via internal sharing link or `bundletool`.
- [ ] Smoke test sign-in, practice session, offline mode, logs export, analytics opt-in/out.
- [ ] Run the [release smoke checklist](release_smoke_checklist.md) for install → exam → practice → locale switch → offline report.
- [ ] Capture screenshots for Play listing (7", 10", phone) if UI changed.

## 5. Play Console submission
- [ ] Upload the `.aab` to **Internal testing** track and run pre-launch report.
- [ ] After sign-off, promote the release to **Closed testing** (or directly to Production when stable).
- [ ] Fill out the Data Safety form using the template below (update answers if data handling changed).
- [ ] Update Play listing: release notes, screenshots, privacy policy URL (https://raw.githubusercontent.com/Inneren12/QWeld/main/docs/PRIVACY.md), contact email.

### Data Safety form (draft answers)
- **Data collection**: App collects account email (user provided), crash logs (collected automatically), optional analytics (user choice).
- **Data sharing**: Not shared with third parties outside Firebase processors.
- **Data use**: App functionality, analytics, fraud prevention.
- **Data handling**: Data is encrypted in transit; users can request deletion via privacy@qweld.app; analytics is optional.
- **Deletion**: Users can clear local data in-app and request account deletion via support.

## 6. Post-release
- [ ] Tag the release in Git (`git tag vX.Y.Z && git push origin vX.Y.Z`).
- [ ] Publish release notes in the docs/CHANGELOG (if applicable).
- [ ] Monitor Crashlytics dashboard and respond to issues within 24h.
