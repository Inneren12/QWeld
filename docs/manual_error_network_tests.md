# Manual Network Error Testing Checklist

This document provides a checklist for manually testing error handling, network error scenarios, and offline behavior in the QWeld app. Use this checklist during QA cycles and before releases to ensure robust error handling.

---

## Prerequisites

- Debug build of the app installed on a test device or emulator
- Access to Settings → Tools → Admin Dashboard (debug-only screens)
- Ability to toggle device network connectivity (airplane mode, Wi-Fi/cellular toggles)
- Optional: Firebase Console access to verify Crashlytics reports and question report submissions

---

## Source of Truth in Code

This section lists the key files/classes implementing the features tested in this checklist. Use these references to understand implementation details or debug issues.

### Error Handling

- **Error Handler Interface:** `core-common/src/main/java/com/qweld/app/common/error/AppErrorHandler.kt`
- **Error Handler Implementation:** `app-android/src/main/java/com/qweld/app/error/AppErrorHandler.kt`
  - `AppErrorHandlerImpl`: Central error handler tracking history, forwarding to Crashlytics, emitting UI events
  - `CrashlyticsCrashReporter`: Crashlytics integration with analytics gating
- **Error Dialog UI:** `app-android/src/main/java/com/qweld/app/ui/AppErrorReportDialog.kt`
  - User-facing dialog for submitting error reports with optional comments
- **Error Models:** `core-common/src/main/java/com/qweld/app/common/error/AppErrorHandler.kt`
  - `AppError`, `AppErrorEvent`, `UiErrorEvent`, `AppErrorReportResult`

### Question Reporting

- **Repository Interface:** `core-data/src/main/java/com/qweld/app/data/reports/QuestionReportRepository.kt`
- **Firestore Implementation:** `core-data/src/main/java/com/qweld/app/data/reports/FirestoreQuestionReportRepository.kt`
  - Handles online submission, offline queueing, and retry logic
- **Offline Queue DAO:** `core-data/src/main/java/com/qweld/app/data/db/dao/QueuedQuestionReportDao.kt`
  - Room DAO for queued reports persistence
- **Queue Entity:** `core-data/src/main/java/com/qweld/app/data/db/entities/QueuedQuestionReportEntity.kt`
- **Retry Use Case:** `core-data/src/main/java/com/qweld/app/data/reports/RetryQueuedQuestionReportsUseCase.kt`
  - Retries queued reports on app start with configurable attempt/batch limits
- **Payload Builder:** `core-data/src/main/java/com/qweld/app/data/reports/QuestionReportPayloadBuilder.kt`
  - Builds sanitized Firestore payloads with metadata (PII-free)

### Admin Dashboard

- **Dashboard ViewModel:** `app-android/src/main/java/com/qweld/app/admin/AdminDashboardViewModel.kt`
  - Loads attempt stats, DB health, queue status, recent errors
- **Dashboard Screen:** `app-android/src/main/java/com/qweld/app/admin/AdminDashboardScreen.kt`
  - UI displaying system health, queued reports count, recent error summaries
- **Question Reports ViewModel:** `app-android/src/main/java/com/qweld/app/admin/QuestionReportsViewModel.kt`
  - Lists question reports with summaries and detail views
- **Navigation:** `app-android/src/main/java/com/qweld/app/navigation/AppNavGraph.kt`
  - Admin dashboard route: `Settings → Tools → Admin Dashboard` (debug-only)

### DI Wiring

- **App Module:** `app-android/src/main/java/com/qweld/app/di/AppModule.kt`
  - Binds `AppErrorHandler`, `QuestionReportRepository`, repositories, analytics

### Tests

- **Error Handler Unit Tests:** `app-android/src/test/java/com/qweld/app/error/AppErrorHandlerTest.kt`
- **Error Dialog UI Tests:** `app-android/src/androidTest/java/com/qweld/app/error/ErrorDialogUiTest.kt`
- **Question Report UI Tests:** `feature-exam/src/androidTest/java/com/qweld/app/feature/exam/ui/QuestionReportUiTest.kt`
- **Admin Reports UI Tests:** `app-android/src/androidTest/java/com/qweld/app/admin/AdminReportsUiTest.kt`
- **Offline/Retry Tests:** `core-data/src/test/java/com/qweld/app/data/reports/FirestoreQuestionReportRepositoryTest.kt`

