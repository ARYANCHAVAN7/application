# Walkthrough - Login and Dashboard Connection

I have connected the login page to the hospital dashboard. You can now log in using the login screen and logout from the dashboard.

## Changes Made

### 1. Created [HospitalActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/HospitalActivity.java)
This new activity manages the Hospital Dashboard. It:
- Inflates the `hospital.xml` layout.
- Sets up the **Logout** button (`btnLogout`) to close the activity and return to the login screen.

### 2. Updated [MainActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/MainActivity.java)
I restored the login functionality in `MainActivity`:
- It now shows the `activity_main.xml` layout by default.
- Added a click listener to the **LOGIN** button (`button2`).
- Included a basic check to ensure the email and password fields are not empty before proceeding.
- Launches `HospitalActivity` upon a successful "login".

### 3. Updated [AndroidManifest.xml](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/res/layout/hospital.xml)
- Registered `HospitalActivity` so the application can navigate to it.

## How to Test
1. **Run the app** on your device or emulator.
2. You will see the Login screen.
3. Enter any email and password.
4. Click **LOGIN**. The Hospital Dashboard will open.
5. Click **Logout** at the top right of the dashboard to return to the Login screen.

> [!NOTE]
> For this demonstration, any non-empty email and password will allow you to log in. In a real application, you would verify these credentials against a database or authentication service.
