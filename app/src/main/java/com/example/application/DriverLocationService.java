package com.example.application;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.application.utils.GPSSmoothing;
import com.example.application.utils.SurakshaLog;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * The ONLY driver location tracker. Runs as a foreground service (type "location") from
 * driver login / going on duty until logout / going off duty, so tracking continues while
 * the driver uses an external navigation app.
 *
 * Each accepted fix (GPSSmoothing moving average) is written in one batch to:
 *   driverLocations/{ambulanceId}  {ambulanceId, latitude, longitude, accuracy, timestamp}
 *   ambulances/{ambulanceId}       {latitude, longitude, lastUpdatedAt, isOnline}
 * The emergencyId field of driverLocations is managed only by driver accept/complete
 * transactions, never by this service.
 */
public class DriverLocationService extends Service {

    public static final String EXTRA_AMBULANCE_ID = "ambulanceId";
    private static final String ACTION_STOP = "com.example.application.action.STOP_DRIVER_TRACKING";
    private static final String CHANNEL_ID = "suraksha_driver_tracking";
    private static final int NOTIFICATION_ID = 4101;
    private static final long INTERVAL_MS = 5_000L;
    private static final float MIN_DISTANCE_M = 5f;

    private FusedLocationProviderClient fusedClient;
    private LocationCallback callback;
    private GPSSmoothing.MovingAverageFilter filter;
    private String ambulanceId;

    public static void start(Context context, String ambulanceId) {
        Intent intent = new Intent(context, DriverLocationService.class);
        intent.putExtra(EXTRA_AMBULANCE_ID, ambulanceId);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, DriverLocationService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }
        String requested = intent != null ? intent.getStringExtra(EXTRA_AMBULANCE_ID) : null;
        if (requested == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat();
        if (callback != null && requested.equals(ambulanceId)) {
            return START_REDELIVER_INTENT; // already tracking this ambulance: no duplicate callback
        }
        stopTracking();
        ambulanceId = requested;
        startTracking();
        return START_REDELIVER_INTENT;
    }

    private void startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Ambulance live location",
                        NotificationManager.IMPORTANCE_LOW));
            }
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Suraksha ambulance on duty")
                .setContentText("Sharing live location with dispatch")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @SuppressWarnings("deprecation")
    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private void startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            SurakshaLog.warn(SurakshaLog.DRIVER, "location permission missing; service stopping", "ambulanceId=" + ambulanceId);
            stopForegroundCompat();
            stopSelf();
            return;
        }
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        filter = new GPSSmoothing.MovingAverageFilter(3);
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
                .build();
        callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location raw = result.getLastLocation();
                if (raw == null || !GPSSmoothing.isValidGPSReading(raw)) return;
                Location smoothed = filter.addLocation(raw);
                // Use the raw fix until the smoothing buffer is full so the first update is immediate.
                publish(smoothed != null ? smoothed : raw);
            }
        };
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper());
            fusedClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null && GPSSmoothing.isValidGPSReading(loc)) {
                    publish(loc);
                }
            });
            SurakshaLog.info(SurakshaLog.DRIVER, "location tracking started", "ambulanceId=" + ambulanceId);
        } catch (SecurityException e) {
            SurakshaLog.error(SurakshaLog.DRIVER, "requestLocationUpdates", e, "ambulanceId=" + ambulanceId);
            callback = null;
            stopSelf();
        }
    }

    private void publish(Location location) {
        if (ambulanceId == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference locRef = db.collection("driverLocations").document(ambulanceId);
        DocumentReference ambRef = db.collection("ambulances").document(ambulanceId);

        Map<String, Object> loc = new HashMap<>();
        loc.put("ambulanceId", ambulanceId);
        loc.put("latitude", location.getLatitude());
        loc.put("longitude", location.getLongitude());
        loc.put("accuracy", (double) location.getAccuracy());
        loc.put("timestamp", FieldValue.serverTimestamp());

        Map<String, Object> amb = new HashMap<>();
        amb.put("latitude", location.getLatitude());
        amb.put("longitude", location.getLongitude());
        amb.put("lastUpdatedAt", FieldValue.serverTimestamp());
        amb.put("isOnline", true);

        WriteBatch batch = db.batch();
        batch.set(locRef, loc, SetOptions.merge());
        batch.update(ambRef, amb);
        batch.commit().addOnFailureListener(e ->
                SurakshaLog.error(SurakshaLog.DRIVER, "publish location", e, "ambulanceId=" + ambulanceId));
    }

    private void stopTracking() {
        if (fusedClient != null && callback != null) {
            fusedClient.removeLocationUpdates(callback);
            SurakshaLog.info(SurakshaLog.DRIVER, "location tracking stopped", "ambulanceId=" + ambulanceId);
        }
        callback = null;
    }

    @Override
    public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
