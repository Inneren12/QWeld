# Content Guide

## Overview
QWeld exam content is data-driven. Blueprints define exam structure and quotas, while localized question banks provide the items used to assemble attempts. Explanations supplement review flows. All artifacts are JSON and kept in-repo for deterministic builds.

## Where Content Lives
- `content/blueprints/` – source-of-truth blueprint JSON files (e.g., `welder_ip_sk_202404.json`).
- `content/questions/` – localized question banks organized by task (`en/`, `ru/` plus `sample_welder_question.json`).
- `content/explanations/` – structured explanations paired with questions (mirrored into assets when built).
- `content/exam_profiles/` – profile metadata for exams (where applicable).
- Built assets are mirrored under `app-android/src/main/assets/` (`blueprints/`, `questions/{locale}/bank.v1.json`, `questions/{locale}/tasks/`, `questions/index.json`) and guarded by the `verifyAssets` task.
- `dist/questions/` is produced by build scripts and should be copied into the app assets for packaging.

## Blueprint JSON Structure
Blueprints describe the target exam: identity, policy versions, and per-task quotas. Common fields:
- `id`, `trade`, `exam`, `blueprintVersion`, `policyVersion`, `questionCount`, `validFrom`.
- `sources`: list of references with `type`, `name`, `notes`.
- `blocks`: array of exam blocks (A–D) each containing `tasks` with `id`, `title`, and numeric `quota` (percentage/count as defined by the blueprint).

Example (trimmed):
```json
{
  "id": "welder_ip_sk_202404",
  "questionCount": 125,
  "blocks": [
    {
      "id": "A",
      "title": "Safety, Tools, and Shop Practice",
      "tasks": [ { "id": "A-1", "title": "Follow welding safety legislation and PPE requirements", "quota": 8 } ]
    }
  ]
}
```
【F:content/blueprints/welder_ip_sk_202404.json†L1-L80】

## Question Bank JSON Structure
Each task folder contains question files with consistent IDs across locales. Typical fields:
- `id` (unique question id), `taskId`, optional `difficulty` tag.
- `stem` (question text), `choices` array (each with `id` and `text`).
- `correctId` referencing one of the choices.
- Optional `rationales` map, `tags`, and `source` metadata.

Example (trimmed):
```json
{
  "id": "Q-0001",
  "taskId": "A-1",
  "stem": "Which action best ensures compliance…?",
  "choices": [ { "id": "CHOICE-1", "text": "…" }, { "id": "CHOICE-3", "text": "Inspect helmet lens…" } ],
  "correctId": "CHOICE-3",
  "difficulty": "medium",
  "rationales": { "CHOICE-3": "PPE checks target protective equipment…" },
  "tags": ["safety", "ppe"]
}
```
【F:content/questions/sample_welder_question.json†L1-L36】

Localized banks mirror the same `taskId` structure under `content/questions/en/` and `content/questions/ru/`. Per-task bundles aggregate multiple question files during dist generation.

## Localization & Fallback
- Supported locales: **en** and **ru**. The asset repository loads per-task bundles for the requested locale, then falls back to the monolithic bank, and finally retries with the default locale (EN) if the locale is missing.
- Corrupt assets in the requested locale surface errors rather than silently masking problems; successful fallback logs the resolved locale and tasks.
【F:feature-exam/src/main/java/com/qweld/app/feature/exam/data/AssetQuestionRepository.kt†L136-L189】

## How to Add / Update Content
1. **Choose the right file:** pick the task folder under `content/questions/<locale>/<taskId>/` and mirror the same question ID in all locales.
2. **Author the JSON:** follow the schema above; keep stable `id`/`taskId`, provide unique `choice` IDs, and include rationales where possible.
3. **Update blueprints if quotas change:** edit `content/blueprints/*.json` to adjust `quota` or `questionCount` when the exam definition shifts.
4. **Regenerate dist assets:** run `node scripts/build-questions-dist.mjs` (or the bash wrapper) to create `dist/questions/<locale>/bank.v1.json`, per-task bundles, and `index.json`; copy them into `app-android/src/main/assets/questions/`.
5. **Mirror blueprints/rules:** copy updated blueprints into `app-android/src/main/assets/blueprints/` if they changed.

## Validation & QA
- **Asset gate:** `./gradlew :app-android:verifyAssets` ensures banks, per-task bundles (15 per locale), and `index.json` exist before builds.
- **Schema/consistency checks:** `bash scripts/validate-blueprint.sh` and `bash scripts/validate-questions.sh` lint blueprints and questions; `bash scripts/check-quotas.sh` validates quotas; `bash scripts/build-questions-dist.sh` builds banks and logs counts.
- **CI:** `.github/workflows/content-validators.yml` runs the validators and publishes artifacts; `.github/workflows/dist-summary.yml` posts per-locale totals to PRs.
- **Sanity checks:** ensure blueprint quotas sum to the expected total, every blueprint task has questions in each locale, and per-task bundles count matches expectations (`expectedTaskCount = 15`).

## Known Limitations / TODOs
- Only EN/RU locales are shipped; missing locales fall back to EN rather than partial translation.
- Benchmarks for content performance are scaffolded but not populated.
- Adaptive assembly or richer authoring UX are not yet present; rely on scripts and manual JSON editing for now.
