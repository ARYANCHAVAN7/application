# Implementation Plan - Google Login & Shake Effect Integration

I will integrate the "Continue with Google" functionality into both User and Hospital login screens, complete with the requested "shake" feedback and a simulated account selection for the Hospital portal.

## Proposed Changes

### [Component Name] - Layouts

#### [MODIFY] [user_login.xml](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/res/layout/user_login.xml)
- Add a "Continue with Google" button below the "Sign In" button, matching the design of the Hospital login screen.
- Add an "OR" divider text between the login methods.

### [Component Name] - Activity Implementation

#### [MODIFY] [MainActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/MainActivity.java)
- Implement a click listener for the Google Login button (`button3`).
- When clicked, show a simulated "Select Account" dialog (e.g., a `Toast` followed by a dummy selection or a `AlertDialog` with mock accounts).
- If the user tries to proceed without "selecting," trigger the `R.anim.shake` on the `loginCard`.

#### [MODIFY] [UserLoginActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/UserLoginActivity.java)
- Implement a click listener for the new Google Login button.
- Apply the same "shake" effect to the `loginCard` for consistency.

## Verification Plan

### Manual Verification
- Deploy to the device.
- **User Login**: Verify the new Google button exists and triggers a shake effect.
- **Hospital Login**: Verify the Google button triggers "options" (simulated account selection) and the shake effect works as expected.
