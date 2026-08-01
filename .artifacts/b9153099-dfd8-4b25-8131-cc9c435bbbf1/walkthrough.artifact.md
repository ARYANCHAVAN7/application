# Walkthrough - Login and Registration Navigation

I have connected the Login screen with the Hospital Registration screen, allowing users to move back and forth between them.

## Changes Made

### 1. Created [HospitalRegisterActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/HospitalRegisterActivity.java)
- This is a new activity that manages the **Hospital Registration** screen.
- It uses the `hospital_registration.xml` layout.
- It includes a listener for `tvGoToLogin` to return to the Login screen.

### 2. Updated [MainActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/MainActivity.java)
- Added a click listener to the **Sign Up** button (`R.id.button`).
- When clicked, it launches the `HospitalRegisterActivity`.

### 3. Updated [AndroidManifest.xml](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/AndroidManifest.xml)
- Formally registered `HospitalRegisterActivity` so the system knows it exists and can launch it.

## How to Test
1. **Launch the app** to the Login screen.
2. Click the **Don't have an account? Sign Up** button at the bottom.
3. Observe that you are taken to the **Hospital Registration** page.
4. Click the **Already registered? Login** text at the bottom.
5. Observe that you are taken back to the **Login** page.

> [!TIP]
> Navigating back using `finish()` (on the Login text) is more efficient than starting a new `MainActivity` instance, as it simply closes the current screen and reveals the previous one.
