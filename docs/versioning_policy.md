# Versioning policy

Use this policy when setting `versionName`/`versionCode` for Android builds.

## versionCode
- Defined in `app-android/build.gradle.kts` as `autoVersionCode`, generated from the UTC timestamp `yyMMddHH` when Gradle runs.
- Do **not** hardcode or backdate `versionCode`; regenerate by re-running the build when creating a new release artifact.
- Record the generated value with each uploaded bundle so Crashlytics symbols and Play Console entries stay aligned.

## versionName
- Default `versionName` lives in `app-android/build.gradle.kts` and should follow **MAJOR.MINOR.PATCH**.
- Increment:
  - **MAJOR** for incompatible changes or significant UI/flow overhauls.
  - **MINOR** for new features that remain backward compatible.
  - **PATCH** for bug fixes, content refreshes, and build-only changes.
- Update `versionName` explicitly in Git when preparing a release branch so the Play listing matches the artifact.

## Track mapping
- **Internal testing:** accepts any `versionName`; used for smoke and pre-release validation.
- **Closed/Beta:** use release-candidate builds with the target `versionName`; ensure `versionCode` is newer than internal builds.
- **Production:** promote the same artifact from beta/closed testing once validated; avoid rebuilding to preserve the `versionCode` and mapping pair.
