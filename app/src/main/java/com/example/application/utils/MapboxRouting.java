package com.example.application.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.application.BuildConfig;
import com.example.application.R;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Mapbox Directions API client.
 *
 * Fixes vs. the previous version:
 * - callbacks are always delivered on the Android main thread (UI updates were crashing);
 * - RouteInfo.toString() had 3 format specifiers and 2 arguments, which threw inside the
 *   success path so every route was reported as a failure;
 * - geometry is requested as polyline6 to match the precision used for decoding;
 * - coordinates are formatted with Locale.US; the access token is never logged;
 * - one shared OkHttpClient; responses are closed.
 */
public class MapboxRouting {

    private static final String DIRECTIONS_API_URL =
            "https://api.mapbox.com/directions/v5/mapbox/driving/";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public static class RouteInfo {
        public double distanceMeters;
        public double distanceKilometers;
        public long durationSeconds;
        public String durationFormatted;
        public LineString geometry;
        public Point origin;
        public Point destination;

        @Override
        public String toString() {
            return String.format(Locale.US, "Route: %s, Distance: %.1f km",
                    durationFormatted, distanceKilometers);
        }
    }

    /** Both methods are invoked on the main thread. */
    public interface RouteCallback {
        void onRouteSuccess(RouteInfo route);

        void onRouteFailure(String errorMessage);
    }

    public static void calculateRoute(Context context,
                                      double originLatitude, double originLongitude,
                                      double destinationLatitude, double destinationLongitude,
                                      RouteCallback callback) {
        if (!HaversineDistance.isValidCoordinate(originLatitude, originLongitude) ||
                !HaversineDistance.isValidCoordinate(destinationLatitude, destinationLongitude)) {
            postFailure(callback, "Invalid coordinates provided");
            return;
        }

        final Point origin = Point.fromLngLat(originLongitude, originLatitude);
        final Point destination = Point.fromLngLat(destinationLongitude, destinationLatitude);

        new Thread(() -> {
            String accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN;
            if (accessToken == null || accessToken.trim().isEmpty()) {
                try {
                    accessToken = context.getString(R.string.mapbox_access_token);
                } catch (Exception ignored) {}
            }
            if (accessToken == null || accessToken.trim().isEmpty()) {
                postFailure(callback, "Mapbox access token not configured (MAPBOX_ACCESS_TOKEN in local.properties)");
                return;
            }

            String coordinates = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f",
                    originLongitude, originLatitude, destinationLongitude, destinationLatitude);
            HttpUrl base = HttpUrl.parse(DIRECTIONS_API_URL + coordinates);
            if (base == null) {
                postFailure(callback, "Could not build Directions URL");
                return;
            }
            HttpUrl url = base.newBuilder()
                    .addQueryParameter("overview", "full")
                    .addQueryParameter("geometries", "polyline6")
                    .addQueryParameter("access_token", accessToken)
                    .build();

            Request request = new Request.Builder().url(url).build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    SurakshaLog.warn(SurakshaLog.MAP, "directions http error", "code=" + response.code());
                    postFailure(callback, "Directions API returned HTTP " + response.code());
                    return;
                }
                String body = response.body() != null ? response.body().string() : "";
                JSONObject json = new JSONObject(body);
                if (!json.has("routes") || json.getJSONArray("routes").length() == 0) {
                    postFailure(callback, "No route found");
                    return;
                }
                JSONObject route = json.getJSONArray("routes").getJSONObject(0);
                RouteInfo info = new RouteInfo();
                info.distanceMeters = route.getDouble("distance");
                info.distanceKilometers = info.distanceMeters / 1000.0;
                info.durationSeconds = (long) route.getDouble("duration");
                info.durationFormatted = formatDuration(info.durationSeconds);
                info.geometry = LineString.fromPolyline(route.getString("geometry"), 6);
                info.origin = origin;
                info.destination = destination;

                SurakshaLog.info(SurakshaLog.MAP, "route calculated", info.toString());
                MAIN.post(() -> {
                    if (callback != null) callback.onRouteSuccess(info);
                });
            } catch (Exception e) {
                SurakshaLog.error(SurakshaLog.MAP, "directions request", e);
                postFailure(callback, "Route error: " + e.getClass().getSimpleName());
            }
        }, "suraksha-directions").start();
    }

    private static void postFailure(RouteCallback callback, String message) {
        MAIN.post(() -> {
            if (callback != null) callback.onRouteFailure(message);
        });
    }

    public static long calculateETA(long currentTimeMs, long routeDurationSeconds) {
        return currentTimeMs + (routeDurationSeconds * 1000);
    }

    public static String formatDuration(long durationSeconds) {
        long minutes = durationSeconds / 60;
        long seconds = durationSeconds % 60;
        if (minutes > 0 && seconds > 0) {
            return String.format(Locale.US, "%d min %d sec", minutes, seconds);
        } else if (minutes > 0) {
            return String.format(Locale.US, "%d min", minutes);
        }
        return String.format(Locale.US, "%d sec", seconds);
    }

    public static String formatETA(long etaTimeMs) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", Locale.US);
        return sdf.format(new java.util.Date(etaTimeMs));
    }

    public static String getETADescription(long durationSeconds) {
        return "Arriving in approximately " + formatDuration(durationSeconds);
    }

    /** "3.4 km | ETA 7 min (2:45 PM)" */
    public static String formatDistanceAndEta(RouteInfo route) {
        return String.format(Locale.US, "%.1f km | ETA %s (%s)",
                route.distanceKilometers,
                formatDuration(route.durationSeconds),
                formatETA(calculateETA(System.currentTimeMillis(), route.durationSeconds)));
    }
}
