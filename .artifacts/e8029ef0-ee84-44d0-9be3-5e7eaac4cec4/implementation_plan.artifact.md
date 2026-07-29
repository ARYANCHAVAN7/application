# Implementation Plan - Fix Layout XML Errors

Several layout XML files have malformed `android:text` attributes that mistakenly contain `android:id` declarations. This causes parsing errors and prevents the UI from rendering correctly.

## Proposed Changes

### [Layout Resources]

#### [MODIFY] [user_dashboard.xml](file:///D:/AS_project/app/src/main/res/layout/user_dashboard.xml)
- Correct multiple `TextView` elements where `android:id` is nested inside `android:text`.
- Replace malformed `android:text` with proper `android:id` and placeholder `android:text`.

#### [MODIFY] [row_account_detail_dashboard.xml](file:///D:/AS_project/app/src/main/res/layout/row_account_detail_dashboard.xml)
- Correct multiple `TextView` elements where `android:id` is nested inside `android:text`.
- Replace malformed `android:text` with proper `android:id` and placeholder `android:text`.

## Verification Plan

### Automated Tests
- Run `analyze_file` on the modified files to ensure no more syntax errors.
- Run a build to ensure XML resources compile successfully.

### Manual Verification
- View the layouts in the Android Studio Layout Editor (if possible, though I can't do this directly, I can use `render_compose_preview` if they were Composables, but these are XML. I'll rely on `analyze_file`).
