# SURAKSHA — Complete End-to-End Implementation Report

Date: 23 Sep 2026 · Repo: `arjun02003/pce_project` (base commit `25f557c`) · Package / applicationId: `com.example.application`

## 0. Read this first — what was and was not verified

This work was done in a sandbox that has **no Android SDK, no access to Google's Maven repository or Mapbox's Maven repository, no Android device/emulator, and no Firebase credentials**. Consequences:

- `gradlew clean assembleDebug test` **could not be run**. The build is **NOT VERIFIED**.
- None of the 21 runtime tests could be executed. Every runtime feature is **NOT TESTED**.
- `firestore.rules` and `firestore.indexes.json` were written but **not deployed and not emulator-tested** (the Firestore emulator binary is also downloaded from a blocked host).

What *was* verified here:

- All 27 main Java files + the new JUnit test parse (javalang); every `R.id / R.layout / R.string / R.color / R.anim` reference resolves to an existing resource; every call into the project's own classes resolves to an existing method/constant; all layouts and the manifest are well-formed XML.
- The **real source files** of `HaversineDistance`, `GeoRadius`, `AmbulanceSelection`, `EmergencyPriority`, `FirestoreValues`, `EmergencyStatus` and `DriverCredentials` were compiled with the JDK compiler (against a minimal `DocumentSnapshot` stand-in that is as type-strict as the real one) and executed: **30/30 logic checks passed** (details in §9).
- Grep checks: no legacy `"pending"/"active"/"accepted"/"completed"` emergency statuses remain; nothing logs a password, security number, login key or token.

Nothing below is claimed to "work on a device". It is implemented, statically checked, and — for the algorithms — logic-tested.

## 1. Firebase project

From `app/google-services.json`:

| Field | Value |
|---|---|
| project_id | **suraksha-d0ac8** |
| project_number | **903850805671** |
| package / applicationId | com.example.application |

Earlier conversations referred to `suraksha-88914`; the app does **not** use that project. `.firebaserc` was added with default = `suraksha-d0ac8` so `firebase deploy` cannot silently hit another project.

## 2. Authentication model (as implemented)

| Actor | Identity | Where role/permission lives |
|---|---|---|
| User | Firebase email/password (or Google) | `users/{uid}.role == "user"` |
| Hospital | Firebase email/password | `users/{uid}.role == "hospital"`, `isVerified`, `approvalStatus` |
| Admin | Firebase account created manually | `users/{uid}.role == "admin"` |
| Ambulance driver | **Firebase Anonymous Auth** + hospitalName/securityNumber | `driverSessions/{anonUid}` → one `ambulanceId` |

No custom claims are used anywhere (the rules never read `request.auth.token.role`).

**Why drivers now use Anonymous Auth.** The existing hospitalName + securityNumber UX is preserved exactly. But without *any* Firebase identity, Firestore rules cannot distinguish a driver from the public internet, so the old design required ambulances/emergencies to be world-readable/writable (and stored the security number in plain text in `ambulances`). The implemented design:

1. Driver taps login → app signs in anonymously.
2. App computes `loginKey = sha256(normalized(hospitalName) + "|" + securityNumber)` and GETs `ambulanceLogins/{loginKey}` → `{ambulanceId, hospitalId}`. Rules allow GET by exact ID only; **listing is denied**.
3. App creates `driverSessions/{anonUid} = {ambulanceId, loginKey}`. Rules only allow this if the login key really maps to that ambulance.
4. Every driver read/write is authorised as "the session's ambulance". Deleting the `ambulanceLogins` doc revokes all sessions for it.

The security number is **no longer stored** anywhere in Firestore (only its salted-by-hospital-name hash as a document ID), and it is never logged.

**Honest limitation:** rules cannot rate-limit. A short security number can be guessed online by repeatedly GETting candidate keys. The app enforces ≥ 6 characters. For production, add **Firebase App Check** and move driver login into a **Cloud Function** (callable) that verifies credentials and issues a custom token.