---

## 1. Debug Test Crash

This test verifies that Crashlytics symbol upload is working correctly by triggering a test crash.

### Steps

1. **Enable analytics in Settings** (if not already enabled)
   - Open app → Settings → Analytics toggle → Enable

2. **Navigate to debug crash trigger**
   - Settings → Tools → "Test Crashlytics"

3. **Trigger test crash**
   - Tap "Trigger Test Crash" button
   - App should crash immediately

4. **Verify crash report**
   - Restart the app
   - Wait 5–10 minutes for crash report to upload
   - Open Firebase Console → Crashlytics → Dashboard
   - Verify that a new crash event appears with:
     - Correct app version and build type
     - Readable stack trace (not obfuscated gibberish)
     - Device/Android version metadata

### Expected Results

- ✅ App crashes when "Trigger Test Crash" is tapped
- ✅ Crash report appears in Firebase Console within 5–10 minutes
- ✅ Stack trace is readable and includes file names and line numbers
- ✅ Device metadata (Android version, device model) is present

### Troubleshooting

- **Crash report missing:** Check that `google-services.json` is present and analytics are enabled
- **Stack trace obfuscated:** Verify that Crashlytics mapping files were uploaded during release build (see `docs/RELEASE_CHECKLIST.md`)

---

## 2. Error Dialog / Report Flow (Online)

This test verifies that the user-facing error dialog appears when unexpected errors occur and that error reports are submitted to Crashlytics.

### Steps

1. **Enable analytics in Settings**
   - Settings → Analytics toggle → Enable

2. **Trigger an error** (choose one method):
   - Method A: Force an error by submitting a question report while Firestore is unavailable (requires backend manipulation)
   - Method B: Use a debug-only error trigger if available (e.g., a "Test Error" button in admin tools)

3. **Verify error dialog appears**
   - Dialog should show:
     - Error message (brief description)
     - "Send report" button
     - Optional comment field
     - "Dismiss" button

4. **Submit error report**
   - Tap "Send report"
   - Enter a comment (e.g., "Manual test – error dialog")
   - Tap "Submit"
   - Verify that a success message or snackbar appears

5. **Check Crashlytics**
   - Wait 5–10 minutes
   - Open Firebase Console → Crashlytics → Non-fatals
   - Verify that the error appears with:
     - Error context (screen, action)
     - User comment attached
     - App/device metadata

### Expected Results

- ✅ Error dialog appears when an error occurs
- ✅ User can enter a comment and submit the report
- ✅ Report appears in Crashlytics within 5–10 minutes
- ✅ User comment is attached to the Crashlytics report

### Troubleshooting

- **Dialog doesn't appear:** Check that `offerReportDialog` is set to `true` for the error type
- **Report not submitted:** Verify analytics are enabled and network is online

---

## 3. Error Dialog / Report Flow (Offline – Analytics Disabled)

This test verifies that when analytics are disabled, error reports are NOT sent to Crashlytics.

### Steps

1. **Disable analytics in Settings**
   - Settings → Analytics toggle → Disable

2. **Trigger an error** (same as Test 2)

3. **Verify error dialog appears**
   - Dialog should still show (error handling works offline)

4. **Attempt to submit error report**
   - Tap "Send report"
   - Enter a comment
   - Tap "Submit"

5. **Verify "disabled" feedback**
   - App should show a message like "Error reporting is disabled" or similar feedback
   - OR the submit button should be disabled/grayed out when analytics are off

6. **Check Crashlytics**
   - Wait 5–10 minutes
   - Verify that **no new non-fatal** appears in Crashlytics (report was not sent)

### Expected Results

- ✅ Error dialog appears even when analytics are disabled
- ✅ Submit is disabled or returns "disabled" feedback
- ✅ No new Crashlytics non-fatal appears (respecting analytics opt-out)

### Troubleshooting

