# Content validation workflows

## Incremental checks (`--changed-only`)

Use the `--changed-only` flag to validate only the question JSON files that changed in your branch:

```bash
bash scripts/validate-questions.sh --changed-only
bash scripts/check-quotas.sh --changed-only --profile content/exam_profiles/welder_exam_2024.json --locales en,ru --mode min --min-multiple 1 --allow-extra
```

The helper `scripts/changed-files.sh` resolves the PR base/head SHAs (or defaults to `origin/main` locally) and prints the list of question JSONs that were modified. When no question JSONs changed and the schema/blueprints stay the same, `validate-questions.sh --changed-only` exits immediately with the log message:

```
[validate] no changed question files; skip
```

Similarly, quota checks skip themselves with:

```
[quotas] no changed question tasks; skip
```

Both scripts still succeed (exit code `0`) in these skip scenarios so CI can bypass unnecessary work.

## Forcing a full run

The incremental mode automatically falls back to a full validation whenever files under `content/schema/**` or `content/blueprints/**` change. Touching those paths forces the workflows to validate every question and recalculate quotas for the entire blueprint.

If you need a manual full run without modifying files, use the GitHub Actions workflow **Content Validators (full)** and press **Run workflow**. The job executes:

- `bash scripts/validate-questions.sh`
- `bash scripts/check-quotas.sh --profile content/exam_profiles/welder_exam_2024.json --locales en,ru --mode min --min-multiple 1 --allow-extra`

The “quick” workflow (triggered on pushes/PRs) uploads validation logs only when they exist and does not create empty artifacts.
