# Code Compliance Reports

The **Code Compliance (minimal)** GitHub Actions workflow collects compliance-focused reports for pull requests and manual runs.

## Workflow outputs

All generated artifacts are bundled into the `code-compliance-reports` archive:

- `reports/reuse-lint.txt` — console output of `reuse lint`, verifying SPDX headers and root `LICENSE`.
- `reports/sbom-cyclonedx.json` — CycloneDX SBOM produced by `syft dir:.`.
- `reports/deps-licenses.json` — dependencies with their declared licenses, derived from the SBOM via `jq`.
- `reports/deps-licenses-summary.json` — aggregated license counts across components.
- `reports/jscpd/` — HTML/JSON reports from `jscpd` code duplication analysis.

## Adding SPDX headers quickly

1. Reuse the canonical SPDX license identifiers from <https://spdx.org/licenses/>.
2. Add a header to the top of each source file, for example:
   ```kotlin
   // SPDX-License-Identifier: Apache-2.0
   ```
3. For shell or Python scripts, prefer `# SPDX-License-Identifier: Apache-2.0`.
4. Re-run the workflow (or `reuse lint`) to confirm compliance.

For bulk updates, consider using the [`reuse addheader`](https://reuse.software/) command locally.
