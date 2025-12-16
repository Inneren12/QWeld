# Pre-Release Validation Checklist

This document describes the validation steps to run before releasing a new version of QWeld. Following this checklist helps ensure content quality, structural integrity, and a smooth user experience.

---

## Automated Validation Commands

Run these commands in order. Each must pass (exit status 0) before proceeding to the next.

### 1. Asset Verification

Ensure all required asset files are present and correctly placed:

```bash
./gradlew :app-android:verifyAssets
```

**What it checks:**
- Presence of blueprint JSON files in `app-android/src/main/assets/blueprints/`
- Presence of question banks (`bank.v1.json`) per locale
- Presence of per-task bundles (expects 15 task bundles per locale)
- Presence of question manifest (`questions/index.json`)

**If it fails:** Check that you've run `node scripts/build-questions-dist.mjs` and copied outputs to assets.

---

### 2. Blueprint Validation

Validate blueprint JSON structure and schema compliance:

```bash
bash scripts/validate-blueprint.sh
```

**What it checks:**
- Blueprint JSON schema validity
- Required fields (id, blocks, tasks, quotas, versions)
- Consistency of task IDs across blueprints

**If it fails:** Review error output and fix blueprint JSON files in `content/blueprints/`.

---

### 3. Question Validation

Validate question JSON structure, rationales, and content policies:

```bash
bash scripts/validate-questions.sh
```

**What it checks:**
- Question JSON schema validity
- Presence of rationales for correct answers
- Banned phrases (e.g., "real exam", "red seal exam question")
- Consistent question IDs and task mappings

**If it fails:** Fix the offending question files in `content/questions/en/` or `content/questions/ru/`.

---

### 4. Quota Validation

Ensure sufficient questions exist for all tasks in all locales:

```bash
bash scripts/check-quotas.sh
```

**What it checks:**
- Each task has at least `quota` questions available
- Quotas are satisfied for both EN and RU locales
- No excessive or missing questions per task

**If it fails:** Add more questions to the task with gaps or adjust blueprint quotas if intentional.

---

### 5. Explanation Coverage Check

Measure explanation coverage for EN and RU:

```bash
bash scripts/check-explanation-coverage.sh
```

**What it checks:**
- Percentage of EN questions with explanations
- Percentage of EN explanations that have RU translations
- Lists missing explanations (warnings, not failures)

**Expected output:**
- Summary of EN and RU explanation coverage percentages
- Warnings for missing explanations (no CI failure, but good to address before release)

**Recommended action:**
- Review missing explanations and prioritize high-value questions for explanation authoring
- Aim for >10% EN coverage and 100% RU translation of existing explanations

---

### 6. RU Locale Coverage Test

Enforce minimum RU locale coverage via unit test:

```bash
./gradlew :feature-exam:testDebugUnitTest --tests LocaleCoverageTest
```

**What it checks:**
- Computes EN→RU question coverage from source content
- Logs per-task gaps
- Enforces minimum coverage threshold (configured via `localeCoverage.ru.min` gradle property)

**If it fails:** Review coverage gaps and add RU translations or adjust threshold temporarily.

---

### 7. Blueprint/Manifest Snapshot Verification

Detect unintended changes to blueprint or manifest structure:

```bash
bash scripts/generate-blueprint-snapshots.sh verify
```

**What it checks:**
- Blueprint metadata (ID, version, question count)
- Sorted task lists and quotas
- Manifest locale totals and task counts

**If it fails:**
- **Intentional changes (e.g., new tasks):** Run `./scripts/generate-blueprint-snapshots.sh update`, review diff, and commit updated snapshots
- **Unintentional changes:** Fix the blueprint or manifest and rerun verification

---

## Recommended Manual Checks

After all automated validations pass, perform these manual spot-checks:

### 1. Sample Question Review (EN/RU)

- Open the app in debug mode
- Start a practice session with 10 random questions from different tasks
- For each question:
  - Verify the question stem is clear and grammatically correct
  - Check that choices are distinct and plausible
  - Confirm the correct answer is accurate
  - Review the rationale for completeness
  - **If explanation exists:** Read the explanation and ensure it adds value beyond the rationale
- Repeat for RU locale to spot translation issues

### 2. Explanation Spot-Check

- Pick 3–5 questions with EN explanations (see `content/explanations/en/`)
- Open each in the review screen
- Verify:
  - Explanation summary is concise and accurate
  - Steps are clear and actionable
  - "Why not" alternatives are helpful
  - Tips are practical
  - References are current and relevant
- Repeat for RU translations to ensure consistency

