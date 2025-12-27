# Release smoke checklist (fast path)

Run on the signed release build before submitting to Play. For detailed lifecycle/offline steps, see [docs/manual_release_smoke.md](docs/manual_release_smoke.md).

1) **Install & launch** the release build; confirm splash/login works.
2) **Start exam** run at least one question; submit an answer.
3) **Start practice** with custom selection; answer and finish a short session.
4) **Switch locale** EN ↔ RU in Settings; verify strings update and navigation works.
5) **Report issue while offline**: go offline, submit a question report; return online and confirm it retries from the queue.

Capture blockers with build ID (`versionName` + `versionCode`) and device info.
