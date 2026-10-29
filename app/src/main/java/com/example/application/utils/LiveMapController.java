package com.example.application.utils;

import android.content.Context;

import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;

/**
 * Shared live-map logic for the user map and the ambulance map:
 * markers (user / ambulance / hospital), route line, distance + ETA.
 *
 * Route origin is always the ambulance. Destination is the user until pickup, then the
 * hospital. Directions requests are throttled (one in flight, at most every 15 s unless the
 * destination changes). Everything here runs on the main thread; MapboxRouting delivers its
 * callbacks on the main thread.
 */
public class LiveMapController {

    public interface Listener {
        /** distanceAndEta e.g. "3.4 km | ETA 7 min (2:45 PM)" */
        void onRouteInfo(String distanceAndEta, boolean toHospital);

        void onRouteUnavailable(String fallbackText);
    }

    private static final long ROUTE_REFRESH_MS = 8_000L;

    private final Context context;
    private final Listener listener;
    private MapboxMap map;
    private MapOverlay overlay;
    private boolean destroyed = false;
    private boolean styleLoaded = false;

    private Point user;
    private Point ambulance;
    private Point hospital;
    private boolean toHospital;

    private boolean routeInFlight = false;
    private long lastRouteAt = 0L;
    private String lastRouteTarget = null;
    private String cameraTarget = null;
    private Point lastCameraAmbulancePoint = null;

    public LiveMapController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void attach(MapView mapView) {
        if (mapView == null) return;
        map = mapView.getMapboxMap();
        map.loadStyle(Style.MAPBOX_STREETS, style -> {
            if (destroyed) return;
            try {
                overlay = new MapOverlay(style);
                styleLoaded = true;
                cameraTarget = null; // Ensure fresh camera framing once style loads
                lastCameraAmbulancePoint = null;
                SurakshaLog.info(SurakshaLog.MAP, "map style loaded successfully");
                render();
                maybeRequestRoute(true);
            } catch (Exception e) {
                SurakshaLog.error(SurakshaLog.MAP, "overlay init", e);
            }
        });
    }

    /** Pass null for any point that is not known yet. */
    public void update(Point user, Point ambulance, Point hospital, boolean headingToHospital) {
        if (destroyed) return;
        boolean targetChanged = this.toHospital != headingToHospital;
        this.user = user;
        this.ambulance = ambulance;
        this.hospital = hospital;
        this.toHospital = headingToHospital;
        render();
        maybeRequestRoute(targetChanged);
    }

    public void forceRecenter() {
        cameraTarget = null;
        lastCameraAmbulancePoint = null;
        moveCameraIfNeeded();
    }

    public void destroy() {
        destroyed = true;
        overlay = null;
        styleLoaded = false;
    }

    private void render() {
        if (overlay != null) {
            overlay.setMarkers(user, ambulance, hospital);
            if (ambulance == null) overlay.clearRoute();
        }
        moveCameraIfNeeded();
    }

    /** Frames the active participants so user and ambulance are both comfortably visible. */
    private void moveCameraIfNeeded() {
        if (map == null || !styleLoaded) return;

        Point destination = toHospital ? hospital : user;
        if (destination == null && user != null) destination = user;

        if (ambulance != null && destination != null) {
            double dist = HaversineDistance.distanceInMeters(
                    ambulance.latitude(), ambulance.longitude(),
                    destination.latitude(), destination.longitude());

            // If coordinates are valid and not on opposite sides of the world (> 250km), frame them together
            if (dist < 250_000.0) {
                double midLat = (ambulance.latitude() + destination.latitude()) / 2.0;
                double midLng = (ambulance.longitude() + destination.longitude()) / 2.0;
                Point center = Point.fromLngLat(midLng, midLat);
                double zoom = calculateZoomForDistance(dist);

                String targetKey = (toHospital ? "to-hosp" : "to-user");
                boolean movedEnough = lastCameraAmbulancePoint == null ||
                        HaversineDistance.distanceInMeters(ambulance.latitude(), ambulance.longitude(),
                                lastCameraAmbulancePoint.latitude(), lastCameraAmbulancePoint.longitude()) > 60.0;

                if (cameraTarget == null || !targetKey.equals(cameraTarget) || movedEnough) {
                    cameraTarget = targetKey;
                    lastCameraAmbulancePoint = ambulance;
                    try {
                        map.setCamera(new CameraOptions.Builder().center(center).zoom(zoom).build());
                    } catch (Exception e) {
                        SurakshaLog.error(SurakshaLog.MAP, "setCamera (framed)", e);
                    }
                }
                return;
            }
        }

        // Single-point focus fallback (e.g. only user before dispatch, or only ambulance)
        Point focus = ambulance != null ? ambulance : (user != null ? user : hospital);
        if (focus == null) return;

        String targetKey = (ambulance != null ? "only-amb" : (user != null ? "only-user" : "only-hosp"));
        boolean moved = lastCameraAmbulancePoint == null ||
                HaversineDistance.distanceInMeters(focus.latitude(), focus.longitude(),
                        lastCameraAmbulancePoint.latitude(), lastCameraAmbulancePoint.longitude()) > 60.0;

        if (cameraTarget == null || !targetKey.equals(cameraTarget) || moved) {
            cameraTarget = targetKey;
            lastCameraAmbulancePoint = focus;
            try {
                map.setCamera(new CameraOptions.Builder().center(focus).zoom(14.5).build());
            } catch (Exception e) {
                SurakshaLog.error(SurakshaLog.MAP, "setCamera (single focus)", e);
            }
        }
    }

