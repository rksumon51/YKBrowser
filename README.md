# YKBrowser

A private Android browser with a **vault**: your logins, bookmarks, history and
logged-in sessions are backed up — encrypted — to the cloud, your phone, or both.
Reinstall the app, sign in, enter your backup PIN, and everything comes back
"same to same".

## Features

- **WebView browser** — address bar, back/forward, progress, login-form detection
- **Saved logins** — password forms are detected on submit; the app asks before saving
- **Session restore** — visible session cookies are captured per site and re-applied on restore,
  so logged-in pages come back logged in (HttpOnly cookies can't be captured — those sites need one fresh login)
- **Browser account** — Firebase Auth (Google Sign-In); the account scopes your cloud vault
- **Cloud backup** — PIN-encrypted vault in Firestore (`users/{uid}/vault/data`), auto-synced every 6h on Wi-Fi-ish connectivity
- **Local backup** — encrypted auto-backup file in app storage + manual export/import of `.ykb` files
- **Encryption** —
  - At rest (Room): AES-256-GCM with Android Keystore
  - Vault (cloud / file): AES-256-GCM with a key derived from your backup PIN via PBKDF2-HMAC-SHA256 (120k iterations).
    The PIN never leaves the phone and is kept in RAM only for background sync.

## Architecture

```
UI (MainActivity, LoginsActivity, SettingsActivity)
        │
BackupManager ──► BackupProvider (interface)
                   ├── LocalBackupProvider  → app-private .ykb / SAF export-import
                   └── CloudBackupProvider  → Firestore users/{uid}/vault/data
        │
VaultPayload (logins, bookmarks, history, cookies) → JSON → VaultCrypto(PIN) → ciphertext
        │
Room (AppDatabase) ← CryptoManager (Keystore) for at-rest secrets
        │
AuthManager (Firebase Auth + Google Sign-In)   SyncWorker (WorkManager, 6h)
```

New backup destinations only need a `BackupProvider` implementation — the UI and
`BackupManager` don't change.

## Setup

1. Create a Firebase project and add an Android app with package `com.yk.browser`
   (full steps: [FIREBASE_SETUP.md](FIREBASE_SETUP.md)).
2. Download `google-services.json` into `app/`.
3. Open in Android Studio (JDK 17) and run.

## Project notes

- `minSdk 24`, `targetSdk 34`, Kotlin 1.9.22, AGP 8.2.0
- The old misnamed `​app` folder (invisible character) was replaced with a proper `app` module.