## 3. User registration / login / profile

- **Register** (`RegistrationActivity`): creates Auth account → `users/{uid}` with `uid, name, fullName, email, phone, bloodGroup, emergencyName, emergencyPhone, role:"user", createdAt`. If the email exists: shows exactly *"Email already registered. Please login or use another email."* and **never** signs in or rewrites the existing profile. If the Firestore write fails, the new Auth user is deleted (rollback) so the email is not left orphaned.
- The old "repair" paths that converted an existing account between user and hospital were removed (both registration screens).
- **Login** (`UserLoginActivity`, unchanged logic + logging): loads `users/{uid}` from the server after sign-in and requires `role == "user"` (or admin); a hospital account is signed out, not routed.
- **Session restore** (`StartingActivity`): role is re-read from Firestore; hospitals must also be `isVerified == true`; anonymous sessions are restored only if a `driverSessions` doc exists; unknown/missing profiles are signed out (fixes the old stale-role redirect loop).
- **Profile** (`DashboardActivity`): shows name, email, phone, blood group, emergency contact; non-user roles are signed out.

## 4. Hospital registration, admin approval, hospital login

- **Registration** writes `role:"hospital", isVerified:false, approvalStatus:"pending", hospitalName, email, phone, address, licenseNumber, totalBeds/availableBeds, totalAmbulances/availableAmbulances` as **numbers**, plus **latitude/longitude** (new fields + "Use my current location" button). Hospitals are no longer auto-verified. After registering the account is signed out until approved.
- **Admin** (`AdminApprovalActivity`): checks `role == "admin"` from Firestore, lists `role=="hospital" && approvalStatus=="pending"`, **Approve** → `isVerified:true, approvalStatus:"approved"`, **Reject** (new) → `isVerified:false, approvalStatus:"rejected"`, both with `reviewedBy/reviewedAt`.
- **Hospital login** (`MainActivity`) and dashboard entry require `role=="hospital" && isVerified==true`; rejected hospitals get a specific message.
- **Hospital cannot change `role`, `isVerified` or `approvalStatus`** — enforced by rules (the app also never writes them from the hospital side). It can update beds, ambulances counts and latitude/longitude (new fields on the dashboard).
- Old documents with string numbers are read through `FirestoreValues` (number-or-string tolerant); all new writes are numeric.

## 5. Algorithms — integration status

| Algorithm | Where it is called in the live flow | Output used for |
|---|---|---|
| **HaversineDistance** | `HospitalSelection` (user→hospital), `GeoRadius`, `AmbulanceSelection` (ambulance→user), hospital reject re-routing, route fallback | selected hospital, `distanceToHospitalMeters`, ambulance ranking, straight-line fallback text |
| **GeoRadius** | `HospitalSelection.selectNearest` → new `findWithinExpandingRadius` | searches 5 → 10 → 15 → 20 km (existing presets), then nearest overall so remote patients are not rejected; `searchRadiusMeters` stored |
| **Hospital selection** (new `utils/HospitalSelection`) | SOS (`DashboardActivity`) and hospital Reject (`HospitalActivity`) | verified + beds > 0 + valid location → GeoRadius tiers → nearest (ties: more beds); its **UID** becomes `hospitalId` |
| **EmergencyPriority** | hospital dashboard sorts its queue with `EmergencyPriority::compare`; level shown on each card; `priority` field written at creation/accept/dispatch | ordering of hospital requests |
| **AmbulanceSelection** | `HospitalActivity.dispatchAmbulance` via new `rankForEmergency` | nearest **online, idle, same-hospital** ambulance with valid GPS, excluding drivers who already rejected this emergency |

EmergencyPriority was kept as the single priority system; it was status + age based and referenced statuses that were never written (`"active"`), so it was updated to `EmergencyStatus` constants: awaiting hospital = CRITICAL, awaiting ambulance = HIGH, being served = MEDIUM. Emergency priority does not affect *which hospital* is chosen (no severity input exists in the app); it drives request ordering.

