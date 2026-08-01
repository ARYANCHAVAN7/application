# Implementation Plan - Re-balancing Starting Page Layout

I will remove the shield logo from the "Welcome to Suraksha" screen and adjust the layout to eliminate the "empty" feeling by centering the main content.

## User Review Required

> [!NOTE]
> I will be removing the 🛡️ emoji and centering the branding and role selection card to create a more compact and balanced interface.

## Proposed Changes

### [Component Name] - Layouts

#### [MODIFY] [starting_page.xml](file:///C:/Users/Dell/AndroidStudioProjects/application/app/src/main/res/layout/starting_page.xml)
- **Logo Update**: Remove the `🛡️` emoji, leaving only the `🚑` (or a more centered brand icon).
- **Layout Re-balancing**:
    - Remove the fixed top margin (`80dp`) from `headerSection`.
    - Constrain `headerSection` to the top of `loginCard`.
    - Constrain `loginCard` to be centered vertically in the screen (instead of pinned to the bottom).
    - This will group the branding and selection card together in the center of the screen, making the page feel full and intentional.

## Verification Plan

### Manual Verification
- Deploy to the device.
- Verify that the branding and card are now centered and the page no longer feels "empty".
- Ensure the staggered animations still function correctly in the new layout.
- Take a screenshot to confirm the improved balance.
