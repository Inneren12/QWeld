# Firestore Security Rules – QWeld Question Reports

This document describes the recommended Firestore security rules for the `/question_reports` collection and provides a checklist for reviewing and deploying them.

---

## Overview

QWeld submits question reports to Firestore under the `/question_reports` collection. These reports include:
- Question metadata (ID, task, block, blueprint version, locale)
- User-provided feedback (reason code, optional comment)
- App/device metadata (version, platform, Android version, device model)
- **No PII** (no email, userName, fullName, phone, userId)

The security rules must:
1. Allow authenticated users to submit reports (write-only)
2. Prevent users from reading other users' reports
3. Allow admin/backend services to read/update reports for triage and moderation

---

## Recommended Firestore Rules

Place these rules in your Firebase project's Firestore rules file (usually `firestore.rules`):

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Question reports collection
    match /question_reports/{reportId} {

      // Helper function: validate report payload structure and constraints
      function isValidReport() {
        let data = request.resource.data;
        let requiredKeys = ['questionId', 'taskId', 'blockId', 'blueprintId', 'locale', 'mode', 'reasonCode', 'status', 'createdAt'];
        let optionalKeys = [
          'blueprintVersion', 'reasonDetail', 'userComment',
          'questionIndex', 'totalQuestions', 'selectedChoiceIds', 'correctChoiceIds', 'blueprintTaskQuota',
          'contentVersion', 'contentIndexSha',
          'appVersionName', 'appVersionCode', 'appVersion', 'buildType', 'env', 'platform',
          'androidVersion', 'deviceModel',
          'sessionId', 'attemptId', 'seed', 'attemptKind',
          'errorContextId', 'errorContextMessage', 'recentError',
          'review'
        ];
        let allowedKeys = requiredKeys.concat(optionalKeys);

        return (
          // Allowlist validation: only permitted keys
          data.keys().hasOnly(allowedKeys)

          // Required fields present
          && data.keys().hasAll(requiredKeys)

          // Type and constraint validation
          && data.questionId is string && data.questionId.size() > 0 && data.questionId.size() <= 100
          && data.taskId is string && data.taskId.size() > 0 && data.taskId.size() <= 50
          && data.blockId is string && data.blockId.size() > 0 && data.blockId.size() <= 50
          && data.blueprintId is string && data.blueprintId.size() > 0 && data.blueprintId.size() <= 100
          && data.locale is string && data.locale.size() > 0 && data.locale.size() <= 10
          && data.mode is string && data.mode.size() > 0 && data.mode.size() <= 20
          && data.reasonCode is string && data.reasonCode.size() > 0 && data.reasonCode.size() <= 50
          && data.status is string && data.status == 'OPEN'
          && data.createdAt is timestamp && data.createdAt <= request.time

          // Optional userComment max length
          && (!('userComment' in data) || (data.userComment is string && data.userComment.size() <= 500))

          // Block PII fields
          && !data.keys().hasAny(['email', 'userName', 'fullName', 'phone', 'userId'])
        );
      }

      // Allow authenticated users to create new reports (write-only)
      // Users cannot read their own or others' reports from the app
      allow create: if request.auth != null && isValidReport();

      // Deny read/update/delete for regular users
      allow read, update, delete: if false;
    }

    // Admin/backend access (requires custom claim or service account)
    // Uncomment if you have admin users with custom claims:
    // match /question_reports/{reportId} {
    //   allow read, update: if request.auth != null && request.auth.token.admin == true;
    //   allow delete: if false;  // Never allow deletes; use soft-delete via status field instead
    // }
  }
}
```

**Note:** Admin SDK and service accounts bypass Firestore security rules by default. For backend services, no additional rule configuration is needed.

---

## Rule Breakdown

### 1. Create (Submit Report) with Allowlist Validation

Authenticated users can submit new reports via `allow create: if request.auth != null && isValidReport();`

The `isValidReport()` helper function validates the report payload (see full implementation in the Recommended Firestore Rules section above).

**What it does:**
- **Allowlist validation:** Uses `hasOnly()` to ensure ONLY expected fields are present (no unexpected keys allowed)
- **Required fields:** Validates presence of `questionId`, `taskId`, `blockId`, `blueprintId`, `locale`, `mode`, `reasonCode`, `status`, `createdAt`
- **Type validation:** Enforces that fields are correct types (string, timestamp, etc.)
- **Length constraints:** Limits string lengths (`questionId` ≤ 100 chars, `taskId` ≤ 50 chars, `blockId` ≤ 50 chars, `blueprintId` ≤ 100 chars, `locale` ≤ 10 chars, `mode` ≤ 20 chars, `reasonCode` ≤ 50 chars, `userComment` ≤ 500 chars)
- **Status enforcement:** New reports must have `status == 'OPEN'` (prevents status manipulation)
- **Timestamp validation:** `createdAt` must be a timestamp ≤ request.time (prevents future-dated reports)
- **PII blocking:** Explicitly blocks `email`, `userName`, `fullName`, `phone`, `userId` fields

**Why:**
- Allowlist (`hasOnly`) is **more secure** than `hasAll` (which is permissive and allows extra keys)
- Type and length constraints prevent malicious payloads (e.g., extremely long strings that could DOS Firestore)
- PII validation at the rule level adds defense-in-depth beyond client-side sanitization
- Timestamp validation prevents clock manipulation attacks

### 2. Read/Update/Delete (Denied for Users)

```javascript
allow read, update, delete: if false;
```

**What it does:**
- Prevents regular users from reading, updating, or deleting reports

**Why:**
- Reports are moderated by admins/backend services, not end users
- Users submitting a report should not see other users' reports or modify their own after submission

### 3. Admin/Backend Access

**Option A: Admin SDK / Service Account (Recommended)**

Firebase Admin SDK and service accounts bypass Firestore security rules automatically. No additional rule configuration needed.

Use this approach for:
- Backend services (Node.js, Python, etc.)
- Cloud Functions with Admin SDK
- Server-side moderation/triage workflows

**Option B: Custom Admin Claims (For Web Admin Dashboards)**

If you have a web admin dashboard with Firebase Auth users, uncomment the following rule in the `/question_reports/{reportId}` match block:

```javascript
// Add this INSIDE the existing match /question_reports/{reportId} block:
allow read, update: if request.auth != null && request.auth.token.admin == true;
allow delete: if false;  // Never allow deletes; use soft-delete via status field instead
```

This allows users with a custom `admin` claim to read and update reports, but still denies deletes (use soft-delete via `status` field instead).

To set the admin claim, use the Admin SDK:
```javascript
admin.auth().setCustomUserClaims(uid, { admin: true });
```

---

## Checklist: Reviewing & Deploying Firestore Rules

Use this checklist to ensure your Firestore rules are secure and correct before deploying them:

### Pre-Deployment Review

- [ ] **Allowlist Validation:** Verify that the rules use `hasOnly()` to restrict payloads to expected fields only (not just `hasAll()`)
- [ ] **Required Fields:** Confirm that required fields (`questionId`, `taskId`, `blockId`, `blueprintId`, `locale`, `mode`, `reasonCode`, `status`, `createdAt`) match the payload structure in `QuestionReportPayloadBuilder.kt`
- [ ] **Type Validation:** Check that field types are validated (strings are strings, timestamps are timestamps, etc.)
- [ ] **Length Constraints:** Ensure string fields have reasonable length limits (`questionId` ≤ 100, `locale` ≤ 10, `reasonCode` ≤ 50, `userComment` ≤ 500)
- [ ] **Status Enforcement:** Verify that new reports are forced to `status == 'OPEN'` to prevent status manipulation
- [ ] **Timestamp Validation:** Confirm that `createdAt` is validated as a timestamp ≤ request.time
- [ ] **PII Blocking:** Verify that the rules block PII fields (`email`, `userName`, `fullName`, `phone`, `userId`)
- [ ] **Read Access:** Ensure regular users cannot read reports (only admins/backend should have read access)
- [ ] **Write Access:** Verify that only authenticated users can submit reports (`request.auth != null`)
- [ ] **Delete Access:** Confirm that deletes are denied for all users (use soft-delete via `status` field instead)

### Deployment Steps

1. **Update rules file:**
   - Edit `firestore.rules` in your Firebase project (or via Firebase Console → Firestore → Rules)
   - Copy the recommended rules from this document

2. **Test rules locally (optional):**
   - Use Firebase Emulator Suite to test rules locally before deploying:
     ```bash
     firebase emulators:start --only firestore
     ```
   - Run the app against the emulator and submit a test report
   - Verify that:
     - Authenticated users can submit reports
     - Unauthenticated users are denied
     - PII fields are blocked
     - Users cannot read reports

3. **Deploy rules:**
   - Via Firebase CLI:
     ```bash
     firebase deploy --only firestore:rules
     ```
   - Or via Firebase Console → Firestore → Rules → Publish

4. **Verify deployment:**
   - Check Firebase Console → Firestore → Rules to see the active rules
   - Submit a test report from the app (production or staging environment)
   - Confirm the report appears in the `/question_reports` collection
   - Attempt to read the report from the app (should fail with permission denied)

### Post-Deployment Monitoring

- [ ] **Monitor rule violations:** Check Firebase Console → Firestore → Usage → Security rules for denied requests
- [ ] **Check for PII leaks:** Manually inspect a sample of reports in the Firestore console to ensure no PII fields are present
- [ ] **Review admin access logs:** If using custom claims, verify that only authorized admins can read/update reports

---

## Access Patterns

### Who Can Read Reports?

- **Regular users:** ❌ No read access
- **Admin users (with custom `admin` claim):** ✅ Read/update access (if uncommented in rules)
- **Backend services (Firebase Admin SDK or service accounts):** ✅ Full access (bypasses rules)

### Who Can Write Reports?

- **Authenticated users:** ✅ Can create new reports (write-only)
- **Unauthenticated users:** ❌ No access
- **Backend services:** ✅ Can create/update/delete via Admin SDK

### Who Can Delete Reports?

- **Regular users:** ❌ No delete access
- **Admin users:** ❌ No delete access (use soft-delete via `status` field)
- **Backend services:** ✅ Can delete via Admin SDK (use sparingly; prefer soft-delete)

---

## PII Protection

### Fields Explicitly Blocked

The rules block these PII fields:
- `email`
- `userName`
- `fullName`
- `phone`
- `userId`

### Allowed Metadata

The following metadata is allowed (non-PII, coarse-grained):
- `appVersionName`, `appVersionCode`
- `buildType` (debug/release)
- `platform` (android)
- `androidVersion` (e.g., "34")
- `deviceModel` (e.g., "Pixel 6")
- `sessionId`, `attemptId` (random UUIDs, not linked to user identity)

### User-Provided Content

- **`userComment`:** Optional free-text field; users are advised via UI to avoid PII
- **`reasonCode`/`reasonDetail`:** Predefined enum values (no PII risk)

**Note:** The client-side code (`QuestionReportPayloadBuilder`) already sanitizes payloads to exclude PII, but the Firestore rules provide a second layer of defense.

---

## Troubleshooting

### "Permission denied" when submitting a report

- **Cause:** User is not authenticated, or required fields are missing
- **Fix:** Ensure `request.auth != null` and all required fields (`questionId`, `taskId`, `blockId`, `blueprintId`, `locale`, `mode`, `reasonCode`, `status`, `createdAt`) are present in the payload

### "Permission denied" when reading reports from admin dashboard

- **Cause:** Admin custom claim is not set, or rules don't allow admin read access
- **Fix:**
  - Set custom `admin` claim for admin users via Firebase Admin SDK:
    ```javascript
    admin.auth().setCustomUserClaims(uid, { admin: true });
    ```
  - Uncomment the admin rules in `firestore.rules`

### PII fields are present in reports

- **Cause:** Client-side sanitization bypassed, or rules not deployed
- **Fix:**
  - Verify Firestore rules are deployed and include PII field blocking
  - Review `QuestionReportPayloadBuilder.kt` to ensure PII fields are never added to payloads
  - Manually delete PII-containing reports and redeploy corrected rules

---

## References

- [Firestore Security Rules Documentation](https://firebase.google.com/docs/firestore/security/get-started)
- [Firebase Auth Custom Claims](https://firebase.google.com/docs/auth/admin/custom-claims)
- [Firebase Admin SDK](https://firebase.google.com/docs/admin/setup)
- [QWeld Privacy Policy](./PRIVACY.md)
- [Question Reporting Implementation](../core-data/src/main/java/com/qweld/app/data/reports/FirestoreQuestionReportRepository.kt)

---

## Example: Setting Admin Custom Claims

If you want to grant admin access to a specific Firebase Auth user:

```javascript
// Node.js example using Firebase Admin SDK
const admin = require('firebase-admin');
admin.initializeApp();

const uid = 'user-uid-here';
admin.auth().setCustomUserClaims(uid, { admin: true })
  .then(() => {
    console.log(`Admin claim set for user ${uid}`);
  })
  .catch((error) => {
    console.error('Error setting custom claims:', error);
  });
```

After setting the claim, the user must sign out and sign in again for the claim to take effect.

---

For questions or updates to these rules, contact the QWeld development team or open an issue in the GitHub repository.
