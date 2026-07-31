# Implementation Plan - Connect Login to Hospital Dashboard

This plan outlines the steps to connect the login page to the hospital dashboard, allowing users to transition between them upon "login" and "logout".

## Proposed Changes

### [NEW] HospitalActivity.java
- Create a new activity to handle the hospital dashboard logic.
- Set `hospital.xml` as its content view.
- Implement the "Logout" button functionality to return to the login screen.

### [MODIFY] MainActivity.java
- Revert the content view to `activity_main.xml`.
- Add a click listener to the Login button (`button2`).
- Launch `HospitalActivity` when the Login button is clicked.

### [MODIFY] AndroidManifest.xml
- Register `HospitalActivity` so it can be launched by the system.

## Verification Plan

### Manual Verification
1. Launch the app.
2. Observe the login screen (`activity_main.xml`).
3. Click the **LOGIN** button.
4. Verify that the Hospital Dashboard (`hospital.xml`) opens.
5. Click the **Logout** button on the dashboard.
6. Verify that the app returns to the login screen.
