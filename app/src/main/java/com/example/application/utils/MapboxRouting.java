package com.example.application.utils;

import android.content.Context;
import android.util.Log;

import com.example.application.BuildConfig;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Mapbox Routing/Directions Service
 * 
 * Uses Mapbox Directions API to calculate:
 * 1. Route geometry (path from ambulance to emergency)
 * 2. Route distance
 * 3. Route duration (for ETA calculation)
 * 
 * Requirements:
 * - Mapbox SDK v11.29.1 (already installed)
 * - Mapbox access token (configured in mapbox_access_token.xml)
 * - Valid GPS coordinates for origin and destination
 * 
 * Note: Uses BuildConfig.MAPBOX_ACCESS_TOKEN automatically.
 * REST API called asynchronously on background thread.
 */
public class MapboxRouting {

    private static final String TAG = "MapboxRouting";
    private static final String DIRECTIONS_API_URL = 
        "https://api.mapbox.com/directions/v5/mapbox/driving";

    /**
     * Represents a calculated route
     */
    public static class RouteInfo {
        public double distanceMeters;        // Total route distance in meters
        public double distanceKilometers;    // Total route distance in km
        public long durationSeconds;         // Total route duration in seconds
        public String durationFormatted;     // Formatted duration (e.g., "12 min 30 sec")
        public LineString geometry;          // Route geometry (polyline)
        public Point origin;                 // Start point
        public Point destination;            // End point

        @Override
        public String toString() {
            return String.format(
                "Route: %s, Duration: %s, Distance: %.1f km",
                durationFormatted,
                String.format(java.util.Locale.US, "%.1f km", distanceKilometers)
            );
        }
    }

    /**
     * Callback interface for route results
     */
    public interface RouteCallback {
        /**
         * Called when route is successfully calculated
         */
        void onRouteSuccess(RouteInfo route);

        /**
         * Called if route calculation fails
         */
        void onRouteFailure(String errorMessage);
    }

    /**
     * Calculate route between ambulance and emergency
     * 
     * This method:
     * 1. Validates both coordinates
     * 2. Makes async API call to Mapbox Directions API on background thread
     * 3. Returns route via callback (not blocking)
     * 
     * @param context Android context
     * @param ambulanceLatitude Ambulance current latitude
     * @param ambulanceLongitude Ambulance current longitude
     * @param emergencyLatitude Emergency location latitude
     * @param emergencyLongitude Emergency location longitude
     * @param callback Callback for result
     */
    public static void calculateRoute(Context context,
                                     double ambulanceLatitude, double ambulanceLongitude,
                                     double emergencyLatitude, double emergencyLongitude,
                                     RouteCallback callback) {
        
        // Validate coordinates
        if (!HaversineDistance.isValidCoordinate(ambulanceLatitude, ambulanceLongitude) ||
            !HaversineDistance.isValidCoordinate(emergencyLatitude, emergencyLongitude)) {
            if (callback != null) {
                callback.onRouteFailure("Invalid coordinates provided");
            }
            return;
        }

        // Create origin and destination points
        Point origin = Point.fromLngLat(ambulanceLongitude, ambulanceLatitude);
        Point destination = Point.fromLngLat(emergencyLongitude, emergencyLatitude);

        // Make API call on background thread
        new Thread(() -> {
            try {
                String accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN;
                if (accessToken == null || accessToken.trim().isEmpty()) {
                    if (callback != null) {
                        callback.onRouteFailure("Mapbox access token not configured");
                    }
                    return;
                }

                // Build API URL
                String url = String.format("%s/%f,%f;%f,%f?overview=full&access_token=%s",
                    DIRECTIONS_API_URL,
                    ambulanceLongitude, ambulanceLatitude,
                    emergencyLongitude, emergencyLatitude,
                    accessToken);

                // Make HTTP request
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                    .url(url)
                    .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    if (callback != null) {
                        callback.onRouteFailure("API returned error: " + response.code());
                    }
                    return;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject json = new JSONObject(responseBody);

                // Parse response
                if (!json.has("routes") || json.getJSONArray("routes").length() == 0) {
                    if (callback != null) {
                        callback.onRouteFailure("No route found");
                    }
                    return;
                }

                JSONObject route = json.getJSONArray("routes").getJSONObject(0);
                RouteInfo routeInfo = new RouteInfo();
                
                routeInfo.distanceMeters = route.getDouble("distance");
                routeInfo.distanceKilometers = routeInfo.distanceMeters / 1000.0;
                routeInfo.durationSeconds = (long) route.getDouble("duration");
                routeInfo.durationFormatted = formatDuration(routeInfo.durationSeconds);
                
                // Parse geometry (polyline6 format)
                String geometry = route.getString("geometry");
                routeInfo.geometry = LineString.fromPolyline(geometry, 6);
                
                routeInfo.origin = origin;
                routeInfo.destination = destination;

                Log.d(TAG, "Route calculated: " + routeInfo);

                if (callback != null) {
                    callback.onRouteSuccess(routeInfo);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error calculating route", e);
                if (callback != null) {
                    callback.onRouteFailure("Error: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Calculate ETA (Estimated Time of Arrival)
     * 
     * @param currentTimeMs Current system time in milliseconds
     * @param routeDurationSeconds Route duration from Mapbox Directions API
     * @return ETA as System.currentTimeMillis() value
     */
    public static long calculateETA(long currentTimeMs, long routeDurationSeconds) {
        return currentTimeMs + (routeDurationSeconds * 1000);
    }

    /**
     * Format route duration for display
     * 
     * @param durationSeconds Duration in seconds
     * @return Formatted string (e.g., "12 min 30 sec", "5 min")
     */
    public static String formatDuration(long durationSeconds) {
        long minutes = durationSeconds / 60;
        long seconds = durationSeconds % 60;

        if (minutes > 0 && seconds > 0) {
            return String.format("%d min %d sec", minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d min", minutes);
        } else {
            return String.format("%d sec", seconds);
        }
    }

    /**
     * Format ETA for display
     * 
     * @param etaTimeMs ETA as milliseconds since epoch
     * @return Formatted string (e.g., "2:45 PM")
     */
    public static String formatETA(long etaTimeMs) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US);
        return sdf.format(new java.util.Date(etaTimeMs));
    }

    /**
     * Get human-readable ETA display string
     * 
     * @param durationSeconds Route duration from API
     * @return String like "Arriving in approximately 12 min"
     */
    public static String getETADescription(long durationSeconds) {
        return "Arriving in approximately " + formatDuration(durationSeconds);
    }
}
