# Implementation Plan - Add Image to Main Activity

The user wants to add an image (likely `img.png` found in `res/drawable`) to the main layout (`activity_main.xml`).

## Proposed Changes

### [Component Name] UI Layout

#### [MODIFY] [activity_main.xml](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/res/layout/activity_main.xml)
- Add an `ImageView` to display `@drawable/img`.
- Position it below the "SURAKSHA APP" title.
- Adjust the positioning of subsequent elements (Email, Password, Login button) to accommodate the image and maintain a clean layout.
- Improve constraints to move away from absolute positioning where possible.

## Verification Plan

### Manual Verification
- Deploy the app to the device/emulator.
- Verify the image is visible and correctly positioned on the screen.
- Ensure other UI elements are still accessible and properly aligned.
