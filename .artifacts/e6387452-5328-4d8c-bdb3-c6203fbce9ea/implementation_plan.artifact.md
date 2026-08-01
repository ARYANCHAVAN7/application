# Implementation Plan - "Nice" Light Theme for Sign-In Page

Transition the Sign-In page from a dark theme to a clean, professional light theme ("Glassmorphism" style) that better complements the `hospitaldash` background and improves visibility.

## Proposed Changes

### Colors & Resources [Component: Resources]

#### [MODIFY] [colors.xml](file:///C:/Users/APURVA%20RAKSHAK/OneDrive/ドキュメント/GitHub/application/app/src/main/res/values/colors.xml)
- Define a semi-transparent white for the card background (`nice_card_bg` -> `#CCFFFFFF`).
- Update role selection colors to be lighter and more professional.

#### [MODIFY] [bg_role_unselected.xml](file:///C:/Users/APURVA%20RAKSHAK/OneDrive/ドキュメント/GitHub/application/app/src/main/res/drawable/bg_role_unselected.xml)
- Change background to a very light gray/blue to match the light theme.

### UI Design [Component: Layout]

#### [MODIFY] [starting_page.xml](file:///C:/Users/APURVA%20RAKSHAK/OneDrive/ドキュメント/GitHub/application/app/src/main/res/layout/starting_page.xml)
- Change `app:cardBackgroundColor` from `dark_card` to the new light theme color.
- Update all `TextView` colors from white/tertiary to `text_primary` and `text_secondary` (darker colors).
- Update `EditText` and `ImageView` tinting to match the light theme.

### Logic [Component: Java]

#### [MODIFY] [StartingActivity.java](file:///C:/Users/APURVA%20RAKSHAK/OneDrive/ドキュメント/GitHub/application/app/src/main/java/com/example/application/StartingActivity.java)
- Adjust the `updateRoleView` logic to use correct text colors for the light theme when a role is selected/unselected.

## Visibility Fix

### [IMPORTANT] Build Issues
To fix the "I cannot see anything" issue (likely caused by Windows file locks preventing the build), I will perform a forced clean and rebuild during the verification phase.

## Verification Plan

### Automated Tests
- Run `clean` and `assembleDebug` tasks.

### Manual Verification
- Verify the new light theme UI is visible and readable.
- Confirm animations still work smoothly with the new colors.
