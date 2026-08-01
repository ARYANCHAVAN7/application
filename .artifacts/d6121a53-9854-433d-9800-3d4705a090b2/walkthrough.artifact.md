# Walkthrough - Registration Success Message

I have added a success message to the hospital registration process.

## Changes Made

### Activity Implementation

#### [MODIFY] [HospitalRegisterActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/HospitalRegisterActivity.java)
- Added a click listener to the `btnRegister` button.
- When clicked, a `Toast` message "Hospital Registered Successfully!" is displayed.
- The activity then calls `finish()` to return the user to the login screen (`MainActivity`).

## Verification Results

### Manual Verification
- Verified that the code compiles and the button listener is correctly implemented.
- The app returns to the login screen after the registration button is pressed, providing immediate feedback to the user.
