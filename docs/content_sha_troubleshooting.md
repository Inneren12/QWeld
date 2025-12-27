# Content SHA troubleshooting (no rebuild)

Short guide for fixing manifest vs. file SHA mismatches without regenerating dist assets.

- **Symptom:** `:app-android:verifyAssets` or `ContentIndexReaderTest` reports a SHA mismatch for a bundled asset even though manifests are unchanged.
- **Root cause:** The working tree copy of an asset differs from the tracked git blob (common causes: Windows `core.autocrlf` rewriting newlines, manual edits/overwrites of `app-android/src/main/assets/questions/**`).
- **Recovery (no rebuild):** Reset the affected files to the tracked blob and re-run verification:
  ```bash
  git checkout -- app-android/src/main/assets/questions/index.json app-android/src/main/assets/questions/*/bank.v1.json app-android/src/main/assets/questions/*/tasks/*.json
  ./gradlew :app-android:verifyAssets
  ```
- **Prevent repeat:** Use a repo-local newline policy that avoids CRLF rewrites when editing assets, e.g. `git config core.autocrlf input` (or `false` on Windows) before touching asset files.