Fixes inside existing algorithm classes: `isValidCoordinate` now rejects NaN and the unset `(0,0)` placeholder; GeoRadius no longer crashes on string coordinates and sorts invalid ones last; AmbulanceSelection skips offline drivers.

## 6. Emergency lifecycle

`EmergencyStatus.java` (new) is the single source of status strings; `firestore.rules` uses the same literals.

```
HOSPITAL_REQUESTED ──accept──▶ HOSPITAL_ACCEPTED ──dispatch──▶ AMBULANCE_DISPATCHED ──driver accept──▶ DRIVER_ACCEPTED
   │  reject: next hospital → HOSPITAL_REQUESTED (new hospitalId)            │ driver reject
   │  reject: none left     → HOSPITAL_REJECTED (terminal)                   ▼
   │                                                            DRIVER_REJECTED ──dispatch──▶ AMBULANCE_DISPATCHED
DRIVER_ACCEPTED ─▶ AMBULANCE_ARRIVED ─▶ PATIENT_PICKED_UP ─▶ EN_ROUTE_TO_HOSPITAL ─▶ COMPLETED
```

**SOS creation** (`DashboardActivity`): auth check → location permission & provider → fresh high-accuracy fix → coordinate validation → server query for an existing active emergency (blocks duplicates; the button becomes "TRACK HELP") → `HospitalSelection` → **one** `emergencies/{emergencyId}` via `document().set()` with `emergencyId, userId, hospitalId, status:HOSPITAL_REQUESTED, timestamp, latitude, longitude` plus denormalised patient info (name, phone, blood group, emergency contact), hospital name/coordinates, distance, search radius, `rejectedHospitalIds[]`, `rejectedAmbulanceIds[]`, `ambulanceId:null`, `priority`. The SOS button has loading ("SENDING..."), duplicate-hold guard, success ("SENT") and error ("RETRY SOS") states. No emergency is created unless a hospital was selected.

Patient data is copied into the emergency so hospitals and drivers never need to read the patient's `users` doc.

**Hospital dashboard**: query `hospitalId == myUid && status in HOSPITAL_VISIBLE`, one listener (attached in `onStart` after verifying the account, removed in `onStop`). Card shows emergency ID, patient name/phone, blood group, location, priority level, age, distance, status, and Accept / Reject / Dispatch as appropriate.

**Accept** — transaction on the SAME doc: requires `HOSPITAL_REQUESTED` and our `hospitalId`, requires `availableBeds > 0`, sets `HOSPITAL_ACCEPTED`, decrements `availableBeds`. Then dispatch runs automatically.

**Reject** — `HospitalSelection` excluding every hospital in `rejectedHospitalIds` + self. If found: same doc gets new `hospitalId`, hospital name/coords, distance, status stays `HOSPITAL_REQUESTED` (visible to the new hospital immediately). Otherwise: `HOSPITAL_REJECTED` and the user is told to call 108/112. No new document is ever created.

**Dispatch** — idle ambulances of this hospital → `AmbulanceSelection.rankForEmergency` → transaction re-checks the emergency (needs dispatch, no ambulance yet) and the ambulance (`isOnTrip != true`), then sets `ambulanceId`, vehicle/driver info, `AMBULANCE_DISPATCHED`, and ambulance `isOnTrip:true, currentEmergencyId`. A candidate grabbed concurrently is skipped and the next nearest tried. This prevents double dispatch. If no driver is online the hospital sees a message and a **Dispatch ambulance** button (also shown after a driver rejects).