### 3. Localized UI Strings

- Switch app locale between EN and RU via Settings
- Navigate through:
  - Home screen
  - Mode selector (Exam/Practice)
  - Practice config screen
  - Exam/practice session
  - Results and review screens
  - Settings and admin screens (if applicable)
- Verify:
  - All UI text appears in the selected locale
  - No missing translations (no fallback to EN when RU is selected)
  - Text fits within UI elements (no overflow/truncation)

### 4. Blueprint Metadata Alignment

- Open `content/blueprints/*.json`
- Verify:
  - `validFrom` date is correct for the release
  - `blueprintVersion` and `policyVersion` match the official source
  - `sources` list is up to date and references current standards
  - Block/task titles match the official trade blueprint

### 5. Asset Distribution Integrity

- Check `app-android/src/main/assets/questions/index.json`
- Verify:
  - `generatedAt` timestamp is recent (should match when you last ran dist build)
  - Locale totals match expected counts (e.g., EN ~750 questions, RU ~750 questions)
  - Per-task counts align with source content in `content/questions/`

---

## Test Build and Smoke Test

After all checks pass:

1. **Build release APK:**
   ```bash
   ./gradlew assembleRelease
   ```

2. **Install on a test device:**
   ```bash
   adb install app-android/build/outputs/apk/release/app-release.apk
   ```

3. **Run smoke test:**
   - Launch the app
   - Start an exam session (IP exam mode, full 125 questions)
   - Answer a few questions, flag a question, submit early or let timer run down
   - Review results and navigate to review screen
   - Open a flagged question and check explanation (if present)
   - Switch locale to RU and repeat a short practice session
   - Verify no crashes or UI glitches

---

## CI/CD Integration

The following CI workflows enforce these checks automatically:

- **`.github/workflows/android.yml`** - Runs formatting, linting, unit tests, and `assembleDebug`
- **`.github/workflows/content-validators.yml`** - Runs blueprint/question validators and RU coverage test
- **`.github/workflows/dist-summary.yml`** - Posts per-locale question totals to PR comments

Ensure all workflows pass on the release branch before tagging a version.

---

## Final Release Steps

After all validations and smoke tests pass:

1. **Tag the release:**
   ```bash
   git tag -a v1.2.0 -m "Release v1.2.0: Add task X, update RU coverage"
   git push origin v1.2.0
   ```

2. **Update CHANGELOG.md** with release notes (features, bug fixes, content updates)

3. **Build and upload release APK** (or trigger CI/CD deployment)

4. **Monitor Crashlytics and user feedback** after deployment for any unexpected issues

---

## Quick Reference

| Check | Command | Purpose |
|-------|---------|---------|
| Assets | `./gradlew :app-android:verifyAssets` | Ensure all bundled assets are present |
| Blueprints | `bash scripts/validate-blueprint.sh` | Validate blueprint schema and structure |
| Questions | `bash scripts/validate-questions.sh` | Validate question schema and rationales |
| Quotas | `bash scripts/check-quotas.sh` | Ensure sufficient questions per task |
| Explanations | `bash scripts/check-explanation-coverage.sh` | Measure explanation coverage EN/RU |
| RU Coverage | `./gradlew :feature-exam:testDebugUnitTest --tests LocaleCoverageTest` | Enforce minimum RU locale coverage |
| Snapshots | `bash scripts/generate-blueprint-snapshots.sh verify` | Detect unintended blueprint/manifest changes |

---

## Troubleshooting

### Assets missing after build

- Ensure you ran `node scripts/build-questions-dist.mjs`
- Copy `dist/questions/` to `app-android/src/main/assets/questions/`
- Copy `content/blueprints/*.json` to `app-android/src/main/assets/blueprints/`

### Snapshot verification fails

- If changes are **intentional**: Run `./scripts/generate-blueprint-snapshots.sh update` and commit
- If changes are **unintentional**: Revert blueprint/manifest changes and rerun verification

### RU coverage test fails

- Review `LocaleCoverageTest` output for per-task gaps
- Add RU translations or adjust `localeCoverage.ru.min` threshold if coverage is acceptable

### Quota validation fails

- Check which tasks are short on questions
- Add more questions or adjust blueprint quotas (and update snapshots if needed)

---

For questions or issues with the release process, consult:
- [CONTENT_GUIDE.md](../CONTENT_GUIDE.md) for content authoring guidelines
- [CONTRIBUTING.md](../CONTRIBUTING.md) for development workflow
- [PROJECT_OVERVIEW.md](../PROJECT_OVERVIEW.md) for architecture overview
