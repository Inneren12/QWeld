# Required checks & branch protection

Follow these steps once the workflows are present on `main`:

1. Open the repository on GitHub and navigate to **Settings → Branches**.
2. Under **Branch protection rules**, click **Add rule** (or edit the existing rule for `main`).
3. Set **Branch name pattern** to `main` and enable **Require status checks to pass before merging**.
4. Select the following checks:
   - `Android CI / build`
   - `Content Validators (quick) / content`
   - *(Optional)* `Android UI smoke / ui-smoke` — this job is marked `continue-on-error` so failures won't block merges but the history is visible.
5. Enable any additional protections you rely on (e.g., linear history, signed commits), then save the rule.

The Android CI workflow now enforces a minimum **60%** line coverage threshold using `scripts/coverage-threshold.sh`. If the aggregated report at `build/reports/kover/xml/report.xml` drops below the threshold (or a higher value provided via `THRESH`), the job fails and blocks the merge until coverage is restored.