- **Report still submitted:** Check that `AppErrorHandler` respects `analyticsEnabled` state before submitting

---

## 4. Question Report (Online)

This test verifies that question reports can be submitted when online and that they appear in Firestore.

### Steps

1. **Start an exam or practice session**
   - Answer at least one question

2. **Report a question**
   - Tap "Report issue" button (three-dot menu or dedicated button)
   - Select a reason (e.g., "Wrong answer", "Typo", "Ambiguous")
   - Enter a comment (e.g., "Manual test – online report")
   - Tap "Submit"

3. **Verify success feedback**
   - App should show a snackbar or toast like "Report submitted" or "Thank you for reporting"

4. **Check Firestore**
   - Open Firebase Console → Firestore → `question_reports` collection
   - Verify that a new document appears with:
     - `questionId`, `locale`, `reasonCode`, `userComment`
     - `status == 'OPEN'`
     - App/device metadata
     - **No PII fields** (no email, userName, etc.)

5. **Check admin dashboard**
   - Settings → Tools → Admin Dashboard → Question Reports
   - Verify that the report appears in the list with the correct comment

### Expected Results

- ✅ Report is submitted successfully
- ✅ Success feedback appears
- ✅ Report appears in Firestore within seconds
- ✅ Report appears in admin dashboard
- ✅ No PII fields are present in the Firestore document

### Troubleshooting

- **Report not submitted:** Check network connectivity and Firestore permissions
- **PII fields present:** Review `QuestionReportPayloadBuilder.kt` and Firestore security rules

---

## 5. Question Report (Offline – Queue and Retry)

This test verifies that question reports are queued locally when offline and retried when network is restored.

### Steps

1. **Enable airplane mode** (or disable Wi-Fi/cellular)

2. **Start an exam or practice session**

3. **Report a question**
   - Tap "Report issue"
   - Select a reason and enter a comment (e.g., "Manual test – offline queue")
   - Tap "Submit"

4. **Verify queued feedback**
   - App should show a message like "Report queued" or "Will retry when online"

5. **Check admin dashboard (queued count)**
   - Settings → Tools → Admin Dashboard
   - Verify that "Queued question reports" count is 1 (or increased by 1)

6. **Disable airplane mode** (restore network)

7. **Wait for retry**
   - Reports are retried on app start or after a short delay
   - Wait 10–30 seconds or restart the app to trigger retry

8. **Check Firestore**
   - Open Firebase Console → Firestore → `question_reports` collection
   - Verify that the queued report now appears

9. **Check admin dashboard (queued count)**
   - Settings → Tools → Admin Dashboard
   - Verify that "Queued question reports" count is now 0 (report was sent)

### Expected Results

- ✅ Report is queued when offline
- ✅ "Queued" feedback appears
- ✅ Queued count increases in admin dashboard
- ✅ Report is sent to Firestore when network is restored
- ✅ Queued count decreases to 0 after successful retry

### Troubleshooting

- **Report not queued:** Check that offline queueing is enabled in `FirestoreQuestionReportRepository`
- **Report not retried:** Verify that `RetryQueuedQuestionReportsUseCase` is called on app start or network resume

---

## 6. Question Report (Offline – Retry Failure)

This test verifies that queued reports are dropped after a maximum number of retry attempts.

### Steps

1. **Enable airplane mode**

2. **Submit multiple question reports** (e.g., 3 reports)
   - Each should be queued locally

3. **Check admin dashboard (queued count)**
   - Verify that queued count is 3

4. **Simulate retry failures** (requires code manipulation or backend config):
   - Option A: Keep airplane mode enabled and force app to retry (restart app multiple times)
   - Option B: Temporarily break Firestore permissions to force submission failures

5. **Trigger retries multiple times**
   - Restart app 5–10 times (or until retry attempts exceed max limit, typically 5)

6. **Verify reports are dropped**
   - Settings → Tools → Admin Dashboard
   - Check admin dashboard or DB directly to see that reports were dropped after max attempts
   - Queued count should decrease as reports are dropped

### Expected Results

