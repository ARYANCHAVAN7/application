# Walkthrough - UI Consistency: Shake Animation for Hospital Login

I have added the "shake" animation effect to the Hospital Login screen to match the User Login's feedback behavior.

## Changes Made

### Activity Implementation

#### [MODIFY] [MainActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/MainActivity.java)
- Added logic to trigger the `R.anim.shake` animation on the `loginCard` when a user attempts to log in with empty credentials.
- This provides visual feedback to the user that input is required.

## Verification Results

### Build Success
- Ran `:app:assembleDebug` and the project compiled successfully.

### UI Feedback
- The "shake" animation now consistently triggers on both User and Hospital login screens when fields are left blank.
