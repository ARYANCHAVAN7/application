package com.example.application;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.style.layers.generated.CircleLayer;
import com.mapbox.maps.extension.style.layers.generated.LineLayer;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private CountDownTimer holdTimer;
    private final long holdDurationMs = 2000;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private ListenerRegistration emergencyTrackingListener;
    private ListenerRegistration ambulanceLocationListener;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private GeoJsonSource patientSource;
    private GeoJsonSource ambulanceSource;
    private GeoJsonSource routeSource;
    private TextView etaDisplay;
    private final ExecutorService routingExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private double patientLatitude;
    private double patientLongitude;
    private double ambulanceLatitude;
    private double ambulanceLongitude;
    private boolean hasPatientLocation;
    private boolean hasAmbulanceLocation;
    private double lastRoutedLatitude = Double.NaN;
    private double lastRoutedLongitude = Double.NaN;
    private long lastRouteRequestAt;
    private String trackedAmbulanceUid;
    private String userEmergencyPhone = "6491050867";
    private FrameLayout sosButton;
    private android.widget.LinearLayout adminPanelBtn;
    private boolean isSosTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mapView = findViewById(R.id.userMap);
        etaDisplay = findViewById(R.id.ambulanceEta);
        if (mapView != null) {
            mapboxMap = mapView.getMapboxMap();
            mapboxMap.loadStyle(Style.MAPBOX_STREETS, this::initializeTrackingSources);
        }

        sosButton = findViewById(R.id.sosButton);
        TextView callNowBtn = findViewById(R.id.callNowBtn);
        adminPanelBtn = findViewById(R.id.adminPanelBtn);

        sosButton.setOnTouchListener((view, event) -> {
            if (isSosTriggered) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startHoldCountdown();
                    startScalingAnimation();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelHoldCountdown();
                    stopScalingAnimation();
                    view.performClick();
                    return true;
                default:
                    return false;
            }
        });

        callNowBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + userEmergencyPhone));
            startActivity(intent);
        });

        findViewById(R.id.logoutBtn).setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(DashboardActivity.this, StartingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        if (adminPanelBtn != null) {
            adminPanelBtn.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AdminApprovalActivity.class);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
        } else {
            loadUserProfile();
            listenForActiveEmergency();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeTrackingListeners();
        if (mapView != null) mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        routingExecutor.shutdownNow();
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    private void loadUserProfile() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String role = documentSnapshot.getString("role");
                        if (role != null) role = role.trim();

                        if (!"user".equals(role) && !"admin".equals(role)) {
                            Toast.makeText(this, "Unauthorized access", Toast.LENGTH_SHORT).show();
                            redirectToLogin();
                            return;
                        }

                        ((TextView) findViewById(R.id.userNameDisplay)).setText(documentSnapshot.getString("fullName"));
                        ((TextView) findViewById(R.id.bloodGroupDisplay)).setText(documentSnapshot.getString("bloodGroup"));
                        ((TextView) findViewById(R.id.emergencyContactName)).setText(documentSnapshot.getString("emergencyName"));

                        userEmergencyPhone = documentSnapshot.getString("emergencyPhone");
                        ((TextView) findViewById(R.id.emergencyContactPhone)).setText(userEmergencyPhone);

                        if ("admin".equals(role) && adminPanelBtn != null) {
                            adminPanelBtn.setVisibility(android.view.View.VISIBLE);
                        }
                    }
                });
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, StartingActivity.class);
        startActivity(intent);
        finish();
    }

    private void initializeTrackingSources(Style style) {
        patientSource = new GeoJsonSource.Builder("patient-source").build();
        ambulanceSource = new GeoJsonSource.Builder("ambulance-source").build();
        routeSource = new GeoJsonSource.Builder("route-source").build();
        patientSource.bindTo(mapboxMap);
        ambulanceSource.bindTo(mapboxMap);
        routeSource.bindTo(mapboxMap);
        new CircleLayer("patient-layer", "patient-source")
                .circleColor("#D32F2F")
            .circleRadius(8.0)
            .bindTo(mapboxMap);
        new CircleLayer("ambulance-layer", "ambulance-source")
                .circleColor("#1976D2")
            .circleRadius(8.0)
            .bindTo(mapboxMap);
        new LineLayer("route-layer", "route-source")
                .lineColor("#1976D2")
            .lineWidth(5.0)
            .bindTo(mapboxMap);
    }

    private void listenForActiveEmergency() {
        removeTrackingListeners();
        if (mAuth.getCurrentUser() == null) return;

        emergencyTrackingListener = db.collection("emergencies")
                .whereEqualTo("userId", mAuth.getCurrentUser().getUid())
                .whereIn("status", java.util.Arrays.asList("accepted", "active"))
                .limit(1)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        showTrackingMessage("Unable to load emergency tracking.");
                        return;
                    }
                    if (snapshots == null || snapshots.isEmpty()) {
                        clearTracking();
                        return;
                    }

                    DocumentSnapshot emergency = snapshots.getDocuments().get(0);
                    Double latitude = emergency.getDouble("latitude");
                    Double longitude = emergency.getDouble("longitude");
                    if (!validCoordinate(latitude, longitude)) {
                        showTrackingMessage("Emergency location is unavailable.");
                        return;
                    }

                    patientLatitude = latitude;
                    patientLongitude = longitude;
                    hasPatientLocation = true;
                    updatePatientMarker();
                    subscribeToAmbulance(emergency.getString("ambulanceUid"));
                });
    }

    private void subscribeToAmbulance(String uid) {
        if (uid == null || uid.trim().isEmpty()) return;
        if (uid.equals(trackedAmbulanceUid) && ambulanceLocationListener != null) return;
        if (ambulanceLocationListener != null) ambulanceLocationListener.remove();
        trackedAmbulanceUid = uid;
        ambulanceLocationListener = db.collection("ambulances").document(uid)
                .addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) {
                        showTrackingMessage("Ambulance location is unavailable.");
                        return;
                    }
                    Double latitude = document.getDouble("latitude");
                    Double longitude = document.getDouble("longitude");
                    Boolean isOnTrip = document.getBoolean("isOnTrip");
                    if (!validCoordinate(latitude, longitude) || Boolean.FALSE.equals(isOnTrip)) {
                        showTrackingMessage("Ambulance location is temporarily unavailable.");
                        return;
                    }
                    ambulanceLatitude = latitude;
                    ambulanceLongitude = longitude;
                    hasAmbulanceLocation = true;
                    updateAmbulanceMarker();
                    requestRouteIfNeeded();
                });
    }

    private void updatePatientMarker() {
        if (patientSource == null || !hasPatientLocation) return;
        patientSource.feature(Feature.fromGeometry(Point.fromLngLat(patientLongitude, patientLatitude)));
        if (mapboxMap != null) {
            mapboxMap.setCamera(new com.mapbox.maps.CameraOptions.Builder()
                    .center(Point.fromLngLat(patientLongitude, patientLatitude)).zoom(12.0).build());
        }
    }

    private void updateAmbulanceMarker() {
        if (ambulanceSource == null || !hasAmbulanceLocation) return;
        ambulanceSource.feature(Feature.fromGeometry(Point.fromLngLat(ambulanceLongitude, ambulanceLatitude)));
    }

    private void requestRouteIfNeeded() {
        if (!hasPatientLocation || !hasAmbulanceLocation) return;
        float[] distance = new float[1];
        android.location.Location.distanceBetween(lastRoutedLatitude, lastRoutedLongitude,
                ambulanceLatitude, ambulanceLongitude, distance);
        long now = System.currentTimeMillis();
        if (!Double.isNaN(lastRoutedLatitude) && distance[0] < 50f && now - lastRouteRequestAt < 10000L) return;
        lastRoutedLatitude = ambulanceLatitude;
        lastRoutedLongitude = ambulanceLongitude;
        lastRouteRequestAt = now;

        routingExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String token = BuildConfig.MAPBOX_ACCESS_TOKEN;
                if (token == null || token.trim().isEmpty()) {
                    mainHandler.post(() -> showTrackingMessage("Mapbox routing is not configured."));
                    return;
                }
                String endpoint = "https://api.mapbox.com/directions/v5/mapbox/driving/"
                        + ambulanceLongitude + "," + ambulanceLatitude + ";"
                        + patientLongitude + "," + patientLatitude
                        + "?overview=full&geometries=geojson&access_token=" + Uri.encode(token);
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    mainHandler.post(() -> showTrackingMessage("Unable to calculate route."));
                    return;
                }
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject route = new JSONObject(body.toString()).getJSONArray("routes").getJSONObject(0);
                double durationSeconds = route.getDouble("duration");
                JSONArray coordinates = route.getJSONObject("geometry").getJSONArray("coordinates");
                List<Point> points = new ArrayList<>();
                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray coordinate = coordinates.getJSONArray(i);
                    points.add(Point.fromLngLat(coordinate.getDouble(0), coordinate.getDouble(1)));
                }
                mainHandler.post(() -> {
                    if (routeSource != null && !points.isEmpty()) {
                        routeSource.feature(Feature.fromGeometry(LineString.fromLngLats(points)));
                    }
                    if (etaDisplay != null) {
                        etaDisplay.setText(String.format(Locale.US, "ETA: %d min", Math.max(1, Math.round(durationSeconds / 60.0))));
                    }
                });
            } catch (Exception error) {
                mainHandler.post(() -> showTrackingMessage("Unable to calculate route."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private boolean validCoordinate(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    private void showTrackingMessage(String message) {
        if (etaDisplay != null) etaDisplay.setText(message);
    }

    private void clearTracking() {
        if (ambulanceLocationListener != null) {
            ambulanceLocationListener.remove();
            ambulanceLocationListener = null;
        }
        trackedAmbulanceUid = null;
        hasPatientLocation = false;
        hasAmbulanceLocation = false;
        lastRoutedLatitude = Double.NaN;
        showTrackingMessage("No active ambulance tracking.");
    }

    private void removeTrackingListeners() {
        if (emergencyTrackingListener != null) {
            emergencyTrackingListener.remove();
            emergencyTrackingListener = null;
        }
        if (ambulanceLocationListener != null) {
            ambulanceLocationListener.remove();
            ambulanceLocationListener = null;
        }
        trackedAmbulanceUid = null;
    }

    private void startHoldCountdown() {
        isSosTriggered = false;
        holdTimer = new CountDownTimer(holdDurationMs, holdDurationMs) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                isSosTriggered = true;
                triggerSos();
                stopScalingAnimation();
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
        scaleUp.setDuration(holdDurationMs);
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

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    private void triggerSos() {
        if (mAuth.getCurrentUser() == null) return;
        if (!hasLocationPermission()) {
            requestLocationPermission();
            Toast.makeText(this, "Location permission is required to send a real emergency SOS.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Turn on device location to send an emergency SOS.", Toast.LENGTH_LONG).show();
            return;
        }

        vibrate(500);

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        isSosTriggered = false;
                        Toast.makeText(this, "Unable to get your current location. Please try again.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String uid = mAuth.getCurrentUser().getUid();
                    Map<String, Object> emergency = new HashMap<>();
                    emergency.put("userId", uid);
                    emergency.put("userName", ((TextView) findViewById(R.id.userNameDisplay)).getText().toString());
                    emergency.put("status", "pending");
                    emergency.put("timestamp", FieldValue.serverTimestamp());
                    emergency.put("latitude", location.getLatitude());
                    emergency.put("longitude", location.getLongitude());
                    emergency.put("location", String.format(Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude()));
                    emergency.put("locationUpdatedAt", FieldValue.serverTimestamp());

                    db.collection("emergencies").add(emergency)
                            .addOnSuccessListener(documentReference -> {
                                Toast.makeText(this, "🚨 SOS SENT! Help is on the way.", Toast.LENGTH_LONG).show();
                            })
                            .addOnFailureListener(e -> {
                                isSosTriggered = false;
                                Toast.makeText(this, "Failed to send SOS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    isSosTriggered = false;
                    Log.e(TAG, "Failed to fetch current location", e);
                    Toast.makeText(this, "Failed to get current GPS location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
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

    private void vibrate(long duration) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(duration);
        }
    }
}
