# Error-handling QA checklist

Use these steps when manually exercising the error-handling stack in debug builds.

## Triggering errors
- **Crash test hook:** Settings → Tools → “Trigger test crash (debug only)” to verify Crashlytics symbol upload and non-fatal capture works end-to-end.
- **AppError handler:** Toggle analytics/diagnostics on in Settings, then hit a known error path (e.g., auth Google sign-in with invalid credentials) to confirm non-fatals are logged without crashing.

## Error dialog
- Force an `AppError` with `offerReportDialog=true` (e.g., by enabling debug flags that emit test errors); ensure the “Report app error” dialog appears.
- Enter a short comment, submit, and confirm the dialog closes with a Snackbar/Toast acknowledgment while the app keeps running.

## Offline or weak network
- Enable airplane mode before submitting a question report from the exam/review screen.
- Confirm the UI surfaces the queued/offline acknowledgment (e.g., “Queued”/“Will retry”) instead of crashing.
- Return connectivity and relaunch the app to allow queued reports to retry; check admin dashboard queue counts if available.

## Crashlytics visibility
- After triggering an error or submitting a report, verify the event appears in Crashlytics (non-fatal) with sanitized metadata and without PII.
