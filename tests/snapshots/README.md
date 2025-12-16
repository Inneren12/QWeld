# Blueprint and Manifest Snapshot Tests

## Overview

This directory contains snapshot files used for regression testing of blueprint and manifest structure. Snapshots help detect unintended changes to exam structure, task quotas, and question counts across content updates.

## Snapshot Files

- `blueprint_*.snapshot` - One file per blueprint in `content/blueprints/`, containing:
  - Blueprint metadata (ID, version, policy version, question count)
  - Sorted list of blocks with titles
  - Sorted list of tasks with quotas and titles
  - Summary statistics (total tasks, total quota)

- `manifest.snapshot` - Summary of the question manifest (`app-android/src/main/assets/questions/index.json`), containing:
  - Manifest schema version
  - Per-locale totals and task counts (sorted)

## Running Snapshot Tests

### Verify Current State Against Snapshots

```bash
./scripts/generate-blueprint-snapshots.sh verify
```

This command:
- Compares current blueprints and manifest against stored snapshots
- **Exits with status 1** if any mismatch is detected
- Prints details about which snapshots failed

Use this in CI or before releases to ensure no unintended structural changes.

### Display Current Snapshots (Without Saving)

```bash
./scripts/generate-blueprint-snapshots.sh show
```

This command:
- Generates snapshots from current state
- Prints to stdout without saving
- Useful for inspecting what would change before updating

### Update Snapshots Intentionally

```bash
./scripts/generate-blueprint-snapshots.sh update
```

This command:
- Regenerates all snapshot files from current blueprints and manifest
- Overwrites existing snapshots
- **Use this when you intentionally change blueprint structure or manifest**

**When to update snapshots:**
- After adding/removing blueprint tasks
- After changing task quotas
- After adjusting question counts
- After rebuilding the manifest with `scripts/build-questions-dist.mjs`

**Important:** Always review the snapshot diff before committing updated snapshots:

```bash
git diff tests/snapshots/
```

## What Triggers a Snapshot Failure?

Snapshot verification fails when:
- Blueprint metadata changes (ID, version, question count)
- Tasks are added, removed, or reordered
- Task quotas change
- Manifest locale totals or task counts change
- Schema version changes

## Intentional vs. Unintentional Changes

### Intentional (Update snapshots)
- Adding new tasks to meet curriculum requirements
- Adjusting quotas to match updated blueprints
- Rebuilding manifest after adding/removing questions

### Unintentional (Fix before committing)
- Accidentally modifying blueprint JSON
- Forgetting to rebuild manifest after content changes
- Merging outdated content that conflicts with current structure

## Integration with CI

Snapshot verification runs as part of content validation workflows:

```yaml
# .github/workflows/content-validators.yml
- name: Verify blueprint snapshots
  run: ./scripts/generate-blueprint-snapshots.sh verify
```

If snapshots fail in CI:
1. Review the error output to understand what changed
2. If intentional, run `./scripts/generate-blueprint-snapshots.sh update` locally
3. Review the diff: `git diff tests/snapshots/`
4. Commit the updated snapshots with a clear message explaining why
5. Push and rerun CI

If unintentional, fix the blueprint/manifest issue and rerun verification.

## Example Workflow

### Scenario: Adding a New Task

1. Edit `content/blueprints/welder_ip_sk_202404.json`
   - Add new task `A-6` with quota `7`
   - Adjust `questionCount` if needed

2. Add questions for the new task under `content/questions/en/A-6/` and `content/questions/ru/A-6/`

3. Rebuild the manifest:
   ```bash
   node scripts/build-questions-dist.mjs
   ```

4. Run snapshot verification (expect failure):
   ```bash
   ./scripts/generate-blueprint-snapshots.sh verify
   # ERROR: Blueprint snapshot mismatch for welder_ip_sk_202404
   ```

5. Review what changed:
   ```bash
   ./scripts/generate-blueprint-snapshots.sh show | diff tests/snapshots/blueprint_welder_ip_sk_202404.snapshot -
   ```

6. Update snapshots intentionally:
   ```bash
   ./scripts/generate-blueprint-snapshots.sh update
   ```

7. Commit with clear message:
   ```bash
   git add tests/snapshots/ content/blueprints/ content/questions/
   git commit -m "Add task A-6: Update blueprint and snapshots"
   ```

## Maintenance

- Review snapshots periodically (e.g., before major releases)
- Keep snapshot files in version control
- Update documentation if snapshot format changes
- Ensure all contributors understand when to update vs. fix snapshot failures
