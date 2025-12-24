# Questions index findings

## Schema + CI validation
- AJV is invoked from `.github/workflows/content-validators.yml` using `tools/schema-validation/schemas/welder_blueprint.schema.json` against every `app-android/src/main/assets/questions/**/index.json` path with `ajv-formats` enabled and `--spec=draft2020 --all-errors --strict=false` flags.
- Schema branches:

| Branch title | Required fields | Allowed properties | Notes |
| --- | --- | --- | --- |
| Root questions index | `schema`, `generatedAt`, `locales` | Only those three keys; `locales` entries must be `localeSummary` objects | Discriminator is `schema: "questions-index-v1"`; totals and per-task counts/hashes live inside each locale summary. |
| Locale bundle index | `blueprintId`, `bankVersion`, `files` | Only those three keys; `files` map values are `fileEntry` objects containing a `sha256` string | Blueprint ID matches `welder_ip_<province>_<yyyymm>` pattern; supports multiple file paths keyed to SHA-256 hashes. |

## Runtime loader decision points
- `core-data/src/main/java/com/qweld/app/data/content/ContentIndexReader.kt` lists locales under `questions/`, opens `<locale>/index.json`, and parses them via `IndexParser`; the root `questions/index.json` is not consulted when loading assets or verifying integrity. Unknown keys are ignored via the parser’s JSON config, and path normalization supports `questions/` prefixes and `.json`/`.json.gz` toggles. 
- `core-data/src/main/java/com/qweld/app/data/content/questions/IndexParser.kt` extracts `blueprintId`, `bankVersion`, and a hash map from `files`/`entries` fields; if those are absent it will fall back to looking for `locales[*].files` but otherwise returns an empty manifest. This parser drives integrity checks and path resolution in `feature-exam` loaders.
- `feature-exam/src/main/java/com/qweld/app/feature/exam/data/AssetQuestionRepository.kt` calls `ContentIndexReader` for each locale, building an `AssetIntegrityGuard` from the parsed manifest; asset resolution requires the manifest to contain the target path (with optional `.gz` alternate) and the guard verifies SHA-256 values.

## Canonical formats
- **Runtime-critical manifests:** per-locale `questions/<locale>/index.json` files with `blueprintId`, `bankVersion`, and a `files` map whose values are objects containing `sha256`. These are required for loaders and integrity guards.
- **Root summary:** `questions/index.json` follows the `questions-index-v1` shape but is only used for diagnostics (e.g., `ContentManifestDiagnostics` in admin tooling); it does not influence runtime loading.
- The loader tolerates extra properties because `IndexParser`/`ContentIndexReader` enable `ignoreUnknownKeys`, but successful integrity checks still rely on the `files` map entries being objects with `sha256` values.

## Execution proof
- Added `ContentIndexReaderTest.read parses real bundled manifests`, which uses the real `app-android/src/main/assets/questions/*/index.json` files. It confirms the per-locale manifests parse, expose `blueprintId="welder_ip_sk_202404"` and `bankVersion="v1"`, and contain expected file paths like `questions/en/bank.v1.json` and `questions/en/tasks/A-1.json`.
