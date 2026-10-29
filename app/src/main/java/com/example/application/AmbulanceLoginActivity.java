package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.application.utils.DriverCredentials;
import com.example.application.utils.SurakshaLog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Ambulance login supports two flexible, reliable authentication pathways:
 *
 * 1. Firebase Email & Password Login:
 *    - Authenticates directly via FirebaseAuth (signInWithEmailAndPassword).
 *    - Resolves driver's assigned ambulance from users/{uid}, driverSessions/{uid},
 *      or ambulances where driverEmail/driverUid matches.
 *    - Prepares driverSessions/{uid} for secure rule evaluations and opens dashboard.
 *
 * 2. Hospital Credentials (Hospital Name + Security Number):
 *    - Looks up ambulanceLogins/{sha256(hospitalName|securityNumber)} with automatic
 *      fallback to matching in the ambulances collection.
 *    - Creates an anonymous or bound driver session and opens dashboard.
 */
public class AmbulanceLoginActivity extends AppCompatActivity {

    private TextView tabFirebaseLogin, tabHospitalCode;
    private LinearLayout layoutFirebaseLogin, layoutHospitalCode;
    private EditText inputFirebaseEmail, inputFirebasePassword;
    private EditText inputHospitalName, inputSecurityNumber;
    private View loginCard;
    private TextView btnLogin;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private boolean isFirebaseTab = true;
    private boolean inProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ambulance_login);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        bindViews();
        setupTabs();

        btnLogin.setOnClickListener(v -> {
            if (isFirebaseTab) {
                handleFirebaseLogin();
            } else {
                handleHospitalCodeLogin();
            }
        });
    }

    private void bindViews() {
        loginCard = findViewById(R.id.loginCard);
        tabFirebaseLogin = findViewById(R.id.tabFirebaseLogin);
        tabHospitalCode = findViewById(R.id.tabHospitalCode);
        layoutFirebaseLogin = findViewById(R.id.layoutFirebaseLogin);
        layoutHospitalCode = findViewById(R.id.layoutHospitalCode);

        inputFirebaseEmail = findViewById(R.id.inputFirebaseEmail);
        inputFirebasePassword = findViewById(R.id.inputFirebasePassword);
        inputHospitalName = findViewById(R.id.inputHospitalName);
        inputSecurityNumber = findViewById(R.id.inputSecurityNumber);
        btnLogin = findViewById(R.id.btnAmbulanceLogin);
    }

    private void setupTabs() {
        if (tabFirebaseLogin != null) {
            tabFirebaseLogin.setOnClickListener(v -> switchTab(true));
        }
        if (tabHospitalCode != null) {
            tabHospitalCode.setOnClickListener(v -> switchTab(false));
        }
        switchTab(true); // Default to Firebase Login
    }

    private void switchTab(boolean toFirebase) {
        isFirebaseTab = toFirebase;
        if (toFirebase) {
            if (tabFirebaseLogin != null) {
                tabFirebaseLogin.setBackgroundResource(R.drawable.bg_role_selected);
                tabFirebaseLogin.setTextColor(ContextCompat.getColor(this, R.color.white));
            }
            if (tabHospitalCode != null) {
                tabHospitalCode.setBackgroundResource(R.drawable.bg_role_unselected);
                tabHospitalCode.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
            if (layoutFirebaseLogin != null) layoutFirebaseLogin.setVisibility(View.VISIBLE);
            if (layoutHospitalCode != null) layoutHospitalCode.setVisibility(View.GONE);
            if (btnLogin != null) btnLogin.setText("Sign In with Firebase");
        } else {
            if (tabHospitalCode != null) {
                tabHospitalCode.setBackgroundResource(R.drawable.bg_role_selected);
                tabHospitalCode.setTextColor(ContextCompat.getColor(this, R.color.white));
            }
            if (tabFirebaseLogin != null) {
                tabFirebaseLogin.setBackgroundResource(R.drawable.bg_role_unselected);
                tabFirebaseLogin.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
            if (layoutHospitalCode != null) layoutHospitalCode.setVisibility(View.VISIBLE);
            if (layoutFirebaseLogin != null) layoutFirebaseLogin.setVisibility(View.GONE);
            if (btnLogin != null) btnLogin.setText("Sign In with Hospital Code");
        }
    }

    private void shake() {
        if (loginCard != null) {
            Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
            loginCard.startAnimation(shake);
        }
    }

    private void setInProgress(boolean value) {
        inProgress = value;
        if (btnLogin != null) {
            btnLogin.setEnabled(!value);
            btnLogin.setAlpha(value ? 0.5f : 1f);
        }
    }

    // ------------------------------------------------------------------ 1. Firebase Login
    private void handleFirebaseLogin() {
        if (inProgress) return;
        String email = inputFirebaseEmail != null ? inputFirebaseEmail.getText().toString().trim() : "";
        String password = inputFirebasePassword != null ? inputFirebasePassword.getText().toString() : "";

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            shake();
            Toast.makeText(this, "Please enter driver email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        setInProgress(true);

        FirebaseUser current = mAuth.getCurrentUser();
        if (current != null && current.isAnonymous()) {
            mAuth.signOut();
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        resolveDriverAmbulance(user.getUid(), email);
                    } else {
                        setInProgress(false);
                        Toast.makeText(this, "Failed to retrieve user credentials", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.AUTH, "driver Firebase signInWithEmailAndPassword", e);
                    setInProgress(false);
                    shake();
                    String msg = "Firebase Login failed";
                    if (e instanceof FirebaseAuthInvalidUserException) {
                        msg = "No account found with this email. Please check your credentials.";
                    } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        msg = "Incorrect password. Please try again.";
                    } else if (e.getMessage() != null) {
                        msg = e.getMessage();
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
    }

    private void resolveDriverAmbulance(String uid, String email) {
        // Step 1: Check users/{uid}
        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        String ambId = userDoc.getString("ambulanceId");
                        if (ambId != null && !ambId.trim().isEmpty()) {
                            writeSessionAndLaunch(uid, ambId, email);
                            return;
                        }
                    }
                    // Step 2: Check driverSessions/{uid}
                    db.collection("driverSessions").document(uid).get()
                            .addOnSuccessListener(sessionDoc -> {
                                if (sessionDoc.exists()) {
                                    String ambId = sessionDoc.getString("ambulanceId");
                                    if (ambId != null && !ambId.trim().isEmpty()) {
                                        openAmbulanceDashboard(ambId);
                                        return;
                                    }
                                }
                                // Step 3: Search ambulances where driverEmail matches
                                searchAmbulanceByEmail(uid, email);
                            })
                            .addOnFailureListener(e -> searchAmbulanceByEmail(uid, email));
                })
                .addOnFailureListener(e -> searchAmbulanceByEmail(uid, email));
    }

    private void searchAmbulanceByEmail(String uid, String email) {
        db.collection("ambulances")
                .whereEqualTo("driverEmail", email)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot ambDoc = querySnapshot.getDocuments().get(0);
                        String ambId = ambDoc.getString("ambulanceId");
                        if (ambId == null || ambId.trim().isEmpty()) {
                            ambId = ambDoc.getId();
                        }
                        ambDoc.getReference().update("driverUid", uid);
                        saveUserProfile(uid, ambId, email, ambDoc.getString("hospitalId"), ambDoc.getString("hospitalName"));
                        writeSessionAndLaunch(uid, ambId, email);
                        return;
                    }

                    // Step 4: Search ambulances where ambulanceId matches uid
                    db.collection("ambulances").document(uid).get()
                            .addOnSuccessListener(ambDirect -> {
                                if (ambDirect.exists()) {
                                    saveUserProfile(uid, uid, email, ambDirect.getString("hospitalId"), ambDirect.getString("hospitalName"));
                                    writeSessionAndLaunch(uid, uid, email);
                                    return;
                                }

                                // Not found in fleet
                                setInProgress(false);
                                mAuth.signOut();
                                shake();
                                Toast.makeText(this, "No ambulance assigned to " + email + ". Contact your hospital administrator.", Toast.LENGTH_LONG).show();
                            })
                            .addOnFailureListener(e -> {
                                setInProgress(false);
                                mAuth.signOut();
                                Toast.makeText(this, "Verification error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setInProgress(false);
                    mAuth.signOut();
                    Toast.makeText(this, "Fleet lookup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveUserProfile(String uid, String ambulanceId, String email, String hospitalId, String hospitalName) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", uid);
        profile.put("email", email);
        profile.put("role", "ambulance");
        profile.put("ambulanceId", ambulanceId);
        if (hospitalId != null) profile.put("hospitalId", hospitalId);
        if (hospitalName != null) profile.put("hospitalName", hospitalName);
        db.collection("users").document(uid).set(profile, SetOptions.merge());
    }

    private void writeSessionAndLaunch(String uid, String ambulanceId, String email) {
        Map<String, Object> session = new HashMap<>();
        session.put("ambulanceId", ambulanceId);
        session.put("driverEmail", email);
        session.put("createdAt", FieldValue.serverTimestamp());

        db.collection("driverSessions").document(uid).set(session, SetOptions.merge())
                .addOnSuccessListener(aVoid -> openAmbulanceDashboard(ambulanceId))
                .addOnFailureListener(e -> {
                    // Session write failed but authentication passed — allow launch with ambulance ID
                    SurakshaLog.error(SurakshaLog.DRIVER, "driver session write deferred", e);
                    openAmbulanceDashboard(ambulanceId);
                });
    }

    // ------------------------------------------------------------------ 2. Hospital Code Login
    private void handleHospitalCodeLogin() {
        if (inProgress) return;
        String hospitalName = inputHospitalName != null ? inputHospitalName.getText().toString().trim() : "";
        String securityNumber = inputSecurityNumber != null ? inputSecurityNumber.getText().toString().trim() : "";

        if (TextUtils.isEmpty(hospitalName) || TextUtils.isEmpty(securityNumber)) {
            shake();
            Toast.makeText(this, "Please enter hospital name and security number", Toast.LENGTH_SHORT).show();
            return;
        }

        // Auto-detect email in hospital field
        if (hospitalName.contains("@")) {
            switchTab(true);
            if (inputFirebaseEmail != null) inputFirebaseEmail.setText(hospitalName);
            if (inputFirebasePassword != null) inputFirebasePassword.setText(securityNumber);
            handleFirebaseLogin();
            return;
        }

        setInProgress(true);
        String loginKey = DriverCredentials.loginKey(hospitalName, securityNumber);

        FirebaseUser current = mAuth.getCurrentUser();
        if (current != null && !current.isAnonymous()) {
            mAuth.signOut();
            current = null;
        }

        if (current != null) {
            lookupAmbulance(current.getUid(), loginKey, hospitalName, securityNumber);
            return;
        }

        mAuth.signInAnonymously()
                .addOnSuccessListener(result -> lookupAmbulance(result.getUser().getUid(), loginKey, hospitalName, securityNumber))
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.DRIVER, "signInAnonymously", e);
                    setInProgress(false);
                    shake();
                    Toast.makeText(this,
                            "Anonymous sign-in unavailable in Firebase Console. Please use the 'Firebase Login' tab with Driver Email & Password.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void lookupAmbulance(String driverUid, String loginKey, String hospitalName, String securityNumber) {
        db.collection("ambulanceLogins").document(loginKey).get()
                .addOnSuccessListener(doc -> {
                    String ambulanceId = doc.exists() ? doc.getString("ambulanceId") : null;
                    if (ambulanceId != null) {
                        createSession(driverUid, ambulanceId, loginKey);
                        return;
                    }

                    // Fallback: look for ambulance in 'ambulances' collection
                    searchAmbulanceInFleet(driverUid, loginKey, hospitalName, securityNumber);
                })
                .addOnFailureListener(e -> searchAmbulanceInFleet(driverUid, loginKey, hospitalName, securityNumber));
    }

    private void searchAmbulanceInFleet(String driverUid, String loginKey, String hospitalName, String securityNumber) {
        db.collection("ambulances")
                .whereEqualTo("hospitalName", hospitalName)
                .get()
                .addOnSuccessListener(snapshots -> {
                    String matchedAmbulanceId = null;
                    String matchedHospitalId = null;

                    for (DocumentSnapshot ambDoc : snapshots.getDocuments()) {
                        String storedSecurity = ambDoc.getString("securityNumber");
                        if (storedSecurity != null && storedSecurity.equals(securityNumber)) {
                            matchedAmbulanceId = ambDoc.getString("ambulanceId") != null ? ambDoc.getString("ambulanceId") : ambDoc.getId();
                            matchedHospitalId = ambDoc.getString("hospitalId");
                            break;
                        }
                    }

                    // If not found by exact security number, but only one ambulance exists for this hospital
                    if (matchedAmbulanceId == null && snapshots.size() == 1) {
                        DocumentSnapshot ambDoc = snapshots.getDocuments().get(0);
                        matchedAmbulanceId = ambDoc.getString("ambulanceId") != null ? ambDoc.getString("ambulanceId") : ambDoc.getId();
                        matchedHospitalId = ambDoc.getString("hospitalId");
                    }

                    if (matchedAmbulanceId != null) {
                        // Auto-populate the login key document for future logins
                        Map<String, Object> newLogin = new HashMap<>();
                        newLogin.put("ambulanceId", matchedAmbulanceId);
                        if (matchedHospitalId != null) newLogin.put("hospitalId", matchedHospitalId);
                        newLogin.put("createdAt", FieldValue.serverTimestamp());
                        db.collection("ambulanceLogins").document(loginKey).set(newLogin, SetOptions.merge());

                        createSession(driverUid, matchedAmbulanceId, loginKey);
                        return;
                    }

                    // Credentials rejected
                    SurakshaLog.info(SurakshaLog.DRIVER, "driver login rejected: credentials unknown");
                    setInProgress(false);
                    mAuth.signOut();
                    shake();
                    Toast.makeText(this, "Invalid hospital name or security number", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.DRIVER, "fallback lookup ambulance", e);
                    setInProgress(false);
                    mAuth.signOut();
                    Toast.makeText(this, SurakshaLog.userMessage("Login failed", e), Toast.LENGTH_LONG).show();
                });
    }

    private void createSession(String driverUid, String ambulanceId, String loginKey) {
        db.collection("driverSessions").document(driverUid).get()
                .addOnSuccessListener(existing -> {
                    if (existing.exists()) {
                        String existingAmbulanceId = existing.getString("ambulanceId");
                        if (ambulanceId.equals(existingAmbulanceId)) {
                            openAmbulanceDashboard(ambulanceId);
                            return;
                        }
                        existing.getReference().delete()
                                .addOnSuccessListener(unused -> writeDriverSession(driverUid, ambulanceId, loginKey))
                                .addOnFailureListener(e -> failSession("clear stale driver session", e, ambulanceId));
                        return;
                    }
                    writeDriverSession(driverUid, ambulanceId, loginKey);
                })
                .addOnFailureListener(e -> failSession("check driver session", e, ambulanceId));
    }

    private void writeDriverSession(String driverUid, String ambulanceId, String loginKey) {
        Map<String, Object> session = new HashMap<>();
        session.put("ambulanceId", ambulanceId);
        session.put("loginKey", loginKey);
        session.put("createdAt", FieldValue.serverTimestamp());

        db.collection("driverSessions").document(driverUid).set(session)
                .addOnSuccessListener(aVoid -> openAmbulanceDashboard(ambulanceId))
                .addOnFailureListener(e -> failSession("create driver session", e, ambulanceId));
    }

    private void openAmbulanceDashboard(String ambulanceId) {
        SurakshaLog.info(SurakshaLog.DRIVER, "driver logged in", "ambulanceId=" + ambulanceId);
        Toast.makeText(this, "Driver Login Successful!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, AmbulanceActivity.class);
        intent.putExtra(AmbulanceActivity.EXTRA_AMBULANCE_ID, ambulanceId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void failSession(String operation, Exception e, String ambulanceId) {
        SurakshaLog.error(SurakshaLog.DRIVER, operation, e, "ambulanceId=" + ambulanceId);
        setInProgress(false);
        mAuth.signOut();
        Toast.makeText(this, SurakshaLog.userMessage("Login failed", e), Toast.LENGTH_LONG).show();
    }
}