    private static double calculateZoomForDistance(double distMeters) {
        if (distMeters <= 400.0) return 15.5;
        if (distMeters <= 1200.0) return 14.5;
        if (distMeters <= 3000.0) return 13.5;
        if (distMeters <= 7000.0) return 12.3;
        if (distMeters <= 15000.0) return 11.2;
        if (distMeters <= 35000.0) return 10.0;
        if (distMeters <= 80000.0) return 8.8;
        return 7.5;
    }

    private void maybeRequestRoute(boolean force) {
        Point destination = toHospital ? hospital : user;
        if (ambulance == null || destination == null) return;
        String target = toHospital ? "hospital" : "user";
        long now = System.currentTimeMillis();
        if (!force && target.equals(lastRouteTarget) && now - lastRouteAt < ROUTE_REFRESH_MS) {
            double meters = HaversineDistance.distanceInMeters(
                    ambulance.latitude(), ambulance.longitude(), destination.latitude(), destination.longitude());
            int totalSec = Math.max(5, (int) Math.round(meters / 8.0));
            int minEta = totalSec / 60;
            int secEta = totalSec % 60;
            String quickEta = HaversineDistance.formatDistance(meters) + " | ETA " + minEta + " min " + secEta + " sec";
            listener.onRouteInfo(quickEta, toHospital);
            return;
        }

        routeInFlight = true;
        lastRouteAt = now;
        lastRouteTarget = target;
        final boolean requestedToHospital = toHospital;
        MapboxRouting.calculateRoute(context,
                ambulance.latitude(), ambulance.longitude(),
                destination.latitude(), destination.longitude(),
                new MapboxRouting.RouteCallback() {
                    @Override
                    public void onRouteSuccess(MapboxRouting.RouteInfo route) {
                        routeInFlight = false;
                        if (destroyed) return;
                        if (overlay != null) overlay.setRoute(route.geometry);
                        listener.onRouteInfo(MapboxRouting.formatDistanceAndEta(route), requestedToHospital);
                    }

                    @Override
                    public void onRouteFailure(String errorMessage) {
                        routeInFlight = false;
                        if (destroyed) return;
                        SurakshaLog.warn(SurakshaLog.MAP, "route unavailable", "reason=" + errorMessage);
                        if (overlay != null) overlay.clearRoute();
                        Point dest = requestedToHospital ? hospital : user;
                        if (ambulance != null && dest != null) {
                            double meters = HaversineDistance.distanceInMeters(
                                    ambulance.latitude(), ambulance.longitude(), dest.latitude(), dest.longitude());
                            listener.onRouteUnavailable("Straight-line distance " + HaversineDistance.formatDistance(meters)
                                     + " (route calculating...)");
                        } else {
                            listener.onRouteUnavailable("Route unavailable");
                        }
                    }
                });
    }

    public static Point point(Double latitude, Double longitude) {
        if (!HaversineDistance.isValidCoordinate(latitude, longitude)) return null;
        return Point.fromLngLat(longitude, latitude);
    }
}
