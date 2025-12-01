Place question banks under this directory before building the Android app.

Expected structure:
- questions/<locale>/bank.v1.json
- questions/<locale>/tasks/<taskId>.json (15 per locale)

Generate the assets with:
  node scripts/build-questions-dist.mjs

Then copy the contents from dist/questions/<locale>/ into app-android/src/main/assets/questions/<locale>/.

Alternatively, regenerate the per-task bundles, banks, and integrity index directly via Gradle:
  ./gradlew generateIntegrityIndexEn generateIntegrityIndexRu
