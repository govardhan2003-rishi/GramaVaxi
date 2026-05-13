# Grama Vaxi Feature Setup

## Firebase collections

- `users/{uid}`: `uid`, `name`, `email`, `phone`, `role` (`farmer`, `doctor`, `admin`), `createdAt`
- `farmers/{phoneDoc}`: farmer profile, role, auth uid, nested `animals`, `vaccine_records`, `disease_reports`
- `veterinary_doctors/{phoneDoc}`: doctor profile, role, location, phone, email
- `vet_sick_reports/{reportId}`: farmer details, animal details, symptoms, notes, `status`, GPS, payment summary
- `payments/{paymentId}`: `farmerId`, `doctorId`, `reportId`, `amount`, `paymentStatus`, `transactionId`, `gateway`, `createdAt`
- `notifications/{notificationId}`: target uid/phone, title, body, type, createdAt, readAt

## Firebase Authentication

1. Enable Email/Password sign-in.
2. Enable Phone sign-in and add SHA-1/SHA-256 fingerprints for the Android app.
3. Keep `app/google-services.json` updated after changing package name or SHA keys.
4. Deploy `firebase-firestore.rules` from this repo to Firestore.

### Fix `[BILLING_NOT_ENABLED]` for Phone OTP

This error is returned by Firebase before app code can send an OTP. Fix it in Firebase/Google Cloud:

1. Open Firebase console for `grama-vaxi-8f6df`.
2. Upgrade the project to the Blaze plan.
3. Link an active Google Cloud billing account.
4. Enable Authentication > Sign-in method > Phone.
5. Add the app SHA-1 and SHA-256 fingerprints under Project settings > Android app.
6. Download the latest `google-services.json` and replace `app/google-services.json`.
7. Rebuild and reinstall the APK.

Forgot-password note: the Android client verifies phone OTP, then uses Firebase Auth password-reset email/link for the actual password reset. Directly setting a forgotten password after OTP must be done by a trusted server or Cloud Function using Firebase Admin SDK.

## Google Maps and location

- The app captures device latitude/longitude using Android location providers.
- Doctor navigation opens Google Maps with `google.navigation:q=lat,lng`.
- For an embedded map preview, add the Maps SDK dependency and a restricted Android API key, then render the stored coordinates in the doctor report card.

## Payments

- Payment requests are stored in `payments/`.
- The current Android implementation creates a verified server-side payment request record and marks the sick report as `Payment requested`.
- For Razorpay production:
  1. Create an order in a trusted backend or Cloud Function.
  2. Open Razorpay Checkout in Android with the order id.
  3. Verify `razorpay_order_id`, `razorpay_payment_id`, and `razorpay_signature` server-side.
  4. Only the backend should update `payments/{id}.paymentStatus = "paid"` and write `transactionId`.

## Notifications

- Local notifications are used for new realtime sick reports on the doctor dashboard.
- For Firebase Cloud Messaging production pushes:
  1. Add `firebase-messaging` dependency.
  2. Save each user's FCM token under `users/{uid}/fcmTokens`.
  3. Trigger Cloud Functions on `vet_sick_reports` and `payments` writes.
  4. Send notifications for sick report created, doctor accepted, payment requested, payment successful, and password reset sent.

## Build

Use Android Studio JBR if Java is not on PATH:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```
