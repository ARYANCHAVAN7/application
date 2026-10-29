package com.example.application;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.application.utils.EmergencyPriority;
import com.example.application.utils.HaversineDistance;
import com.example.application.utils.HospitalSelection;
import com.example.application.utils.SurakshaLog;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * User dashboard + SOS.
 *
 * SOS pipeline: auth check -> location permission/provider -> fresh GPS fix -> validate ->
 * block if an active emergency already exists -> HospitalSelection (verified, beds > 0,
 * GeoRadius tiers, Haversine) -> create exactly one emergencies/{emergencyId} with
 * status HOSPITAL_REQUESTED and hospitalId = selected hospital UID -> open live map.
 */
public class DashboardActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final long HOLD_DURATION_MS = 2000;

    private enum SosState { IDLE, LOADING, SENT, ERROR, ACTIVE }

    private CountDownTimer holdTimer;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private FrameLayout sosButton;
    private TextView sosLabel;
    private android.widget.LinearLayout adminPanelBtn;

    private SosState sosState = SosState.IDLE;
    /** Guards the whole async SOS pipeline so one hold = at most one emergency. */
    private boolean sosInProgress = false;
    private String activeEmergencyId;
    private ListenerRegistration emergencyListener;
    private DocumentSnapshot profile;
    private String userEmergencyPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        sosButton = findViewById(R.id.sosButton);
        sosLabel = findViewById(R.id.sosLabel);
        TextView callNowBtn = findViewById(R.id.callNowBtn);
        adminPanelBtn = findViewById(R.id.adminPanelBtn);

        sosButton.setOnTouchListener((view, event) -> {
            if (sosInProgress) return true;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (activeEmergencyId != null) {
                        openMap(activeEmergencyId);
                        return true;
                    }
                    startHoldCountdown();
                    startScalingAnimation();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelHoldCountdown();
                    if (!sosInProgress) stopScalingAnimation();
                    view.performClick();
                    return true;
                default:
                    return false;
            }
        });

        callNowBtn.setOnClickListener(v -> {
            if (userEmergencyPhone == null || userEmergencyPhone.trim().isEmpty()) {
                Toast.makeText(this, "No emergency contact saved in your profile.", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + userEmergencyPhone)));
        });

        findViewById(R.id.logoutBtn).setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(DashboardActivity.this, StartingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        if (adminPanelBtn != null) {
            adminPanelBtn.setOnClickListener(v -> startActivity(new Intent(this, AdminApprovalActivity.class)));
        }

        View cardAiFirstAid = findViewById(R.id.cardAiFirstAid);
        if (cardAiFirstAid != null) {
            cardAiFirstAid.setOnClickListener(v -> startActivity(new Intent(this, FirstAidChatActivity.class)));
        }

        View fabAiChat = findViewById(R.id.fabAiChat);
        if (fabAiChat != null) {
            fabAiChat.setOnClickListener(v -> startActivity(new Intent(this, FirstAidChatActivity.class)));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() == null || mAuth.getCurrentUser().isAnonymous()) {
            redirectToLogin();
        } else {
            loadUserProfile();
            listenForEmergencyUpdates();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelHoldCountdown();
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
    }

    private void loadUserProfile() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        SurakshaLog.warn(SurakshaLog.AUTH, "dashboard without profile", "uid=" + uid);
                        mAuth.signOut();
                        redirectToLogin();
                        return;
                    }
                    String role = doc.getString("role");
                    if (role != null) role = role.trim();
                    if (!"user".equals(role) && !"admin".equals(role)) {
                        SurakshaLog.warn(SurakshaLog.AUTH, "non-user opened user dashboard", "uid=" + uid, "role=" + role);
                        Toast.makeText(this, "Unauthorized access", Toast.LENGTH_SHORT).show();
                        mAuth.signOut(); // prevents StartingActivity <-> Dashboard loop
                        redirectToLogin();
                        return;
                    }
                    profile = doc;
                    ((TextView) findViewById(R.id.userNameDisplay)).setText(displayName());
                    ((TextView) findViewById(R.id.userEmailDisplay)).setText(nonNull(doc.getString("email")));
                    ((TextView) findViewById(R.id.userPhoneDisplay)).setText(nonNull(doc.getString("phone")));
                    ((TextView) findViewById(R.id.bloodGroupDisplay)).setText(nonNull(doc.getString("bloodGroup")));
                    ((TextView) findViewById(R.id.emergencyContactName)).setText(nonNull(doc.getString("emergencyName")));
                    userEmergencyPhone = doc.getString("emergencyPhone");
                    ((TextView) findViewById(R.id.emergencyContactPhone)).setText(nonNull(userEmergencyPhone));

                    if ("admin".equals(role) && adminPanelBtn != null) {
                        adminPanelBtn.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.AUTH, "load user profile", e, "uid=" + uid);
                    Toast.makeText(this, SurakshaLog.userMessage("Could not load profile", e), Toast.LENGTH_LONG).show();
                });
    }

    private String displayName() {
        if (profile == null) return "User";
        String name = profile.getString("fullName");
        if (name == null || name.isEmpty()) name = profile.getString("name");
        return name == null || name.isEmpty() ? "User" : name;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    /** Keeps the dashboard status and history synchronized with this user's Firestore records. */
    private void listenForEmergencyUpdates() {
        String uid = mAuth.getCurrentUser().getUid();
        if (emergencyListener != null) emergencyListener.remove();
        emergencyListener = db.collection("emergencies")
                .whereEqualTo("userId", uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        SurakshaLog.error(SurakshaLog.SOS, "listen to user emergencies", error, "userId=" + uid);
                        return;
                    }
                    if (snapshot == null) return;
                    activeEmergencyId = null;
                    DocumentSnapshot activeEmergency = null;
                    DocumentSnapshot latestEmergency = null;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (latestEmergency == null || isNewer(doc, latestEmergency)) latestEmergency = doc;
                        if (EmergencyStatus.isActive(doc.getString("status"))
                                && (activeEmergency == null || isNewer(doc, activeEmergency))) {
                            activeEmergency = doc;
                        }
                    }
                    if (activeEmergency != null) activeEmergencyId = activeEmergency.getId();
                    updateEmergencyStatus(activeEmergency != null ? activeEmergency : latestEmergency);
                    updateEmergencyHistory(snapshot.getDocuments());
                    if (!sosInProgress) {
                        setSosState(activeEmergencyId != null ? SosState.ACTIVE : SosState.IDLE);
                    }
                });
    }

    private boolean isNewer(DocumentSnapshot candidate, DocumentSnapshot current) {
        Timestamp candidateTime = candidate.getTimestamp("timestamp");
        Timestamp currentTime = current.getTimestamp("timestamp");
        return candidateTime != null && (currentTime == null || candidateTime.compareTo(currentTime) > 0);
    }

    private void updateEmergencyStatus(DocumentSnapshot emergency) {
        TextView title = findViewById(R.id.emergencyStatusTitle);
        TextView description = findViewById(R.id.emergencyStatusDescription);
        if (emergency == null) {
            title.setText(R.string.no_active_emergency);
            description.setText(R.string.safe_message);
            return;
        }
        String status = emergency.getString("status");
        title.setText(EmergencyStatus.label(status));
        String hospitalName = emergency.getString("hospitalName");
        String detail = EmergencyStatus.isActive(status)
                ? "Your emergency request is active. Tap SOS to view live details."
                : "Latest emergency: " + EmergencyStatus.label(status) + ".";
        if (hospitalName != null && !hospitalName.trim().isEmpty()) detail += " Hospital: " + hospitalName + ".";
        description.setText(detail);
    }

    private void updateEmergencyHistory(java.util.List<DocumentSnapshot> emergencies) {
        LinearLayout rows = findViewById(R.id.emergencyHistoryRows);
        rows.removeAllViews();
        if (emergencies.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 24, 0, 24);
            empty.setText(R.string.no_history_message);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setTextSize(13);
            rows.addView(empty);
            return;
        }
        java.util.List<DocumentSnapshot> sortedEmergencies = new ArrayList<>(emergencies);
        sortedEmergencies.sort((a, b) -> {
            Timestamp at = emergencyTimestamp(a), bt = emergencyTimestamp(b);
            if (at == null) return bt == null ? 0 : 1;
            return bt == null ? -1 : bt.compareTo(at);
        });
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy'\n'h:mm a", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getDefault());
        for (DocumentSnapshot emergency : sortedEmergencies) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(8, 12, 8, 12);
            Timestamp timestamp = emergencyTimestamp(emergency);
            TextView date = historyCell(timestamp == null
                    ? "Date unavailable" : dateFormat.format(timestamp.toDate()), 1f, false);
            String hospital = emergency.getString("hospitalName");
            TextView hospitalView = historyCell(hospital == null || hospital.trim().isEmpty() ? "Hospital" : hospital, 1.5f, false);
            TextView status = historyCell(EmergencyStatus.label(emergency.getString("status")), 1f, true);
            status.setGravity(android.view.Gravity.END);
            row.addView(date);
            row.addView(hospitalView);
            row.addView(status);
            rows.addView(row);
            View divider = new View(this);
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.stroke_subtle));
            rows.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
    }

    /**
     * Use Firestore's local estimate while serverTimestamp() is pending, then tolerate older
     * records that stored the creation date under a different field name.
     */
    private Timestamp emergencyTimestamp(DocumentSnapshot emergency) {
        Timestamp timestamp = emergency.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        if (timestamp == null) timestamp = emergency.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        if (timestamp == null) timestamp = emergency.getTimestamp("requestedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        return timestamp;
    }

    private TextView historyCell(String value, float weight, boolean bold) {
        TextView cell = new TextView(this);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        cell.setText(value);
        cell.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        cell.setTextSize(11);
        if (bold) cell.setTypeface(null, android.graphics.Typeface.BOLD);
        return cell;
    }

    private void setSosState(SosState state) {
        sosState = state;
        if (sosLabel == null) return;
        switch (state) {
            case LOADING:
                sosLabel.setText("SENDING...");
                sosButton.setAlpha(0.7f);
                break;
            case SENT:
                sosLabel.setText("SENT");
                sosButton.setAlpha(1f);
                break;
            case ERROR:
                sosLabel.setText("RETRY SOS");
                sosButton.setAlpha(1f);
                break;
            case ACTIVE:
                sosLabel.setText("TRACK\nHELP");
                sosButton.setAlpha(1f);
                break;
            default:
                sosLabel.setText(R.string.sos_button_text);
                sosButton.setAlpha(1f);
        }
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, StartingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void startHoldCountdown() {
        cancelHoldCountdown();
        holdTimer = new CountDownTimer(HOLD_DURATION_MS, HOLD_DURATION_MS) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                holdTimer = null;
                stopScalingAnimation();
                triggerSos();
            }
        }.start();
    }

    private void cancelHoldCountdown() {
        if (holdTimer != null) {
            holdTimer.cancel();
            holdTimer = null;
        }
    }

    private void startScalingAnimation() {
        ScaleAnimation scaleUp = new ScaleAnimation(1f, 1.3f, 1f, 1.3f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleUp.setDuration(HOLD_DURATION_MS);
        scaleUp.setFillAfter(true);
        sosButton.startAnimation(scaleUp);
        vibrate(50);
    }

    private void stopScalingAnimation() {
        sosButton.clearAnimation();
        ScaleAnimation scaleDown = new ScaleAnimation(1.3f, 1f, 1.3f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleDown.setDuration(200);
        sosButton.startAnimation(scaleDown);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return false;
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void failSos(String userMessage) {
        sosInProgress = false;
        setSosState(SosState.ERROR);
        Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show();
    }

    private void triggerSos() {
        if (sosInProgress) return;
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }
        if (activeEmergencyId != null) {
            openMap(activeEmergencyId);
            return;
        }
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            Toast.makeText(this, "Location permission is required to send an SOS.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Turn on device location to send an SOS.", Toast.LENGTH_LONG).show();
            return;
        }

        sosInProgress = true;
        setSosState(SosState.LOADING);
        vibrate(500);
        SurakshaLog.info(SurakshaLog.SOS, "SOS triggered", "userId=" + mAuth.getCurrentUser().getUid());

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            SurakshaLog.warn(SurakshaLog.SOS, "GPS returned null");
                            failSos("Unable to get your current location. Please ensure device GPS is turned on and try again.");
                            return;
                        }
                        if (!HaversineDistance.isValidCoordinate(location.getLatitude(), location.getLongitude())) {
                            SurakshaLog.warn(SurakshaLog.SOS, "GPS returned invalid coordinate");
                            failSos("Your GPS position is invalid. Please try again outdoors.");
                            return;
                        }
                        SurakshaLog.info(SurakshaLog.SOS, "GPS fix",
                                String.format(Locale.US, "accuracy=%.0fm", location.getAccuracy()));
                        checkNoActiveEmergencyThenSelect(location);
                    })
                    .addOnFailureListener(e -> {
                        SurakshaLog.error(SurakshaLog.SOS, "getCurrentLocation", e);
                        failSos("Failed to get GPS location: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            SurakshaLog.error(SurakshaLog.SOS, "location permission", e);
            failSos("Location permission missing.");
        }
    }

    /** Fresh server check right before creation so a second device/tab cannot create a duplicate. */
    private void checkNoActiveEmergencyThenSelect(Location location) {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("emergencies")
                .whereEqualTo("userId", uid)
                .whereIn("status", EmergencyStatus.ACTIVE)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        activeEmergencyId = snapshot.getDocuments().get(0).getId();
                        sosInProgress = false;
                        setSosState(SosState.ACTIVE);
                        SurakshaLog.info(SurakshaLog.SOS, "duplicate SOS blocked", "emergencyId=" + activeEmergencyId);
                        Toast.makeText(this, "You already have an active emergency.", Toast.LENGTH_LONG).show();
                        openMap(activeEmergencyId);
                        return;
                    }
                    selectHospitalAndCreate(location);
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.SOS, "duplicate check query", e, "userId=" + uid);
                    failSos(SurakshaLog.userMessage("Could not verify existing emergencies", e));
                });
    }

    private void selectHospitalAndCreate(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        HospitalSelection.selectNearest(db, lat, lng, new ArrayList<>(), new HospitalSelection.Callback() {
            @Override
            public void onSelected(HospitalSelection.Candidate hospital) {
                createEmergency(location, hospital);
            }

            @Override
            public void onNoneAvailable(String reason) {
                SurakshaLog.warn(SurakshaLog.SOS, "no hospital available", "reason=" + reason);
                failSos("No hospital is available right now (" + reason + "). Call 108/112.");
            }

            @Override
            public void onError(Exception e) {
                SurakshaLog.error(SurakshaLog.SOS, "hospital selection query", e);
                failSos(SurakshaLog.userMessage("Could not search hospitals", e));
            }
        });
    }

    private void createEmergency(Location location, HospitalSelection.Candidate hospital) {
        String uid = mAuth.getCurrentUser().getUid();
        DocumentReference ref = db.collection("emergencies").document();
        String emergencyId = ref.getId();

        Map<String, Object> emergency = new HashMap<>();
        emergency.put("emergencyId", emergencyId);
        emergency.put("userId", uid);
        emergency.put("userName", displayName());
        emergency.put("userEmail", profile != null ? profile.getString("email") : null);
        emergency.put("userPhone", profile != null ? profile.getString("phone") : null);
        emergency.put("bloodGroup", profile != null ? profile.getString("bloodGroup") : null);
        emergency.put("emergencyContactName", profile != null ? profile.getString("emergencyName") : null);
        emergency.put("emergencyContactPhone", userEmergencyPhone);

        emergency.put("latitude", location.getLatitude());
        emergency.put("longitude", location.getLongitude());
        emergency.put("locationAccuracy", (double) location.getAccuracy());
        emergency.put("location", String.format(Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude()));
        emergency.put("locationUpdatedAt", FieldValue.serverTimestamp());

        emergency.put("hospitalId", hospital.hospitalId);
        emergency.put("hospitalName", hospital.hospitalName);
        emergency.put("hospitalLatitude", hospital.latitude);
        emergency.put("hospitalLongitude", hospital.longitude);
        emergency.put("distanceToHospitalMeters", hospital.distanceMeters);
        emergency.put("searchRadiusMeters", hospital.searchRadiusMeters);
        emergency.put("rejectedHospitalIds", new ArrayList<String>());
        emergency.put("rejectedAmbulanceIds", new ArrayList<String>());
        emergency.put("ambulanceId", null);

        emergency.put("status", EmergencyStatus.HOSPITAL_REQUESTED);
        emergency.put("priority", EmergencyPriority.levelForStatus(EmergencyStatus.HOSPITAL_REQUESTED));
        emergency.put("timestamp", FieldValue.serverTimestamp());

        ref.set(emergency)
                .addOnSuccessListener(aVoid -> {
                    SurakshaLog.info(SurakshaLog.SOS, "emergency created",
                            "emergencyId=" + emergencyId, "hospitalId=" + hospital.hospitalId,
                            "status=" + EmergencyStatus.HOSPITAL_REQUESTED);
                    activeEmergencyId = emergencyId;
                    sosInProgress = false;
                    setSosState(SosState.SENT);
                    Toast.makeText(this, "SOS sent to " + hospital.hospitalName + " ("
                            + HaversineDistance.formatDistance(hospital.distanceMeters) + ")", Toast.LENGTH_LONG).show();
                    openMap(emergencyId);
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.SOS, "create emergency", e,
                            "emergencyId=" + emergencyId, "hospitalId=" + hospital.hospitalId);
                    failSos(SurakshaLog.userMessage("Failed to send SOS", e));
                });
    }

    private void openMap(String emergencyId) {
        Intent intent = new Intent(this, UserMapActivity.class);
        intent.putExtra(UserMapActivity.EXTRA_EMERGENCY_ID, emergencyId);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                triggerSos();
            } else {
                Toast.makeText(this, "Emergency SOS cannot be sent without location permission.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long duration) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(duration);
        }
    }
}
