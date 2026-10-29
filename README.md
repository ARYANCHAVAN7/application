# SURAKSHA 🚨

**An Android emergency-response network** that connects people in danger with nearby hospitals and ambulances — with live map tracking, real-time status updates, and an AI-powered first-aid assistant.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Java-orange)](https://www.java.com/)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Mapbox](https://img.shields.io/badge/Maps-Mapbox-4264FB?logo=mapbox&logoColor=white)](https://www.mapbox.com/)
[![Groq](https://img.shields.io/badge/AI-Groq-F55036)](https://groq.com/)

> Repository: **https://github.com/Apurva2004coder/Suraksha**

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Configuration: API keys & environment file](#configuration-api-keys--environment-file)
- [Getting the API keys](#getting-the-api-keys)
  - [Firebase](#1-firebase)
  - [Mapbox](#2-mapbox)
  - [Groq](#3-groq)
  - [Google Sign-In client ID](#4-google-sign-in-client-id)
- [Build & run](#build--run)
- [Firestore data model](#firestore-data-model)
- [Security notes](#security-notes)
- [Troubleshooting](#troubleshooting)

---

## Features

### 👤 User
- Email/password registration and login, plus Google Sign-In.
- **One-tap SOS** with a fresh GPS fix.
- Automatically selects the **nearest verified hospital with available beds** using radius tiers and Haversine distance.
- Creates exactly one active emergency request at a time.
- **Live map** showing the hospital, assigned ambulance, route, distance, and ETA.
- Emergency contact quick-call.
- **AI First-Aid Assistant** powered by Groq (`openai/gpt-oss-120b`).
- Emergency history and real-time status updates.

### 🏥 Hospital
- Hospital registration, followed by admin verification.
- Dashboard with bed and ambulance resource management.
- Incoming emergency requests with accept/dispatch flow (Firestore transaction, bed count decrement).
- Ambulance fleet overview.

### 🚑 Ambulance driver
- Login with Firebase email/password **or** hospital name + security number (anonymous session).
- Accept or reject dispatched emergencies.
- Foreground location service that publishes live driver coordinates.
- Navigation to the emergency location and then to the hospital.

### 🛡️ Admin
- Hospital verification/approval screen.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 11 |
| UI | XML layouts, AndroidX AppCompat, Material Components, ConstraintLayout |
| SDK | minSdk 24, targetSdk 36, compileSdk 36 |
| Build | Gradle 9.5, Android Gradle Plugin 9.0.0 |
| Authentication | Firebase Authentication (email/password, Google, anonymous) |
| Database | Cloud Firestore (real-time listeners) + security rules/indexes |
| Storage | Firebase Storage (dependency available) |
| Maps & routing | Mapbox Maps SDK 11.29.1 + Mapbox Directions API |
| AI assistant | Groq Chat Completions API via OkHttp |
| Location | Google Play Services Fused Location Provider + foreground service |

---

## Project structure

```text
Suraksha/
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json          # Firebase client config (replace for your project)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/application/
│       │   │   ├── MainActivity.java             # hospital login
│       │   │   ├── UserLoginActivity.java        # user login
│       │   │   ├── AmbulanceLoginActivity.java   # driver login
│       │   │   ├── DashboardActivity.java        # user dashboard + SOS
│       │   │   ├── HospitalActivity.java         # hospital dashboard
│       │   │   ├── AmbulanceActivity.java        # driver dashboard
│       │   │   ├── UserMapActivity.java          # live emergency map
│       │   │   ├── FirstAidChatActivity.java     # AI first-aid chat
│       │   │   ├── AdminApprovalActivity.java    # hospital verification
│       │   │   ├── DriverLocationService.java    # foreground location service
│       │   │   └── utils/                        # routing, selection, AI, helpers
│       │   └── res/
│       │       └── values/
│       │           ├── mapbox_access_token.xml.example
│       │           └── mapbox_access_token.xml   # local only, git-ignored
│       └── test/                                 # unit tests
├── firestore.rules
├── firestore.indexes.json
├── firebase.json
├── local.properties.example          # template for API keys
├── gradle/libs.versions.toml
├── Makefile
└── README.md
```

---

## Prerequisites

- **Android Studio** with support for AGP 9.0 / SDK 36 (or the command-line SDK).
- **JDK 17 or newer** (Gradle 9.5 requirement; the project compiles with Java 11 compatibility).
- **Android SDK Platform 36** and build tools installed.
- An Android device or emulator running **API 24+**.
- Accounts for **Firebase**, **Mapbox**, and **Groq** (all have free tiers).
- Optional: **Node.js + firebase-tools** to deploy Firestore rules/indexes.

---

## Configuration: API keys & environment file

Android projects do **not** use a `.env` file. The equivalent environment/configuration file is **`local.properties`** in the project root. It is already listed in `.gitignore`, so your real keys are never committed.

### Step 1 — create your config file

```bash
cp local.properties.example local.properties
```

Then edit `local.properties` and fill in the values:

```properties
sdk.dir=/path/to/Android/Sdk
MAPBOX_ACCESS_TOKEN=pk.your_mapbox_public_token_here
GROQ_API_KEY=gsk_your_groq_api_key_here
GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

| Key | Required | Where it comes from | Used for |
|---|---|---|---|
| `sdk.dir` | ✅ | Android SDK location (Android Studio writes this) | Gradle builds |
| `MAPBOX_ACCESS_TOKEN` | ✅ | [account.mapbox.com/access-tokens](https://account.mapbox.com/access-tokens/) | Map rendering + Directions API |
| `GROQ_API_KEY` | ✅ for AI chat | [console.groq.com/keys](https://console.groq.com/keys) | AI first-aid assistant |
| `GOOGLE_WEB_CLIENT_ID` | ⚠️ for Google Sign-In | Firebase Console / Google Cloud Console | Google Sign-In |

The Gradle build reads these values and exposes them as `BuildConfig` fields, so **rebuild after changing them** (`./gradlew clean assembleDebug`).

---

## Getting the API keys

### 1. Firebase

The app uses Firebase Authentication, Cloud Firestore, and Firebase Storage.

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a project (or reuse `suraksha-emergency-app`).
2. Add an **Android app** with package name:
   ```text
   com.example.application
   ```
3. Download **`google-services.json`** and place it at:
   ```text
   app/google-services.json
   ```
   > The repository already contains a client config for the demo project `suraksha-emergency-app`. It lets you explore the app, but you should create your own project for real use. If you switch projects, also update `.firebaserc`.
4. In **Authentication → Sign-in method**, enable:
   - **Email/Password** — users, hospitals, and driver logins.
   - **Google** — Google Sign-In (also gives you the Web Client ID below).
   - **Anonymous** — required for the "hospital name + security number" driver login.
5. Create a **Firestore Database** (production mode).
6. Deploy the included rules and indexes:
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase use --add          # select your Firebase project
   firebase deploy --only firestore:rules,firestore:indexes
   ```
   `firebase.json` already points to `firestore.rules` and `firestore.indexes.json`.

---

### 2. Mapbox

Mapbox provides the map tiles and the Directions API used for routes/ETA.

1. Create a free account at [mapbox.com](https://www.mapbox.com/).
2. Open the [Access Tokens page](https://account.mapbox.com/access-tokens/).
3. Click **Create a token** and keep the default **public** scopes (`styles:read`, `fonts:read`, `directions:read`, etc.).
4. Copy the token — public tokens start with **`pk.`**
5. Add it to `local.properties`:
   ```properties
   MAPBOX_ACCESS_TOKEN=pk.your_token_here
   ```
6. The map widget also needs the token as an Android string resource. Copy the template:
   ```bash
   cp app/src/main/res/values/mapbox_access_token.xml.example \
      app/src/main/res/values/mapbox_access_token.xml
   ```
   Then replace the placeholder with the same `pk.` token. This real file is git-ignored.

> Mapbox Maps SDK 11.29.1 is downloaded from Mapbox's public Maven endpoint, so no secret downloads token is required for this version.

---

### 3. Groq

Groq powers the AI First-Aid chat using the `openai/gpt-oss-120b` model.

1. Sign in at [console.groq.com](https://console.groq.com/).
2. Open [API Keys](https://console.groq.com/keys) and click **Create API Key**.
3. Copy the key — it starts with **`gsk_`**.
4. Add it to `local.properties`:
   ```properties
   GROQ_API_KEY=gsk_your_key_here
   ```
5. Rebuild the app.

You can also enter/replace the key at runtime from the **key icon** on the AI chat screen; it is stored in the app's `SharedPreferences` (and overrides the build value). Test the key from the terminal with:

```bash
make test-groq
```

---

### 4. Google Sign-In client ID

Google Sign-In uses Credential Manager + a Google OAuth **Web client ID**.

1. In the Firebase Console, go to **Authentication → Sign-in method → Google**.
2. Enable Google and copy the **Web client ID** from the **Web SDK configuration** section (format `xxxxxxxx.apps.googleusercontent.com`).
   - Alternatively: **Google Cloud Console → APIs & Services → Credentials → OAuth 2.0 Client IDs**.
3. Add it to `local.properties`:
   ```properties
   GOOGLE_WEB_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
   ```
4. Register your app's **SHA-1 fingerprint** in Firebase:
   - Get the debug fingerprint:
     ```bash
     ./gradlew signingReport
     ```
   - Firebase Console → **Project settings → Your apps → Add fingerprint** → paste the SHA-1.
5. Rebuild and reinstall the app.

Without these steps the **Sign in with Google** button reports a missing client ID; email/password login still works.

---

## Build & run

### Android Studio
1. Open the project folder in Android Studio.
2. Let Gradle sync (it downloads dependencies and creates `local.properties` if missing).
3. Configure the API keys as described above.
4. Select a device/emulator and click **Run ▶**.

### Command line

```bash
# Build the debug APK
./gradlew assembleDebug

# APK output
# app/build/outputs/apk/debug/app-debug.apk

# Install on a connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run unit tests
./gradlew testDebugUnitTest
```

### Makefile shortcuts

The included `Makefile` wraps common tasks. Its default `JAVA_HOME` / `ANDROID_HOME` paths may need editing for your machine; override them on the command line if necessary:

```bash
make build JAVA_HOME=/path/to/jdk ANDROID_HOME=/path/to/android-sdk
```

| Command | Action |
|---|---|
| `make build` / `make apk` | Build the debug APK and copy it to `Suraksha-Latest.apk` |
| `make test` | Run unit tests |
| `make install` | Install the APK on the connected device |
| `make run` | Install and launch the app |
| `make run-chat` | Launch the AI First-Aid screen directly |
| `make test-groq` | Test `GROQ_API_KEY` against the Groq API |
| `make logcat` | Stream filtered app logs |
| `make devices` | List ADB devices |
| `make connect` | Connect ADB to the configured Genymotion emulator |

---

## Firestore data model

| Collection | Purpose |
|---|---|
| `users` | All accounts (user / hospital / admin), profile data, `role`, `isVerified`, hospital resources |
| `emergencies` | SOS lifecycle, assigned hospital/ambulance, live coordinates, status |
| `ambulances` | Ambulance fleet, driver details, availability |
| `ambulanceLogins` | Hashed hospital login keys and generated driver credentials |
| `driverSessions` | Driver session → ambulance binding used by security rules |
| `driverLocations` | Live driver coordinates published by the foreground service |

Security rules live in `firestore.rules`; composite indexes live in `firestore.indexes.json`.

---

## Security notes

- **Never commit** `local.properties` or `app/src/main/res/values/mapbox_access_token.xml` — both are already in `.gitignore`.
- If a key is ever exposed, rotate it immediately in the provider console.
- Values in `BuildConfig` are embedded in the APK and can be extracted. For production, proxy the Groq API through a backend instead of shipping the key in the app.
- The bundled `google-services.json` is a client configuration, not a server secret, but it should still point to **your** Firebase project for production.
- Deploy and review `firestore.rules` before going live; never leave Firestore in test mode.
- Mapbox public tokens are meant to be embedded; restrict them in the Mapbox dashboard if needed.
- The driver login flow has a documented brute-force limitation in `firestore.rules`; use Firebase App Check / a Cloud Function for production.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `Mapbox access token not configured` | Set `MAPBOX_ACCESS_TOKEN` in `local.properties` and rebuild. |
| `resource string/mapbox_access_token not found` | Copy `mapbox_access_token.xml.example` to `mapbox_access_token.xml` and add your token. |
| Google Sign-In says client ID is missing | Set `GOOGLE_WEB_CLIENT_ID` in `local.properties` and rebuild. |
| Google Sign-In fails after login attempt | Add your debug/release SHA-1 in Firebase Project Settings. |
| Groq returns `401` / invalid API key | Recheck the `gsk_...` key with `make test-groq`; rotate it if needed. |
| Firestore `PERMISSION_DENIED` | Deploy `firestore.rules` and make sure the user is authenticated with the expected role. |
| Build fails with `google-services.json` missing | Ensure the file is at `app/google-services.json`. |

---

> This project is intended for educational and emergency-response prototyping purposes. It is not a substitute for professional medical or emergency services. Always call your local emergency number (e.g. **108**) in a real emergency.
