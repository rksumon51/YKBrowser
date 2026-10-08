# Firebase setup for YKBrowser

The cloud vault needs a Firebase project. This is a one-time, ~10 minute setup
in your own Google account.

## 1. Create the Firebase project

1. Go to https://console.firebase.google.com and click **Add project**.
2. Name it e.g. `ykbrowser`, disable Google Analytics (not needed), create.

## 2. Add the Android app

1. In the project overview click the **Android** icon.
2. Android package name: `com.yk.browser`
3. App nickname: `YKBrowser` (debug signing certificate SHA-1 can be skipped for now —
   but **Google Sign-In needs your SHA-1**: add it later under Project settings → Your apps
   → SHA certificate fingerprints. Get yours via
   `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`).
4. Download `google-services.json` and place it in the repo's `app/` folder
   (next to `app/build.gradle`). It is git-ignored by design — never commit it.

## 3. Enable Authentication

1. Build → **Authentication** → Get started.
2. Sign-in method → enable **Google**.
3. Add a support email when asked.

## 4. Create Firestore

1. Build → **Firestore Database** → Create database.
2. Start in **production mode**, choose a region.
3. Rules tab — paste:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Each user can only read/write their own vault
    match /users/{uid}/vault/{doc} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

## 5. Build & run

Open the project in Android Studio (JDK 17), let Gradle sync, and run on a
device/emulator. In the app: Settings → Sign in with Google → Set backup PIN →
Backup now.

## How the vault stays private

`CloudBackupProvider` encrypts the whole vault JSON with your backup PIN
(PBKDF2-HMAC-SHA256 → AES-256-GCM) **on the phone** before uploading.
Firestore only ever stores ciphertext — Firebase/Google cannot read your
passwords or cookies.
