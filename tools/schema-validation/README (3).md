# QWeld Schema Validation

JSON Schema validation for QWeld question bank index files.

## Directory Structure

```
tools/schema-validation/
├── schemas/
│   └── welder_blueprint.schema.json    # JSON Schema definition
├── package.json                         # npm dependencies
└── README.md                           # This file
```

## Setup

```bash
cd tools/schema-validation
npm ci
```

## Usage

### Validate Root Index

Validates the main questions index at `app-android/src/main/assets/questions/index.json`:

```bash
npm run validate:root
```

Or directly with ajv-cli:

```bash
npx ajv-cli validate \
  -c ajv-formats \
  -s schemas/welder_blueprint.schema.json \
  -d "../../app-android/src/main/assets/questions/index.json" \
  --spec=draft2020 --all-errors --strict=false
```

### Validate Locale Manifests

Validates all locale manifest files at `app-android/src/main/assets/questions/{locale}/index.json`:

```bash
npm run validate:locales
```

Or directly:

```bash
npx ajv-cli validate \
  -c ajv-formats \
  -s schemas/welder_blueprint.schema.json \
  -d "../../app-android/src/main/assets/questions/*/index.json" \
  --spec=draft2020 --all-errors --strict=false
```

### Validate All

```bash
npm run validate:all
```

## Schema Overview

The schema (`welder_blueprint.schema.json`) validates two types of files using `oneOf`:

### 1. Root Summary Index

**File:** `questions/index.json`

**Purpose:** Aggregated metadata for all locales

**Structure:**
```json
{
  "schema": "questions-index-v1",
  "generatedAt": "2025-12-08T20:00:00Z",
  "locales": {
    "en": {
      "total": 606,
      "tasks": {
        "A-1": { "sha256": "..." }
      },
      "sha256": {
        "sha256": "..."
      }
    }
  }
}
```

### 2. Locale Manifest

**File:** `questions/{locale}/index.json`

**Purpose:** Complete file listing for a specific locale

**Structure:**
```json
{
  "blueprintId": "welder_ip_sk_202404",
  "bankVersion": "v1",
  "files": {
    "questions/en/bank.v1.json": { "sha256": "..." },
    "questions/en/tasks/A-1.json": { "sha256": "..." }
  }
}
```

## CI/CD Integration

This validation runs automatically in GitHub Actions. See `.github/workflows/content-validators.yml`.

## Troubleshooting

### Error: Cannot find schema

Make sure you're running commands from `tools/schema-validation` directory or using the correct relative path.

### Error: must be object (for sha256/tasks)

Your root index structure is incorrect. Tasks must be objects with sha256 property:

```json
// ✓ Correct
"tasks": {
  "A-1": { "sha256": "abc..." }
}

// ✗ Wrong
"tasks": {
  "A-1": "abc..."
}
```

### Error: must match exactly one schema in oneOf

Your file doesn't match either root index or locale manifest format. Check:
- Root index must have: `schema`, `generatedAt`, `locales`
- Locale manifest must have: `blueprintId`, `bankVersion`, `files`

## Related Files

- Question generation: `tools/content/gen_questions_indexes.js`
- Blueprint definitions: `app-android/src/main/assets/blueprints/`
