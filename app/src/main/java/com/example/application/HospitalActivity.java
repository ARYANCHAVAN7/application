package com.example.application;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.application.utils.AmbulanceRouteSimulator;
import com.example.application.utils.AmbulanceSelection;
import com.example.application.utils.DriverCredentials;
import com.example.application.utils.EmergencyPriority;
import com.example.application.utils.FirestoreValues;
import com.example.application.utils.HaversineDistance;
import com.example.application.utils.HospitalSelection;
import com.example.application.utils.SurakshaLog;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hospital dashboard.
 * - Only verified hospitals (role == "hospital" && isVerified == true) may stay here.
 * - Shows ONLY emergencies with hospitalId == this hospital's UID, ordered by EmergencyPriority.
 * - Accept: transaction (HOSPITAL_REQUESTED -> HOSPITAL_ACCEPTED, availableBeds - 1), then dispatch.
 * - Reject: HospitalSelection picks the next nearest verified hospital (excluding every hospital
 *   that already rejected) and reassigns the SAME document; if none, HOSPITAL_REJECTED.
 * - Dispatch: AmbulanceSelection ranks this hospital's online, idle ambulances by distance;
 *   a transaction assigns the nearest one (AMBULANCE_DISPATCHED) and marks it isOnTrip.
 */
public class HospitalActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST_CODE = 3101;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration emergencyListener;
    private ListenerRegistration fleetListener;

    private String hospitalUid;
    private String hospitalName;
    private final Set<String> busyEmergencyIds = new HashSet<>();

    private TextView tvHospitalName, tvAvailableBeds, tvAvailableAmbulances, tvActiveEmergencies, tvNoRequests;
    private EditText etTotalBeds, etAvailableBeds, etTotalAmbulances, etAvailableAmbulances;
    private EditText etHospitalLatitude, etHospitalLongitude;
    private EditText etDriverName, etDriverPhone, etDriverEmail, etDriverPassword, etVehiclePlate, etVehicleType;
    private RecyclerView rvAmbulanceFleet, rvEmergencyRequests;
    private AmbulanceAdapter adapter;
    private EmergencyAdapter emergencyAdapter;
    private final List<DocumentSnapshot> fleetList = new ArrayList<>();
    private final List<DocumentSnapshot> emergencyList = new ArrayList<>();

    /** Thrown inside transactions for business-rule rejections (not retried by the SDK). */
    private static FirebaseFirestoreException abort(String message) {
        return new FirebaseFirestoreException(message, FirebaseFirestoreException.Code.CANCELLED);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();
        setupRecyclerView();

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            mAuth.signOut();
            redirectToLogin();
        });
        findViewById(R.id.btnUpdateResources).setOnClickListener(v -> updateResources());
        findViewById(R.id.btnAddAmbulance).setOnClickListener(v -> addAmbulance());
        findViewById(R.id.btnUseHospitalLocation).setOnClickListener(v -> fillCurrentLocation());
    }

    private void bindViews() {
        tvHospitalName = findViewById(R.id.tvHospitalName);
        tvAvailableBeds = findViewById(R.id.tvAvailableBeds);
        tvAvailableAmbulances = findViewById(R.id.tvAvailableAmbulances);
        tvActiveEmergencies = findViewById(R.id.tvActiveEmergencies);
        tvNoRequests = findViewById(R.id.tvNoRequests);

        etTotalBeds = findViewById(R.id.etTotalBeds);
        etAvailableBeds = findViewById(R.id.etAvailableBeds);
        etTotalAmbulances = findViewById(R.id.etTotalAmbulances);
        etAvailableAmbulances = findViewById(R.id.etAvailableAmbulances);
        etHospitalLatitude = findViewById(R.id.etHospitalLatitude);
        etHospitalLongitude = findViewById(R.id.etHospitalLongitude);

        etDriverName = findViewById(R.id.etDriverName);
        etDriverPhone = findViewById(R.id.etDriverPhone);
        etDriverEmail = findViewById(R.id.etDriverEmail);
        etDriverPassword = findViewById(R.id.etDriverPassword);
        etVehiclePlate = findViewById(R.id.etVehiclePlate);
        etVehicleType = findViewById(R.id.etVehicleType);
        rvAmbulanceFleet = findViewById(R.id.rvAmbulanceFleet);
        rvEmergencyRequests = findViewById(R.id.rvEmergencyRequests);
    }

    private void setupRecyclerView() {
        adapter = new AmbulanceAdapter(fleetList);
        rvAmbulanceFleet.setLayoutManager(new LinearLayoutManager(this));
        rvAmbulanceFleet.setAdapter(adapter);

        emergencyAdapter = new EmergencyAdapter(emergencyList);
        rvEmergencyRequests.setLayoutManager(new LinearLayoutManager(this));
        rvEmergencyRequests.setAdapter(emergencyAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() == null || mAuth.getCurrentUser().isAnonymous()) {
            redirectToLogin();
            return;
        }
        hospitalUid = mAuth.getCurrentUser().getUid();
        verifyAndLoad();
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeListeners();
    }

    private void removeListeners() {
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
        if (fleetListener != null) {
            fleetListener.remove();
            fleetListener = null;
        }
    }

    /** Role + verification check; listeners start only for a verified hospital. */
    private void verifyAndLoad() {
        db.collection("users").document(hospitalUid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !"hospital".equals(doc.getString("role"))
                            || !FirestoreValues.getBoolean(doc, "isVerified")) {
                        SurakshaLog.warn(SurakshaLog.HOSPITAL, "dashboard access denied",
                                "hospitalId=" + hospitalUid, "role=" + doc.getString("role"));
                        Toast.makeText(this, "Hospital account is not verified.", Toast.LENGTH_LONG).show();
                        mAuth.signOut();
                        redirectToLogin();
                        return;
                    }
                    bindProfile(doc);
                    listenForEmergencies();
                    listenForFleet();
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.HOSPITAL, "load hospital profile", e, "hospitalId=" + hospitalUid);
                    Toast.makeText(this, SurakshaLog.userMessage("Could not load hospital profile", e), Toast.LENGTH_LONG).show();
                });
    }

    private void bindProfile(DocumentSnapshot doc) {
        hospitalName = FirestoreValues.getString(doc, "hospitalName");
        tvHospitalName.setText(hospitalName);
        etTotalBeds.setText(FirestoreValues.getString(doc, "totalBeds"));
        etAvailableBeds.setText(FirestoreValues.getString(doc, "availableBeds"));
        etTotalAmbulances.setText(FirestoreValues.getString(doc, "totalAmbulances"));
        etAvailableAmbulances.setText(FirestoreValues.getString(doc, "availableAmbulances"));
        tvAvailableBeds.setText(FirestoreValues.getString(doc, "availableBeds"));
        tvAvailableAmbulances.setText(FirestoreValues.getString(doc, "availableAmbulances"));
        Double lat = FirestoreValues.getDouble(doc, "latitude");
        Double lng = FirestoreValues.getDouble(doc, "longitude");
        if (lat != null) etHospitalLatitude.setText(String.format(Locale.US, "%.6f", lat));
        if (lng != null) etHospitalLongitude.setText(String.format(Locale.US, "%.6f", lng));
        if (!HaversineDistance.isValidCoordinate(lat, lng)) {
            Toast.makeText(this, "Set your hospital location so patients can be routed to you.", Toast.LENGTH_LONG).show();
        }
    }

    private Double getHospitalLatitude() {
        try {
            if (etHospitalLatitude != null && etHospitalLatitude.getText() != null) {
                String s = etHospitalLatitude.getText().toString().trim();
                if (!s.isEmpty()) return Double.parseDouble(s);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Double getHospitalLongitude() {
        try {
            if (etHospitalLongitude != null && etHospitalLongitude.getText() != null) {
                String s = etHospitalLongitude.getText().toString().trim();
                if (!s.isEmpty()) return Double.parseDouble(s);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ------------------------------------------------------------------ resources

    private void fillCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_REQUEST_CODE);
            return;
        }
        try {
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            Toast.makeText(this, "Could not get location", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        etHospitalLatitude.setText(String.format(Locale.US, "%.6f", location.getLatitude()));
                        etHospitalLongitude.setText(String.format(Locale.US, "%.6f", location.getLongitude()));
                    })
                    .addOnFailureListener(e -> SurakshaLog.error(SurakshaLog.HOSPITAL, "hospital getCurrentLocation", e));
        } catch (SecurityException e) {
            SurakshaLog.error(SurakshaLog.HOSPITAL, "hospital location permission", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fillCurrentLocation();
        }
    }

    private void updateResources() {
        if (hospitalUid == null) return;
        try {
            long totalBeds = Long.parseLong(etTotalBeds.getText().toString().trim());
            long availableBeds = Long.parseLong(etAvailableBeds.getText().toString().trim());
            long totalAmbulances = Long.parseLong(etTotalAmbulances.getText().toString().trim());
            long availableAmbulances = Long.parseLong(etAvailableAmbulances.getText().toString().trim());
            double latitude = Double.parseDouble(etHospitalLatitude.getText().toString().trim());
            double longitude = Double.parseDouble(etHospitalLongitude.getText().toString().trim());

            if (totalBeds < 0 || availableBeds < 0 || totalAmbulances < 0 || availableAmbulances < 0) {
                Toast.makeText(this, "Values cannot be negative", Toast.LENGTH_SHORT).show();
                return;
            }
            if (availableBeds > totalBeds) {
                etAvailableBeds.setError("Available beds cannot exceed total beds");
                return;
            }
            if (availableAmbulances > totalAmbulances) {
                etAvailableAmbulances.setError("Available ambulances cannot exceed total ambulances");
                return;
            }
            if (!HaversineDistance.isValidCoordinate(latitude, longitude)) {
                etHospitalLatitude.setError("Invalid coordinates");
                return;
            }

            // role / isVerified are intentionally never written here (and rules forbid it).
            Map<String, Object> updates = new HashMap<>();
            updates.put("totalBeds", totalBeds);
            updates.put("availableBeds", availableBeds);
            updates.put("totalAmbulances", totalAmbulances);
            updates.put("availableAmbulances", availableAmbulances);
            updates.put("latitude", latitude);
            updates.put("longitude", longitude);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            db.collection("users").document(hospitalUid).update(updates)
                    .addOnSuccessListener(aVoid -> {
                        SurakshaLog.info(SurakshaLog.HOSPITAL, "resources updated", "hospitalId=" + hospitalUid,
                                "availableBeds=" + availableBeds);
                        Toast.makeText(this, "Resources updated successfully", Toast.LENGTH_SHORT).show();
                        tvAvailableBeds.setText(String.valueOf(availableBeds));
                        tvAvailableAmbulances.setText(String.valueOf(availableAmbulances));
                    })
                    .addOnFailureListener(e -> {
                        SurakshaLog.error(SurakshaLog.HOSPITAL, "update resources", e, "hospitalId=" + hospitalUid);
                        Toast.makeText(this, SurakshaLog.userMessage("Update failed", e), Toast.LENGTH_LONG).show();
                    });
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numeric values (beds, ambulances, latitude, longitude)", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ fleet

    private void addAmbulance() {
        if (hospitalUid == null || hospitalName == null || hospitalName.trim().isEmpty()) {
            Toast.makeText(this, "Hospital profile not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = etDriverName.getText().toString().trim();
        String phone = etDriverPhone.getText().toString().trim();
        String email = etDriverEmail.getText().toString().trim();
        String security = etDriverPassword.getText().toString().trim();
        String plate = etVehiclePlate.getText().toString().trim();
        String vehicleType = etVehicleType.getText().toString().trim();

        if (name.isEmpty() || plate.isEmpty()) {
            Toast.makeText(this, "Driver name and vehicle number are required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (security.length() < DriverCredentials.MIN_SECURITY_NUMBER_LENGTH) {
            etDriverPassword.setError("Security number must be at least "
                    + DriverCredentials.MIN_SECURITY_NUMBER_LENGTH + " characters");
            return;
        }

        String loginKey = DriverCredentials.loginKey(hospitalName, security);
        DocumentReference loginRef = db.collection("ambulanceLogins").document(loginKey);
        loginRef.get()
                .addOnSuccessListener(existing -> {
                    if (existing.exists()) {
                        Toast.makeText(this, "This security number is already used for this hospital. Choose another.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    DocumentReference ambRef = db.collection("ambulances").document();
                    Map<String, Object> ambulance = new HashMap<>();
                    ambulance.put("ambulanceId", ambRef.getId());
                    ambulance.put("driverName", name);
                    ambulance.put("driverPhone", phone);
                    ambulance.put("driverEmail", email);
                    ambulance.put("vehicleNumber", plate);
                    ambulance.put("vehicleType", vehicleType);
                    ambulance.put("hospitalId", hospitalUid);
                    ambulance.put("hospitalName", hospitalName);
                    ambulance.put("isOnTrip", false);
                    ambulance.put("isOnline", false);
                    Double hLat = getHospitalLatitude();
                    Double hLng = getHospitalLongitude();
                    if (HaversineDistance.isValidCoordinate(hLat, hLng)) {
                        ambulance.put("latitude", hLat);
                        ambulance.put("longitude", hLng);
                    }
                    ambulance.put("currentEmergencyId", null);
                    ambulance.put("createdAt", FieldValue.serverTimestamp());

                    Map<String, Object> login = new HashMap<>();
                    login.put("ambulanceId", ambRef.getId());
                    login.put("hospitalId", hospitalUid);
                    login.put("createdAt", FieldValue.serverTimestamp());

                    WriteBatch batch = db.batch();
                    batch.set(ambRef, ambulance);
                    batch.set(loginRef, login);
                    if (!email.isEmpty()) {
                        String emailLoginKey = DriverCredentials.loginKey(email, security);
                        DocumentReference emailLoginRef = db.collection("ambulanceLogins").document(emailLoginKey);
                        batch.set(emailLoginRef, login);
                    }
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                SurakshaLog.info(SurakshaLog.AMBULANCE, "ambulance added",
                                        "hospitalId=" + hospitalUid, "ambulanceId=" + ambRef.getId());
                                createDriverAuthAccount(email, security, ambRef.getId(), name, phone);
                                Toast.makeText(this, "Ambulance added! Driver can log in with Firebase Email/Password or Hospital Name + Security Number.",
                                        Toast.LENGTH_LONG).show();
                                clearAmbulanceFields();
                            })
                            .addOnFailureListener(e -> {
                                SurakshaLog.error(SurakshaLog.AMBULANCE, "add ambulance", e, "hospitalId=" + hospitalUid);
                                Toast.makeText(this, SurakshaLog.userMessage("Could not add ambulance", e), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.AMBULANCE, "check ambulance login key", e, "hospitalId=" + hospitalUid);
                    Toast.makeText(this, SurakshaLog.userMessage("Could not add ambulance", e), Toast.LENGTH_LONG).show();
                });
    }

    private void createDriverAuthAccount(String email, String password, String ambulanceId, String name, String phone) {
        if (email == null || email.trim().isEmpty() || password == null || password.length() < 6) {
            return;
        }
        try {
            FirebaseApp secondaryApp;
            try {
                secondaryApp = FirebaseApp.getInstance("DriverAuthApp");
            } catch (IllegalStateException e) {
                FirebaseOptions options = FirebaseApp.getInstance().getOptions();
                secondaryApp = FirebaseApp.initializeApp(getApplicationContext(), options, "DriverAuthApp");
            }
            FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
            secondaryAuth.createUserWithEmailAndPassword(email.trim(), password)
                    .addOnSuccessListener(authResult -> {
                        if (authResult.getUser() != null) {
                            String driverUid = authResult.getUser().getUid();
                            Map<String, Object> driverProfile = new HashMap<>();
                            driverProfile.put("uid", driverUid);
                            driverProfile.put("email", email.trim());
                            driverProfile.put("fullName", name);
                            driverProfile.put("phone", phone);
                            driverProfile.put("role", "ambulance");
                            driverProfile.put("ambulanceId", ambulanceId);
                            driverProfile.put("hospitalId", hospitalUid);
                            driverProfile.put("hospitalName", hospitalName);
                            driverProfile.put("createdAt", FieldValue.serverTimestamp());
                            db.collection("users").document(driverUid).set(driverProfile, SetOptions.merge());

                            Map<String, Object> session = new HashMap<>();
                            session.put("ambulanceId", ambulanceId);
                            session.put("driverEmail", email.trim());
                            session.put("createdAt", FieldValue.serverTimestamp());
                            db.collection("driverSessions").document(driverUid).set(session, SetOptions.merge());

                            db.collection("ambulances").document(ambulanceId).update("driverUid", driverUid);
                        }
                        secondaryAuth.signOut();
                    })
                    .addOnFailureListener(e -> {
                        SurakshaLog.error(SurakshaLog.AMBULANCE, "createDriverAuthAccount note: driver may already exist in Auth", e);
                        secondaryAuth.signOut();
                    });
        } catch (Exception ex) {
            SurakshaLog.error(SurakshaLog.AMBULANCE, "createDriverAuthAccount exception", ex);
        }
    }

    private void clearAmbulanceFields() {
        etDriverName.setText("");
        etDriverPhone.setText("");
        etDriverEmail.setText("");
        etDriverPassword.setText("");
        etVehiclePlate.setText("");
        etVehicleType.setText("");
    }

    private void listenForFleet() {
        if (fleetListener != null) return;
        fleetListener = db.collection("ambulances")
                .whereEqualTo("hospitalId", hospitalUid)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        SurakshaLog.error(SurakshaLog.AMBULANCE, "fleet listener", error, "hospitalId=" + hospitalUid);
                        return;
                    }
                    if (value == null) return;
                    fleetList.clear();
                    fleetList.addAll(value.getDocuments());
                    adapter.notifyDataSetChanged();
                    // Backfill hospital coordinates to any ambulances created without coordinates
                    Double hLat = getHospitalLatitude();
                    Double hLng = getHospitalLongitude();
                    if (HaversineDistance.isValidCoordinate(hLat, hLng)) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Double aLat = FirestoreValues.getDouble(doc, "latitude");
                            Double aLng = FirestoreValues.getDouble(doc, "longitude");
                            if (!HaversineDistance.isValidCoordinate(aLat, aLng)) {
                                doc.getReference().update("latitude", hLat, "longitude", hLng)
                                        .addOnFailureListener(e -> SurakshaLog.warn(SurakshaLog.AMBULANCE, "backfill coords", e.getMessage()));
                            }
                        }
                    }
                });
    }

    // ------------------------------------------------------------------ emergencies

    private void listenForEmergencies() {
        if (emergencyListener != null) return; // never duplicate
        emergencyListener = db.collection("emergencies")
                .whereEqualTo("hospitalId", hospitalUid)
                .whereIn("status", EmergencyStatus.HOSPITAL_VISIBLE)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        SurakshaLog.error(SurakshaLog.HOSPITAL, "emergency listener", error, "hospitalId=" + hospitalUid);
                        Toast.makeText(this, SurakshaLog.userMessage("Failed to load emergency requests", error),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (value == null) return;
                    List<DocumentSnapshot> docs = new ArrayList<>(value.getDocuments());
                    docs.sort(EmergencyPriority::compare); // priority-ordered queue
                    emergencyList.clear();
                    emergencyList.addAll(docs);
                    emergencyAdapter.notifyDataSetChanged();
                    tvActiveEmergencies.setText(String.valueOf(docs.size()));

                    for (DocumentSnapshot doc : docs) {
                        String st = doc.getString("status");
                        String ambId = doc.getString("ambulanceId");
                        if (ambId != null && (EmergencyStatus.AMBULANCE_DISPATCHED.equals(st)
                                || EmergencyStatus.AMBULANCE_ARRIVED.equals(st)
                                || EmergencyStatus.PATIENT_PICKED_UP.equals(st)
                                || EmergencyStatus.EN_ROUTE_TO_HOSPITAL.equals(st))) {
                            Double hLat = getHospitalLatitude();
                            Double hLng = getHospitalLongitude();
                            if (!HaversineDistance.isValidCoordinate(hLat, hLng)) {
                                hLat = FirestoreValues.getDouble(doc, "hospitalLatitude");
                                hLng = FirestoreValues.getDouble(doc, "hospitalLongitude");
                            }
                            Double pLat = FirestoreValues.getDouble(doc, "latitude");
                            Double pLng = FirestoreValues.getDouble(doc, "longitude");
                            if (HaversineDistance.isValidCoordinate(hLat, hLng) && HaversineDistance.isValidCoordinate(pLat, pLng)) {
                                AmbulanceRouteSimulator.start(this, ambId, doc.getId(), hLat, hLng, pLat, pLng, st);
                            }
                        }
                    }

                    boolean empty = emergencyList.isEmpty();
                    tvNoRequests.setVisibility(empty ? View.VISIBLE : View.GONE);
                    rvEmergencyRequests.setVisibility(empty ? View.GONE : View.VISIBLE);
                    if (empty) tvNoRequests.setText(R.string.msg_no_requests);
                    SurakshaLog.info(SurakshaLog.HOSPITAL, "emergency snapshot",
                            "hospitalId=" + hospitalUid, "count=" + docs.size(),
                            "fromCache=" + value.getMetadata().isFromCache());
                });
    }

    private boolean markBusy(String emergencyId) {
        if (busyEmergencyIds.contains(emergencyId)) return false;
        busyEmergencyIds.add(emergencyId);
        emergencyAdapter.notifyDataSetChanged();
        return true;
    }

    private void clearBusy(String emergencyId) {
        busyEmergencyIds.remove(emergencyId);
        emergencyAdapter.notifyDataSetChanged();
    }

    private void acceptEmergency(DocumentSnapshot emergencyDoc) {
        String emergencyId = emergencyDoc.getId();
        if (!markBusy(emergencyId)) return;
        DocumentReference emergencyRef = db.collection("emergencies").document(emergencyId);
        DocumentReference hospitalRef = db.collection("users").document(hospitalUid);

        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(emergencyRef);
            DocumentSnapshot hospital = transaction.get(hospitalRef);
            if (!snap.exists()) throw abort("Emergency no longer exists.");
            if (!hospitalUid.equals(snap.getString("hospitalId"))) throw abort("Emergency is no longer assigned to you.");
            if (!EmergencyStatus.HOSPITAL_REQUESTED.equals(snap.getString("status"))) {
                throw abort("Emergency was already handled (" + EmergencyStatus.label(snap.getString("status")) + ").");
            }
            Integer beds = FirestoreValues.getInt(hospital, "availableBeds");
            if (beds == null || beds <= 0) throw abort("No available beds. Update resources or reject.");

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", EmergencyStatus.HOSPITAL_ACCEPTED);
            updates.put("priority", EmergencyPriority.levelForStatus(EmergencyStatus.HOSPITAL_ACCEPTED));
            updates.put("acceptedBy", hospitalUid);
            updates.put("acceptedAt", FieldValue.serverTimestamp());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(emergencyRef, updates);
            transaction.update(hospitalRef, "availableBeds", (long) (beds - 1));
            return null;
        }).addOnSuccessListener(r -> {
            SurakshaLog.info(SurakshaLog.HOSPITAL, "emergency accepted", "emergencyId=" + emergencyId,
                    "hospitalId=" + hospitalUid, "status=" + EmergencyStatus.HOSPITAL_ACCEPTED);
            Toast.makeText(this, "Accepted. Dispatching nearest ambulance...", Toast.LENGTH_SHORT).show();
            clearBusy(emergencyId);
            dispatchAmbulance(emergencyId);
        }).addOnFailureListener(e -> {
            SurakshaLog.error(SurakshaLog.HOSPITAL, "accept emergency", e, "emergencyId=" + emergencyId,
                    "hospitalId=" + hospitalUid);
            clearBusy(emergencyId);
            Toast.makeText(this, e.getMessage() != null ? e.getMessage() : "Failed to accept", Toast.LENGTH_LONG).show();
        });
    }

    private void rejectEmergency(DocumentSnapshot emergencyDoc) {
        String emergencyId = emergencyDoc.getId();
        Double lat = FirestoreValues.getDouble(emergencyDoc, "latitude");
        Double lng = FirestoreValues.getDouble(emergencyDoc, "longitude");
        if (!markBusy(emergencyId)) return;

        Set<String> excluded = new HashSet<>();
        Object rejected = emergencyDoc.get("rejectedHospitalIds");
        if (rejected instanceof List) {
            for (Object o : (List<?>) rejected) if (o instanceof String) excluded.add((String) o);
        }
        excluded.add(hospitalUid);

        if (!HaversineDistance.isValidCoordinate(lat, lng)) {
            commitReject(emergencyId, null);
            return;
        }
        HospitalSelection.selectNearest(db, lat, lng, excluded, new HospitalSelection.Callback() {
            @Override
            public void onSelected(HospitalSelection.Candidate candidate) {
                commitReject(emergencyId, candidate);
            }

            @Override
            public void onNoneAvailable(String reason) {
                SurakshaLog.info(SurakshaLog.HOSPITAL, "no next hospital", "emergencyId=" + emergencyId, "reason=" + reason);
                commitReject(emergencyId, null);
            }

            @Override
            public void onError(Exception e) {
                SurakshaLog.error(SurakshaLog.HOSPITAL, "next hospital selection", e, "emergencyId=" + emergencyId);
                clearBusy(emergencyId);
                Toast.makeText(HospitalActivity.this, SurakshaLog.userMessage("Could not find next hospital", e),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** next == null means no other hospital is available -> terminal HOSPITAL_REJECTED. */
    private void commitReject(String emergencyId, HospitalSelection.Candidate next) {
        DocumentReference ref = db.collection("emergencies").document(emergencyId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            if (!snap.exists()) throw abort("Emergency no longer exists.");
            if (!hospitalUid.equals(snap.getString("hospitalId"))
                    || !EmergencyStatus.HOSPITAL_REQUESTED.equals(snap.getString("status"))) {
                throw abort("Emergency was already handled.");
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("rejectedHospitalIds", FieldValue.arrayUnion(hospitalUid));
            updates.put("lastRejectedAt", FieldValue.serverTimestamp());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            if (next != null) {
                Double lat = FirestoreValues.getDouble(snap, "latitude");
                Double lng = FirestoreValues.getDouble(snap, "longitude");
                updates.put("hospitalId", next.hospitalId);
                updates.put("hospitalName", next.hospitalName);
                updates.put("hospitalLatitude", next.latitude);
                updates.put("hospitalLongitude", next.longitude);
                updates.put("distanceToHospitalMeters", lat != null && lng != null
                        ? HaversineDistance.distanceInMeters(lat, lng, next.latitude, next.longitude) : next.distanceMeters);
                updates.put("searchRadiusMeters", next.searchRadiusMeters);
                updates.put("status", EmergencyStatus.HOSPITAL_REQUESTED);
            } else {
                updates.put("status", EmergencyStatus.HOSPITAL_REJECTED);
                updates.put("priority", EmergencyPriority.levelForStatus(EmergencyStatus.HOSPITAL_REJECTED));
            }
            transaction.update(ref, updates);
            return null;
        }).addOnSuccessListener(r -> {
            SurakshaLog.info(SurakshaLog.HOSPITAL, "emergency rejected", "emergencyId=" + emergencyId,
                    "fromHospitalId=" + hospitalUid,
                    next != null ? "reassignedTo=" + next.hospitalId : "status=" + EmergencyStatus.HOSPITAL_REJECTED);
            clearBusy(emergencyId);
            Toast.makeText(this, next != null ? "Rejected. Forwarded to " + next.hospitalName
                    : "Rejected. No other hospital is available.", Toast.LENGTH_LONG).show();
        }).addOnFailureListener(e -> {
            SurakshaLog.error(SurakshaLog.HOSPITAL, "reject emergency", e, "emergencyId=" + emergencyId,
                    "hospitalId=" + hospitalUid);
            clearBusy(emergencyId);
            Toast.makeText(this, e.getMessage() != null ? e.getMessage() : "Reject failed", Toast.LENGTH_LONG).show();
        });
    }

    /** Selects the nearest eligible ambulance of this hospital and assigns it atomically. */
    private void dispatchAmbulance(String emergencyId) {
        if (!markBusy(emergencyId)) return;
        DocumentReference emergencyRef = db.collection("emergencies").document(emergencyId);
        emergencyRef.get()
                .addOnSuccessListener(emergency -> {
                    Double lat = FirestoreValues.getDouble(emergency, "latitude");
                    Double lng = FirestoreValues.getDouble(emergency, "longitude");
                    if (!HaversineDistance.isValidCoordinate(lat, lng)) {
                        clearBusy(emergencyId);
                        Toast.makeText(this, "Emergency has no valid location", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Set<String> excluded = new HashSet<>();
                    Object rejected = emergency.get("rejectedAmbulanceIds");
                    if (rejected instanceof List) {
                        for (Object o : (List<?>) rejected) if (o instanceof String) excluded.add((String) o);
                    }
                    db.collection("ambulances")
                            .whereEqualTo("hospitalId", hospitalUid)
                            .whereEqualTo("isOnTrip", false)
                            .get()
                            .addOnSuccessListener(fleet -> {
                                Double hLat = getHospitalLatitude();
                                Double hLng = getHospitalLongitude();
                                double defaultLat = HaversineDistance.isValidCoordinate(hLat, hLng) ? hLat : 0.0;
                                double defaultLng = HaversineDistance.isValidCoordinate(hLat, hLng) ? hLng : 0.0;

                                List<AmbulanceSelection.AmbulanceCandidate> ranked = AmbulanceSelection.rankForEmergency(
                                        fleet.getDocuments(), hospitalUid, defaultLat, defaultLng, lat, lng, excluded);
                                SurakshaLog.info(SurakshaLog.AMBULANCE, "ambulance candidates",
                                        "emergencyId=" + emergencyId, "idle=" + fleet.size(), "eligible=" + ranked.size());
                                if (ranked.isEmpty()) {
                                    clearBusy(emergencyId);
                                    if (fleet.isEmpty()) {
                                        Toast.makeText(this, "No idle ambulances found for this hospital. Please add an ambulance first.",
                                                Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(this, "All ambulances are busy or lack a valid location.",
                                                Toast.LENGTH_LONG).show();
                                    }
                                    return;
                                }
                                tryDispatch(emergencyId, ranked, 0);
                            })
                            .addOnFailureListener(e -> {
                                SurakshaLog.error(SurakshaLog.AMBULANCE, "query idle ambulances", e,
                                        "emergencyId=" + emergencyId, "hospitalId=" + hospitalUid);
                                clearBusy(emergencyId);
                                Toast.makeText(this, SurakshaLog.userMessage("Could not load ambulances", e), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.AMBULANCE, "read emergency for dispatch", e, "emergencyId=" + emergencyId);
                    clearBusy(emergencyId);
                });
    }

    /** Tries candidates nearest-first; a candidate taken concurrently is skipped. */
    private void tryDispatch(String emergencyId, List<AmbulanceSelection.AmbulanceCandidate> ranked, int index) {
        if (index >= ranked.size()) {
            clearBusy(emergencyId);
            Toast.makeText(this, "All nearby ambulances became busy. Try again.", Toast.LENGTH_LONG).show();
            return;
        }
        AmbulanceSelection.AmbulanceCandidate candidate = ranked.get(index);
        DocumentReference emergencyRef = db.collection("emergencies").document(emergencyId);
        DocumentReference ambulanceRef = db.collection("ambulances").document(candidate.ambulanceDocId);

        db.runTransaction(transaction -> {
            DocumentSnapshot emergency = transaction.get(emergencyRef);
            DocumentSnapshot ambulance = transaction.get(ambulanceRef);
            if (!emergency.exists()) throw abort("Emergency no longer exists.");
            if (!hospitalUid.equals(emergency.getString("hospitalId"))) throw abort("Emergency is not assigned to you.");
            if (!EmergencyStatus.needsDispatch(emergency.getString("status"))) throw abort("Ambulance already dispatched.");
            if (emergency.getString("ambulanceId") != null) throw abort("Ambulance already dispatched.");
            if (!ambulance.exists() || Boolean.TRUE.equals(ambulance.getBoolean("isOnTrip"))) {
                throw new FirebaseFirestoreException("AMBULANCE_BUSY", FirebaseFirestoreException.Code.CANCELLED);
            }

            Map<String, Object> e = new HashMap<>();
            e.put("ambulanceId", candidate.ambulanceDocId);
            e.put("ambulanceVehicle", candidate.vehicleNumber);
            e.put("driverName", candidate.driverName);
            e.put("driverPhone", candidate.driverPhone);
            e.put("ambulanceLatitude", candidate.latitude);
            e.put("ambulanceLongitude", candidate.longitude);
            e.put("ambulanceDistanceMeters", candidate.distanceMeters);
            e.put("status", EmergencyStatus.AMBULANCE_DISPATCHED);
            e.put("priority", EmergencyPriority.levelForStatus(EmergencyStatus.AMBULANCE_DISPATCHED));
            e.put("dispatchedAt", FieldValue.serverTimestamp());
            e.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(emergencyRef, e);

            Map<String, Object> a = new HashMap<>();
            a.put("isOnTrip", true);
            a.put("currentEmergencyId", emergencyId);
            transaction.update(ambulanceRef, a);
            return null;
        }).addOnSuccessListener(r -> {
            SurakshaLog.info(SurakshaLog.AMBULANCE, "ambulance dispatched", "emergencyId=" + emergencyId,
                    "hospitalId=" + hospitalUid, "ambulanceId=" + candidate.ambulanceDocId,
                    "status=" + EmergencyStatus.AMBULANCE_DISPATCHED);

            // Link driver location document immediately so patient can track the ambulance in real-time
            Map<String, Object> link = new HashMap<>();
            link.put("ambulanceId", candidate.ambulanceDocId);
            link.put("emergencyId", emergencyId);
            link.put("latitude", candidate.latitude);
            link.put("longitude", candidate.longitude);
            link.put("timestamp", FieldValue.serverTimestamp());
            db.collection("driverLocations").document(candidate.ambulanceDocId)
                    .set(link, SetOptions.merge())
                    .addOnFailureListener(err -> SurakshaLog.warn(SurakshaLog.AMBULANCE, "link driverLocation", "error=" + err.getMessage()));

            clearBusy(emergencyId);
            Toast.makeText(this, "Dispatched " + candidate.vehicleNumber + " ("
                    + HaversineDistance.formatDistance(candidate.distanceMeters) + " away)", Toast.LENGTH_LONG).show();

            emergencyRef.get().addOnSuccessListener(eDoc -> {
                Double pLat = FirestoreValues.getDouble(eDoc, "latitude");
                Double pLng = FirestoreValues.getDouble(eDoc, "longitude");
                Double hLat = getHospitalLatitude();
                Double hLng = getHospitalLongitude();
                if (!HaversineDistance.isValidCoordinate(hLat, hLng)) {
                    hLat = candidate.latitude;
                    hLng = candidate.longitude;
                }
                if (HaversineDistance.isValidCoordinate(hLat, hLng)
                        && HaversineDistance.isValidCoordinate(pLat, pLng)) {
                    AmbulanceRouteSimulator.start(this, candidate.ambulanceDocId, emergencyId,
                            hLat, hLng, pLat, pLng, EmergencyStatus.AMBULANCE_DISPATCHED);
                }
            });
        }).addOnFailureListener(e -> {
            if (e.getMessage() != null && e.getMessage().contains("AMBULANCE_BUSY")) {
                SurakshaLog.info(SurakshaLog.AMBULANCE, "candidate busy, trying next",
                        "emergencyId=" + emergencyId, "ambulanceId=" + candidate.ambulanceDocId);
                tryDispatch(emergencyId, ranked, index + 1);
                return;
            }
            SurakshaLog.error(SurakshaLog.AMBULANCE, "dispatch transaction", e, "emergencyId=" + emergencyId,
                    "ambulanceId=" + candidate.ambulanceDocId);
            clearBusy(emergencyId);
            Toast.makeText(this, e.getMessage() != null ? e.getMessage() : "Dispatch failed", Toast.LENGTH_LONG).show();
        });
    }

    private void manualStartTransport(DocumentSnapshot doc) {
        String emergencyId = doc.getId();
        String ambId = doc.getString("ambulanceId");
        if (ambId == null) return;
        Double pLat = FirestoreValues.getDouble(doc, "latitude");
        Double pLng = FirestoreValues.getDouble(doc, "longitude");
        Double hLat = getHospitalLatitude();
        Double hLng = getHospitalLongitude();
        if (!HaversineDistance.isValidCoordinate(hLat, hLng)) {
            hLat = FirestoreValues.getDouble(doc, "hospitalLatitude");
            hLng = FirestoreValues.getDouble(doc, "hospitalLongitude");
        }
        if (HaversineDistance.isValidCoordinate(hLat, hLng) && HaversineDistance.isValidCoordinate(pLat, pLng)) {
            AmbulanceRouteSimulator.start(this, ambId, emergencyId, hLat, hLng, pLat, pLng, EmergencyStatus.EN_ROUTE_TO_HOSPITAL);
            Toast.makeText(this, "Starting patient transport to hospital...", Toast.LENGTH_SHORT).show();
        }
    }

    private void manualCompleteEmergency(DocumentSnapshot doc) {
        String emergencyId = doc.getId();
        String ambId = doc.getString("ambulanceId");
        Map<String, Object> update = new HashMap<>();
        update.put("status", EmergencyStatus.COMPLETED);
        update.put("completedAt", FieldValue.serverTimestamp());
        update.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("emergencies").document(emergencyId).update(update);

        if (ambId != null) {
            Double hLat = getHospitalLatitude();
            Double hLng = getHospitalLongitude();
            Map<String, Object> ambUpdate = new HashMap<>();
            ambUpdate.put("isOnTrip", false);
            ambUpdate.put("currentEmergencyId", null);
            if (HaversineDistance.isValidCoordinate(hLat, hLng)) {
                ambUpdate.put("latitude", hLat);
                ambUpdate.put("longitude", hLng);
            }
            db.collection("ambulances").document(ambId).update(ambUpdate);

            Map<String, Object> loc = new HashMap<>();
            loc.put("emergencyId", null);
            db.collection("driverLocations").document(ambId).set(loc, SetOptions.merge());
            AmbulanceRouteSimulator.stop(ambId);
        }
        Toast.makeText(this, "Patient admitted! Emergency completed.", Toast.LENGTH_SHORT).show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, StartingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ------------------------------------------------------------------ adapters

    private class EmergencyAdapter extends RecyclerView.Adapter<EmergencyAdapter.ViewHolder> {
        private final List<DocumentSnapshot> list;

        EmergencyAdapter(List<DocumentSnapshot> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency_request, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = list.get(position);
            String status = doc.getString("status");
            String id = doc.getId();
            boolean busy = busyEmergencyIds.contains(id);

            String phone = doc.getString("userPhone");
            holder.tvPatientName.setText(nonEmpty(doc.getString("userName"), "Patient")
                    + (phone != null && !phone.isEmpty() ? "  •  " + phone : ""));
            Double lat = FirestoreValues.getDouble(doc, "latitude");
            Double lng = FirestoreValues.getDouble(doc, "longitude");
            holder.tvLocation.setText(getString(R.string.label_location_prefix)
                    + (lat != null && lng != null ? String.format(Locale.US, "%.5f, %.5f", lat, lng) : "Unknown"));
            holder.tvTimestamp.setText(EmergencyPriority.formatAge(doc));

            String level = EmergencyPriority.getPriorityLevel(doc);
            holder.tvPriority.setText(level);
            holder.tvEmergencyId.setText("ID: " + id + (doc.getString("bloodGroup") != null
                    ? "  |  Blood: " + doc.getString("bloodGroup") : ""));
            Double dist = FirestoreValues.getDouble(doc, "distanceToHospitalMeters");
            holder.tvDistance.setText(dist != null ? "Distance to hospital: " + HaversineDistance.formatDistance(dist) : "");
            String vehicle = doc.getString("ambulanceVehicle");
            holder.tvStatus.setText("Status: " + EmergencyStatus.label(status)
                    + (vehicle != null && doc.getString("ambulanceId") != null ? " (" + vehicle + ")" : ""));

            boolean requested = EmergencyStatus.HOSPITAL_REQUESTED.equals(status);
            holder.btnAccept.setVisibility(requested ? View.VISIBLE : View.GONE);
            holder.btnReject.setVisibility(requested ? View.VISIBLE : View.GONE);
            holder.btnAccept.setEnabled(!busy);
            holder.btnReject.setEnabled(!busy);

            boolean needsDispatch = EmergencyStatus.needsDispatch(status);
            boolean arrived = EmergencyStatus.AMBULANCE_ARRIVED.equals(status);
            boolean enRoute = EmergencyStatus.EN_ROUTE_TO_HOSPITAL.equals(status) || EmergencyStatus.PATIENT_PICKED_UP.equals(status);

            if (needsDispatch) {
                holder.btnDispatch.setVisibility(View.VISIBLE);
                holder.btnDispatch.setText("Dispatch ambulance");
                holder.btnDispatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E6FD9));
                holder.btnDispatch.setEnabled(!busy);
                holder.btnDispatch.setOnClickListener(v -> dispatchAmbulance(id));
            } else if (arrived) {
                holder.btnDispatch.setVisibility(View.VISIBLE);
                holder.btnDispatch.setText("Start Transport to Hospital");
                holder.btnDispatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE65100));
                holder.btnDispatch.setEnabled(!busy);
                holder.btnDispatch.setOnClickListener(v -> manualStartTransport(doc));
            } else if (enRoute) {
                holder.btnDispatch.setVisibility(View.VISIBLE);
                holder.btnDispatch.setText("Mark Reached & Admitted");
                holder.btnDispatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2E7D32));
                holder.btnDispatch.setEnabled(!busy);
                holder.btnDispatch.setOnClickListener(v -> manualCompleteEmergency(doc));
            } else {
                holder.btnDispatch.setVisibility(View.GONE);
            }

            holder.btnAccept.setOnClickListener(v -> acceptEmergency(doc));
            holder.btnReject.setOnClickListener(v -> rejectEmergency(doc));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvPatientName, tvLocation, tvTimestamp, tvPriority, tvEmergencyId, tvDistance, tvStatus;
            final Button btnAccept, btnReject, btnDispatch;

            ViewHolder(View v) {
                super(v);
                tvPatientName = v.findViewById(R.id.tvPatientName);
                tvLocation = v.findViewById(R.id.tvLocation);
                tvTimestamp = v.findViewById(R.id.tvTimestamp);
                tvPriority = v.findViewById(R.id.tvPriority);
                tvEmergencyId = v.findViewById(R.id.tvEmergencyId);
                tvDistance = v.findViewById(R.id.tvDistance);
                tvStatus = v.findViewById(R.id.tvRequestStatus);
                btnAccept = v.findViewById(R.id.btnAcceptRequest);
                btnReject = v.findViewById(R.id.btnRejectRequest);
                btnDispatch = v.findViewById(R.id.btnDispatchAmbulance);
            }
        }
    }

    private class AmbulanceAdapter extends RecyclerView.Adapter<AmbulanceAdapter.ViewHolder> {
        private final List<DocumentSnapshot> list;

        AmbulanceAdapter(List<DocumentSnapshot> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = list.get(position);
            String state = Boolean.TRUE.equals(doc.getBoolean("isOnTrip")) ? "ON TRIP"
                    : Boolean.TRUE.equals(doc.getBoolean("isOnline")) ? "AVAILABLE" : "OFFLINE";
            if (doc.contains("securityNumber")) state += " (legacy - re-add driver)";
            holder.text1.setText(nonEmpty(doc.getString("driverName"), "Driver") + " (" + doc.getString("vehicleNumber") + ")");
            holder.text2.setText("Phone: " + nonEmpty(doc.getString("driverPhone"), "--") + "  •  " + state);
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView text1, text2;

            ViewHolder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }

    private static String nonEmpty(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AmbulanceRouteSimulator.stopAll();
        if (fleetListener != null) { fleetListener.remove(); fleetListener = null; }
        if (emergencyListener != null) { emergencyListener.remove(); emergencyListener = null; }
    }
}