**Driver** (`AmbulanceActivity`): listens to `ambulanceId == mine && status in DRIVER_VISIBLE`. On `AMBULANCE_DISPATCHED` shows ACCEPT/REJECT.
- Accept: transaction → `DRIVER_ACCEPTED` and links `driverLocations/{ambId}.emergencyId` (this is what lets the patient read the live position).
- Reject: transaction → `DRIVER_REJECTED`, `ambulanceId:null`, driver added to `rejectedAmbulanceIds`, ambulance released (`isOnTrip:false`).
- One primary button then walks ARRIVED → PATIENT PICKED UP → START TO HOSPITAL → COMPLETE, each a guarded transaction writing `arrivedAt / pickedUpAt / enRouteAt / completedAt`. **COMPLETED** releases the ambulance (`isOnTrip:false, currentEmergencyId:null`) and unlinks the live location. The emergency document is kept as history. The `"active"` status check that made completion unreachable is gone.

## 7. Live location and maps

- **Driver location**: produced only by the new `DriverLocationService`, a foreground service of type `location` running from login / ON DUTY until logout / OFF DUTY, so tracking continues while the driver uses an external navigation app. Every 5 s / 5 m, smoothed with the existing `GPSSmoothing`, one batch writes `driverLocations/{ambulanceId}` `{ambulanceId, latitude, longitude, accuracy, timestamp}` and `ambulances/{ambulanceId}` `{latitude, longitude, lastUpdatedAt, isOnline}`. The old in-Activity tracker (which only started after the map style loaded and never requested permission) is removed. Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, service declaration; runtime permission requests added.
- **User map** (`UserMapActivity`): opens a specific `emergencyId`, listens to that one document and tracks **only `emergencies.ambulanceId`** (not the hospital's first ambulance). Driver details come from `ambulances/{ambulanceId}`, live position from `driverLocations/{ambulanceId}`; listeners are swapped if the ambulance changes and all are removed in `onStop`.
- **Ambulance map**: the dashboard map shows its own position (from `driverLocations`), the patient and the hospital.
- Both maps use a shared `LiveMapController` + `MapOverlay`: red = patient, blue = ambulance, green = hospital, blue route line. Route origin is the ambulance; destination is the patient until pickup, then the hospital. Distance + ETA text ("3.4 km | ETA 7 min (2:45 PM)"). Directions are throttled (one request in flight, refresh ≤ every 15 s or when the destination changes). If routing fails a Haversine straight-line distance is shown.
- **MapboxRouting fixes**: callbacks now always run on the main thread (UI updates used to happen on a background thread); `RouteInfo.toString()` had 3 format specifiers but 2 arguments — it threw inside the success path, so **every route previously reported failure**; the API now requests `geometries=polyline6` to match the decoder; Locale-safe URL; token never logged.
- **Mapbox token**: still not in source control — `mapbox_access_token.xml` stays git-ignored and `MAPBOX_ACCESS_TOKEN` comes from `local.properties` via BuildConfig. No change to that setup.

## 8. Firestore rules and indexes

`firestore.rules` (new, repo root) — summary:

| Collection | Read | Write |
|---|---|---|
| users | own doc; any account may read **verified hospital** docs (hospital selection); admin reads all | create only as `user`, or as `hospital` with `isVerified:false, approvalStatus:"pending"`; owners can't touch role/isVerified/approvalStatus/uid; admin may only change isVerified/approvalStatus/reviewed* on hospitals |
| emergencies | owning user; assigned verified hospital; assigned driver | create by user with `HOSPITAL_REQUESTED`, own userId, verified hospitalId, no ambulance; hospital transitions (accept / forward to another verified hospital / terminal reject / dispatch of its own ambulance); driver transitions with a field whitelist; immutable userId/location/timestamp; no deletes |
| ambulances | owner hospital; the session's driver; the patient whose emergency is `currentEmergencyId` | owner hospital CRUD (no `securityNumber` field allowed); driver may only update position/online and *release* itself |
| ambulanceLogins | GET by exact key only; list denied | create by owning hospital (checked with `getAfter`); no update |
| driverSessions | own | anonymous create validated against the login key; no update |
| driverLocations | own driver; owning hospital; the patient while linked to their emergency | own driver only, field whitelist |
| emergencyContacts, notifications, emergencyHistory, anything else | denied | denied (unused by the app — contacts live in `users`, history is `emergencies`) |

There is no `allow read, write: if true`.

`firestore.indexes.json` — composite indexes for the three `==` + `in` queries the app runs:
`emergencies (userId, status)`, `(hospitalId, status)`, `(ambulanceId, status)`. Pure equality queries (`users` role+isVerified, role+approvalStatus; `ambulances` hospitalId+isOnTrip) are served by Firestore's automatic single-field indexes. If any query still reports `FAILED_PRECONDITION`, the Logcat line contains a direct console link to create the missing index.

Deploy (targets `suraksha-d0ac8` via `.firebaserc`):
```
firebase login
firebase use suraksha-d0ac8
firebase deploy --only firestore:rules,firestore:indexes
```

## 9. Verification performed in this environment

Static: Java parse of all files ✔ · resource references ✔ · internal symbol references ✔ · XML well-formed ✔ · no legacy statuses ✔ · no secret logging ✔.

Executed logic tests (real source files, JDK compiler):

```
PASS haversine nagpur short distance (812 m)          PASS 5km tier used when a hospital is inside it
PASS haversine nagpur-mumbai ~685km (688 km)          PASS nearest first
PASS reject (0,0) / null / NaN / lat>90               PASS legacy string coordinates are usable (no crash)
PASS accept nagpur, format km                         PASS hospital without location excluded
PASS driver status chain accepted→…→completed         PASS 15km hospital found in the 15km tier
PASS hospital-visible has initial status              PASS ~18km hospital found in the 20km tier (17677 m)
PASS whereIn lists ≤ 30, terminal not active          PASS beyond 20km falls back to nearest overall
PASS needsDispatch after driver reject                PASS legacy string beds parsed
PASS heading to hospital after pickup                 PASS nearest ONLINE ambulance of the SAME hospital
PASS login key normalizes name, depends on secret     PASS offline / other-hospital / no-GPS excluded
                                                      PASS rejecting driver excluded on re-dispatch
                                                      PASS priority order CRITICAL > HIGH > MEDIUM
```
30/30 passed. (One test initially failed because *my test* assumed a 15.0 km hospital would land in the 20 km tier; it is 14.99 km and the code correctly used the 15 km tier. The test was corrected, not the code.) A JUnit version of the pure-JVM checks was added as `app/src/test/.../EmergencyFlowLogicTest.java` so `gradlew test` runs them on your machine.

## 10. Runtime test plan (not executed — run these on a device)

Filter Logcat with `SURAKSHA_` and watch for `PERMISSION_DENIED`, `FAILED_PRECONDITION`, `NOT_FOUND`, `UNAVAILABLE`, `FATAL EXCEPTION`, `NullPointerException`, `SecurityException`.

| # | Test | Expected evidence |
|---|---|---|
| 1 | User registration | `SURAKSHA_AUTH user profile created uid=…`; re-register same email → exact "Email already registered…" toast |
| 2 | User login | Dashboard opens; hospital credentials here are refused |
| 3 | User profile | name/email/phone/blood group shown |
| 4 | Hospital registration | `hospital registered (pending approval)`; doc has isVerified=false, numeric lat/lng/beds |
| 5 | Admin approval | `hospital approved hospitalId=…` |
| 6 | Hospital login | only after approval |
| 7 | User SOS | button SENDING… → SENT; second hold → TRACK HELP, no second doc |
| 8 | GPS + hospital selection | `SURAKSHA_SOS GPS fix`, `hospital candidates …`, `hospital selected hospitalId=… radius=5.0 km` |
| 9 | Emergency creation | `emergency created emergencyId=… status=HOSPITAL_REQUESTED` |
| 10 | Hospital dashboard | request appears without refresh, priority CRITICAL |
| 11 | Hospital accept | `emergency accepted`, availableBeds −1 |
| 12 | Ambulance selection | `ambulance candidates … eligible=N`, `ambulance dispatched ambulanceId=…` (driver must be logged in first) |
| 13 | Ambulance dashboard | request with ACCEPT/REJECT |
| 14 | Ambulance accept | `driver accepted … status=DRIVER_ACCEPTED` |
| 15 | Ambulance map | blue/red/green markers, route, "To patient: x km \| ETA …" |
| 16 | User live tracking | ambulance marker moves; `SURAKSHA_MAP route calculated` |
| 17 | Ambulance live tracking | `SURAKSHA_DRIVER location tracking started`; notification visible |
| 18 | Arrived | status AMBULANCE_ARRIVED |
| 19 | Patient pickup | status PATIENT_PICKED_UP, route switches to hospital |
| 20 | Route to hospital | EN_ROUTE_TO_HOSPITAL, "To hospital: …" |
| 21 | Completed | COMPLETED, ambulance isOnTrip=false, user map shows "Completed" |

Also test: hospital Reject with a second verified hospital (doc moves, `reassignedTo=…`) and with none (`HOSPITAL_REJECTED`); driver Reject then hospital "Dispatch ambulance".

## 11. Manual steps required before testing

1. Firebase Console (suraksha-d0ac8) → Authentication → Sign-in method → **enable Anonymous** (driver login fails with an explicit message otherwise).
2. Deploy rules + indexes (commands in §8).
3. Create an admin: register a normal user, then in the console set `users/{uid}.role = "admin"`.
4. `local.properties` must contain `MAPBOX_ACCESS_TOKEN=pk…` and `app/src/main/res/values/mapbox_access_token.xml` must exist locally (both unchanged, git-ignored).
5. **Legacy data**: old emergencies with lowercase statuses are ignored — delete them. Old ambulance docs containing `securityNumber` cannot log in or be dispatched — delete and re-add them from the hospital dashboard (the fleet list marks them "legacy"). Old hospitals without latitude/longitude are not selectable until they set their location on the dashboard.
6. `gradle.properties` hardcodes a Windows JDK path (`org.gradle.java.home`); remove it on other machines.
7. Run `gradlew clean assembleDebug test`, then the 21 tests above with at least two devices (user + driver; hospital can be a third or the same device after logout).

## 12. Remaining issues and risks

1. **Build not verified.** Highest-risk compile points: the Mapbox v11 core style calls in `MapOverlay` (`Value.fromJson`, `addStyleSource`, `addStyleLayer(value, null)`, `setStyleSourceProperty`, `styleSourceExists/styleLayerExists`) and the foreground-service APIs. If one fails to compile, the fix is local to that file.
2. Driver credential brute-force limitation (§2). Renaming a hospital invalidates its drivers' login keys (re-add ambulances).
3. Hospital selection runs on the client, so any signed-in account can read verified hospitals' profile docs (name, phone, email, license, beds, location). Acceptable for public hospitals; otherwise move selection to a Cloud Function or publish a trimmed `hospitals` collection.
4. Duplicate-SOS protection is a fresh server query immediately before creation plus UI locking. Two devices of the same user pressing SOS in the same second could still both pass; a hard guarantee needs a server-side check.
5. After a driver rejects, re-dispatch is a hospital tap ("Dispatch ambulance"), not automatic. Dispatch requires at least one driver logged in (online with GPS).
6. Beds are decremented on hospital accept and never automatically restored (no discharge flow); the hospital adjusts them manually. There is no user-side "cancel SOS".
7. `HOSPITAL_REJECTED` is terminal; the user is told to call 108/112. No automatic retry later.
8. If Android kills and restarts the location service while the app is in the background, Android 12+ may refuse to restart a foreground service from the background; the driver reopening the app restarts it.
9. Directions API is called at most every 15 s per open map — watch Mapbox quota.
10. Rules were written carefully but not run in the emulator; test them with the Firestore emulator or the Rules Playground before relying on them.

## 13. Final table

| Item | Result |
|---|---|
| USER REGISTER | NOT TESTED (implemented) |
| USER LOGIN | NOT TESTED (implemented) |
| USER PROFILE | NOT TESTED (implemented) |
| SOS BUTTON | NOT TESTED (implemented) |
| GPS | NOT TESTED (implemented) |
| HAVERSINE | LOGIC PASS · integrated in flow (code-verified) · device NOT TESTED |
| GEO RADIUS | LOGIC PASS · integrated in flow (code-verified) · device NOT TESTED |
| HOSPITAL SELECTION | LOGIC PASS (filter/tiers/nearest) · Firestore query NOT TESTED |
| EMERGENCY PRIORITY | LOGIC PASS · integrated in flow (code-verified) · device NOT TESTED |
| FIRESTORE SOS | NOT TESTED (implemented) |
| HOSPITAL DASHBOARD | NOT TESTED (implemented) |
| HOSPITAL ACCEPT | NOT TESTED (implemented) |
| HOSPITAL REJECT | NOT TESTED (implemented) |
| AMBULANCE SELECTION | LOGIC PASS · integrated in dispatch (code-verified) · device NOT TESTED |
| AMBULANCE DASHBOARD | NOT TESTED (implemented) |
| AMBULANCE ACCEPT | NOT TESTED (implemented) |
| AMBULANCE REJECT | NOT TESTED (implemented) |
| DRIVER LOCATION | NOT TESTED (implemented) |
| USER LIVE MAP | NOT TESTED (implemented) |
| AMBULANCE LIVE MAP | NOT TESTED (implemented) |
| ROUTE | NOT TESTED (implemented; routing bugs fixed) |
| ETA | NOT TESTED (implemented) |
| ARRIVED | NOT TESTED (implemented) |
| PATIENT PICKUP | NOT TESTED (implemented) |
| HOSPITAL ROUTE | NOT TESTED (implemented) |
| COMPLETED | NOT TESTED (implemented) |
| FIRESTORE RULES | NOT TESTED (written, not deployed/emulated) |
| FIRESTORE INDEXES | NOT TESTED (written, not deployed) |
| BUILD | NOT VERIFIED (no Android SDK / Maven access here) |
| END-TO-END | NOT TESTED |

**The system is not claimed to be fixed.** The full chain USER REGISTER → … → COMPLETED is implemented end to end, but it has not been built or run. It becomes verified only after §11 and the §10 runs pass on a device.

## 14. Files changed

New: `EmergencyStatus.java`, `DriverLocationService.java`, `utils/SurakshaLog.java`, `utils/FirestoreValues.java`, `utils/DriverCredentials.java`, `utils/HospitalSelection.java`, `utils/MapOverlay.java`, `utils/LiveMapController.java`, `app/src/test/.../EmergencyFlowLogicTest.java`, `firestore.rules`, `firestore.indexes.json`, `firebase.json`, `.firebaserc`.

Rewritten: `DashboardActivity`, `UserMapActivity`, `HospitalActivity`, `AmbulanceActivity`, `AmbulanceLoginActivity`, `RegistrationActivity`, `HospitalRegisterActivity`, `AdminApprovalActivity`, `utils/MapboxRouting`, `utils/EmergencyPriority`.

Edited: `StartingActivity`, `UserLoginActivity`, `MainActivity`, `utils/HaversineDistance`, `utils/GeoRadius`, `utils/AmbulanceSelection`, `AndroidManifest.xml`, layouts `user_dashboard`, `hospital_registration`, `hospital`, `item_emergency_request`, `item_pending_hospital`, `ambulence_dashboard`.

Unchanged: Gradle files, `google-services.json`, Mapbox token configuration, all other activities/layouts.