- ✅ Queued reports are retried on app start or network resume
- ✅ Retry attempts are incremented on each failure
- ✅ Reports are dropped after max retry attempts (default 5)
- ✅ Admin dashboard reflects dropped reports (queued count decreases)

### Troubleshooting

- **Reports never dropped:** Check `RetryQueuedQuestionReportsUseCase` max attempts parameter
- **Retry attempts not incremented:** Verify that `QueuedQuestionReportDao.updateAttemptCount()` is called on retry failure

---

## 7. Network Error Scenarios (General App Flows)

This test verifies that the app handles network errors gracefully in various screens and flows.

### Steps

1. **Enable airplane mode**

2. **Navigate through the app**
   - Open home screen
   - Start an exam or practice session (should work offline if assets are bundled)
   - Submit answers (should work offline, saved locally)
   - Finish attempt (should work offline, saved locally)
   - Navigate to results/review (should work offline)
   - Try to load explanations (may fail if not bundled; verify graceful error message)

3. **Check for crashes or hangs**
   - App should not crash or freeze due to network errors
   - Error messages should be user-friendly (e.g., "No network connection" instead of "HTTP 503")

4. **Disable airplane mode** (restore network)

5. **Verify sync/retry behavior**
   - Check if any queued data (e.g., question reports, analytics events) is retried automatically

### Expected Results

- ✅ App remains functional when offline (core flows work with bundled assets)
- ✅ No crashes or freezes due to network errors
- ✅ User-friendly error messages appear when network-dependent features fail
- ✅ Queued data is retried when network is restored

### Troubleshooting

- **App crashes offline:** Check for missing null checks or unhandled network exceptions
- **Error messages not user-friendly:** Review error handling in repositories and ViewModels

---

## 8. Admin Dashboard – Error Context

This test verifies that the admin dashboard shows recent errors and error context.

### Steps

1. **Trigger an error** (use any method from previous tests)

2. **Navigate to admin dashboard**
   - Settings → Tools → Admin Dashboard

3. **Verify error summary**
   - Check for:
     - "Recent errors" count (should be > 0)
     - Last error timestamp
     - Error context (screen, action) if displayed

4. **Verify error history**
   - If admin dashboard shows error details, verify that:
     - Error message is present
     - Error context (screen, action) is displayed
     - Timestamp is recent

### Expected Results

- ✅ Admin dashboard shows recent errors count
- ✅ Error context and timestamp are displayed
- ✅ Error history is accessible for debugging

### Troubleshooting

- **Errors not showing:** Check that `AppErrorHandler` is wired correctly and tracking error history

---

## Summary Checklist

Use this quick checklist to verify all scenarios:

- [ ] **Test 1:** Debug test crash triggers and appears in Crashlytics
- [ ] **Test 2:** Error dialog appears and reports are submitted (analytics enabled)
- [ ] **Test 3:** Error dialog respects analytics opt-out (analytics disabled)
- [ ] **Test 4:** Question reports are submitted when online
- [ ] **Test 5:** Question reports are queued offline and retried when online
- [ ] **Test 6:** Queued reports are dropped after max retry attempts
- [ ] **Test 7:** App handles network errors gracefully in all flows
- [ ] **Test 8:** Admin dashboard shows recent errors and error context

---

## Notes

- **Test on multiple devices/Android versions:** Network behavior may vary across devices and OS versions
- **Test with weak network:** Use Android emulator network throttling or tools like Charles Proxy to simulate slow/flaky connections
- **Test with Firestore emulator:** Use Firebase Emulator Suite to test question reporting without affecting production data

---

## References

- [Error Handling Documentation](./error_handling_manual_checklist.md)
- [Firestore Security Rules](./firestore_security_notes.md)
- [Release Checklist](./RELEASE_CHECKLIST.md)
- [AppErrorHandler Implementation](../app-android/src/main/java/com/qweld/app/error/AppErrorHandler.kt)
- [FirestoreQuestionReportRepository Implementation](../core-data/src/main/java/com/qweld/app/data/reports/FirestoreQuestionReportRepository.kt)

---

For questions or issues with manual testing, consult the QWeld development team or open an issue in the GitHub repository.
