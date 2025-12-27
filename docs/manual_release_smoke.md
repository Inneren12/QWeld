# Manual QA: Lifecycle & Resume Smoke (10–20 minutes)

Use this checklist before releases to confirm exam/practice attempts survive common Android lifecycle events and brief offline periods. Focus on backgrounding, rotation (activity recreation), force-stop + relaunch, resume timer correctness, and queued question report retry.

## Prerequisites
- Test on a real device or emulator with **battery saver off** (avoid timer throttling) and **Doze disabled** if available.
- Developer options:
  - **Animations ON** (1x) unless diagnosing jank; keep default for realistic pacing.
  - **"Don't keep activities" OFF** to allow normal background/restore flows; turn ON only if explicitly testing process death.
- App build: recent **debug** build installed with known version/build variant recorded.
- Sign in or set up a **sample attempt** ready to start; ensure at least one question report can be queued.
- Network controls ready: quick toggle for **airplane mode** or Wi‑Fi off/on to simulate offline/online.

## Test Steps & Expected Results
1. **Start attempt & answer a question**
   - Begin an exam/practice attempt; answer at least one question.
   - *Expect:* Attempt appears in-progress; selected answers saved in UI.

2. **Background → Foreground**
   - Press Home to background for ~30 seconds, then return.
   - *Expect:* Same attempt resumes where left off; answers persist; timer continues from remaining time (no jump/skips).

3. **Rotate (activity recreation)**
   - Rotate device (portrait ↔ landscape) or trigger config change.
   - *Expect:* UI restores to same question with selected answers intact; timer shows consistent remaining time.

4. **Force-stop + relaunch**
   - Force-stop from app switcher or Settings → Apps → Force stop, then reopen.
   - *Expect:* Attempt is still listed as in-progress; resume surfaces same question/answers; remaining time uses persisted value and resumes ticking normally.

5. **Resume timer correctness over background**
   - While resumed, leave app in background for ~2–3 minutes (screen off acceptable), then return.
   - *Expect:* Remaining timer decreased only by time spent away; no reset or fast-forward beyond expected drift (<1–2s).

6. **Offline queued report smoke**
   - With attempt open, go **offline** (airplane mode). Submit a question report.
   - *Expect:* Submission queues without crash; offline queue retained (verify via admin/debug UI if available).
   - Restore network and wait for retry trigger (app resume or ~30–60s).
   - *Expect:* Queued report retries automatically and succeeds; queue count returns to zero; no duplicate submissions.

## Failure Capture
- **Logcat filters:** capture with tags used by lifecycle/resume and reporting flows:
  - `AttemptsRepository`, `QWeldDb` (attempt load/save),
  - `UserPrefsDataStore` (prefs restore),
  - `FirestoreQuestionReportRepo` (queued report submission),
  - `AttemptExporter` (export/resume helpers if invoked).
- **Screenshots:** record the screen showing incorrect timer/answers or error dialog.
- **Steps to reproduce:** concise numbered steps including device state (rotation, background duration, force-stop path, offline toggle timing).
- **Build info:** app version, build variant (debug/release), and device/emulator OS/version.

## Pass/Fail Invariants
- In-progress attempt remains available after background, rotation, and force-stop.
- Previously selected answers persist across all lifecycle events.
- Remaining timer persists and counts down correctly after resume (no resets/overruns).
- Offline question report queue is retained while offline and retries successfully once network returns.
