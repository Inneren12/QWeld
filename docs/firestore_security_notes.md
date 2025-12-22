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

      // Allow authenticated users to create new reports (write-only)
      // Users cannot read their own or others' reports from the app
      allow create: if request.auth != null
                    && request.resource.data.keys().hasAll(['questionId', 'locale', 'reasonCode', 'status', 'platform', 'createdAt'])
                    && request.resource.data.status == 'OPEN'
                    && !request.resource.data.keys().hasAny(['email', 'userName', 'fullName', 'phone', 'userId']);

      // Deny read/update/delete for regular users
      allow read, update, delete: if false;
    }

    // Admin/backend access (requires custom claim or service account)
    // Uncomment and adjust if you have admin users with custom claims:
    // match /question_reports/{reportId} {
    //   allow read, update: if request.auth.token.admin == true;
    //   allow delete: if false;  // Never allow deletes even for admins; soft-delete via status instead
    // }
  }
}
```

---

## Rule Breakdown

### 1. Create (Submit Report)

```javascript
allow create: if request.auth != null
              && request.resource.data.keys().hasAll(['questionId', 'locale', 'reasonCode', 'status', 'platform', 'createdAt'])
              && request.resource.data.status == 'OPEN'
              && !request.resource.data.keys().hasAny(['email', 'userName', 'fullName', 'phone', 'userId']);
```

**What it does:**
- Allows any authenticated user to create a new report
- Requires essential fields to be present (`questionId`, `locale`, `reasonCode`, `status`, `platform`, `createdAt`)
- Enforces that new reports have `status == 'OPEN'` (prevents users from setting arbitrary statuses)
- **Blocks PII fields** (`email`, `userName`, `fullName`, `phone`, `userId`) to ensure no personally identifiable information leaks into reports

**Why:**
- Users need write access to submit reports
- Users should NOT be able to read reports (prevents data leakage, spam, or abuse)
- PII validation at the rule level adds an extra layer of protection beyond client-side sanitization

### 2. Read/Update/Delete (Denied for Users)

```javascript
allow read, update, delete: if false;
```

**What it does:**
- Prevents regular users from reading, updating, or deleting reports

**Why:**
- Reports are moderated by admins/backend services, not end users
- Users submitting a report should not see other users' reports or modify their own after submission

### 3. Admin/Backend Access (Optional)

```javascript
// Uncomment if you have admin users with custom claims:
// allow read, update: if request.auth.token.admin == true;
// allow delete: if false;
```

**What it does:**
- Allows users with a custom `admin` claim to read and update reports
- Still denies deletes (use soft-delete via `status` field instead)

**When to use:**
- If you have a web admin dashboard with Firebase Auth users who have an `admin` custom claim
- For server-side triage/moderation workflows

**Alternative:**
- Use Firebase Admin SDK or service accounts for backend access (no custom rules needed; service accounts bypass rules by default)

---

## Checklist: Reviewing & Deploying Firestore Rules

Use this checklist to ensure your Firestore rules are secure and correct before deploying them:

### Pre-Deployment Review

- [ ] **PII Validation:** Verify that the rules block PII fields (`email`, `userName`, `fullName`, `phone`, `userId`)
- [ ] **Required Fields:** Confirm that required fields match the payload structure in `QuestionReportPayloadBuilder.kt`
- [ ] **Status Enforcement:** Check that new reports are forced to `status == 'OPEN'` to prevent status manipulation
- [ ] **Read Access:** Ensure regular users cannot read reports (only admins/backend should have read access)
- [ ] **Write Access:** Verify that only authenticated users can submit reports (`request.auth != null`)
- [ ] **Delete Access:** Confirm that deletes are denied (use soft-delete via `status` field instead)

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
- **Fix:** Ensure `request.auth != null` and all required fields (`questionId`, `locale`, `reasonCode`, `status`, `platform`, `createdAt`) are present in the payload

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
