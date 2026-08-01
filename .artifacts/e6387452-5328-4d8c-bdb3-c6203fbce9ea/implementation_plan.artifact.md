# Implementation Plan - Advanced Ambulance Dashboard Features

Enhance the Ambulance Dashboard with critical medical information for paramedics and advanced mapping features for faster emergency response.

## Proposed Changes

### UI Design [Component: Layout]

#### [MODIFY] [ambulence_dashboard.xml](file:///C:/Users/APURVA%20RAKSHAK/OneDrive/ドキュメント/GitHub/application/app/src/main/res/layout/ambulence_dashboard.xml)
- **Medical Info Panel**: Add a collapsible section within the `taskCard` to display:
    - Blood Group (e.g., "O+ Positive").
    - Allergies (e.g., "Penicillin").
    - Emergency Contact name and phone.
- **Traffic Toggle**: Add a small floating button on the map to enable/disable the "Traffic Layer".

### Resources [Component: Resources]

#### [MODIFY] [strings.xml](file:///C:/Users/APURVA%20RAKSHAK/OneDrive/ドキュメント/GitHub/application/app/src/main/res/values/strings.xml)
- Add labels: `label_medical_vitals`, `label_blood_group_short`, `label_allergies_short`, `label_view_more`.

### Logic [Component: Java]

#### [MODIFY] [AmbulanceActivity.java](file:///C:/Users/APURVA%20RAKSHAK/OneDrive/ドキュメント/GitHub/application/app/src/main/java/com/example/application/AmbulanceActivity.java)
- **Map Enhancements**: Enable the `setTrafficEnabled(true)` feature on the Google Map.
- **Collapsible UI**: Implement logic to expand/collapse the Medical Info panel when a "View Vitals" button is clicked.

## Verification Plan

### Manual Verification
- Verify the "View Vitals" button expands to show placeholder patient data.
- Confirm the map shows green/yellow/red traffic lines when traffic is enabled.
- Test the layout consistency on different screen sizes in the preview.
