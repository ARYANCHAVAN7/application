# Implementation Plan - Navigation between Login and Registration

This plan connects the Login screen (`MainActivity`) and the Hospital Registration screen (`HospitalRegisterActivity`) using Android Intents.

## User Review Required

> [!IMPORTANT]
> - A new activity class `HospitalRegisterActivity.java` will be created.
> - The `AndroidManifest.xml` will be updated to register this new activity.
> - The "Sign Up" button in `MainActivity` will navigate to the registration screen.
> - The "Already registered? Login" text in the registration screen will return the user to the login screen.

## Proposed Changes

### [NEW] [HospitalRegisterActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/HospitalRegisterActivity.java)
- Implement `onCreate` to set the content view to `R.layout.hospital_registration`.
- Add a click listener to `tvGoToLogin` that calls `finish()` to return to the Login screen.

### [MODIFY] [MainActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/MainActivity.java)
- Find the "Sign Up" button (`R.id.button`).
- Add a click listener to start `HospitalRegisterActivity`.

### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/AndroidManifest.xml)
- Add the `<activity>` tag for `.HospitalRegisterActivity`.

## Verification Plan

### Manual Verification
1. Launch the app to the Login screen.
2. Click the **Sign Up** button at the bottom.
3. Verify that the **Hospital Registration** screen opens.
4. Click the **Already registered? Login** text at the bottom.
5. Verify that the app returns to the **Login** screen.
