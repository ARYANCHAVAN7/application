# Firebase Integration Plan

This plan outlines the steps to integrate Firebase Authentication and Firestore into the application for user registration and login.

## User Review Required

> [!IMPORTANT]
> - Ensure that the `google-services.json` file provided is correctly configured for your Firebase project.
> - Firebase project must have **Email/Password** authentication enabled in the Firebase Console.
> - Firestore database must be initialized in the Firebase Console.

## Proposed Changes

### Configuration
#### [MODIFY] [gradle.properties](file:///C:/Users/Dell/AndroidStudioProjects/application/gradle.properties)
- Suppress the experimental path check warning (Already completed).

### Authentication & Data Persistence
#### [MODIFY] [RegistrationActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/RegistrationActivity.java)
- Implement `FirebaseAuth` to create a new user with email and password.
- Save additional user details (phone, blood group, emergency contact) to `FirebaseFirestore`.

#### [MODIFY] [UserLoginActivity.java](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/java/com/example/application/UserLoginActivity.java)
- Implement `FirebaseAuth` to sign in existing users.
- Add Google Sign-In support (optional/placeholder as requested by user in code comments).

## Verification Plan

### Manual Verification
1.  **Registration**: Register a new user and verify the account appears in the Firebase Auth console and user data appears in Firestore.
2.  **Login**: Log in with the newly created credentials and verify navigation to the `DashboardActivity`.
3.  **Error Handling**: Verify that invalid credentials or existing email errors are handled gracefully with Toasts or error messages.

### Automated Tests
- Run existing unit tests to ensure no regressions (if any).
