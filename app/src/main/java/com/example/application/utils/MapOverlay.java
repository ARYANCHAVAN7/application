package com.example.application.utils;

import com.mapbox.bindgen.Expected;
import com.mapbox.bindgen.None;
import com.mapbox.bindgen.Value;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.maps.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws user / ambulance / hospital markers and the active route on a loaded Mapbox style
 * using the core style API (GeoJSON sources + circle/line layers). Must be used on the
 * main thread with the Style delivered by loadStyle().
 */
public class MapOverlay {

    public static final String KIND_USER = "user";
    public static final String KIND_AMBULANCE = "ambulance";
    public static final String KIND_HOSPITAL = "hospital";

    private static final String POINTS_SOURCE = "suraksha-points-source";
    private static final String ROUTE_SOURCE = "suraksha-route-source";
    private static final String POINTS_LAYER = "suraksha-points-layer";
    private static final String ROUTE_LAYER = "suraksha-route-layer";
    private static final String EMPTY_FC = "{\"type\":\"FeatureCollection\",\"features\":[]}";

    private final Style style;

    public MapOverlay(Style style) {
        this.style = style;
        addSourceIfMissing(ROUTE_SOURCE);
        addSourceIfMissing(POINTS_SOURCE);
        addLayerIfMissing(ROUTE_LAYER,
                "{\"id\":\"" + ROUTE_LAYER + "\",\"type\":\"line\",\"source\":\"" + ROUTE_SOURCE + "\","
                        + "\"layout\":{\"line-join\":\"round\",\"line-cap\":\"round\"},"
                        + "\"paint\":{\"line-color\":\"#1E88E5\",\"line-width\":6,\"line-opacity\":0.9}}");
        addLayerIfMissing(POINTS_LAYER,
                "{\"id\":\"" + POINTS_LAYER + "\",\"type\":\"circle\",\"source\":\"" + POINTS_SOURCE + "\","
                        + "\"paint\":{\"circle-radius\":12,\"circle-stroke-width\":3,\"circle-stroke-color\":\"#FFFFFF\","
                        + "\"circle-color\":[\"match\",[\"get\",\"kind\"],"
                        + "\"user\",\"#D32F2F\",\"ambulance\",\"#1E88E5\",\"hospital\",\"#2E7D32\",\"#555555\"]}}");
    }

    /** Any argument may be null to hide that marker. */
    public void setMarkers(Point user, Point ambulance, Point hospital) {
        List<Feature> features = new ArrayList<>();
        addPoint(features, user, KIND_USER);
        addPoint(features, ambulance, KIND_AMBULANCE);
        addPoint(features, hospital, KIND_HOSPITAL);
        setSourceData(POINTS_SOURCE, FeatureCollection.fromFeatures(features).toJson());
    }

    public void setRoute(LineString route) {
        if (route == null) {
            clearRoute();
            return;
        }
        List<Feature> features = new ArrayList<>();
        features.add(Feature.fromGeometry(route));
        setSourceData(ROUTE_SOURCE, FeatureCollection.fromFeatures(features).toJson());
    }

    public void clearRoute() {
        setSourceData(ROUTE_SOURCE, EMPTY_FC);
    }

    private void addPoint(List<Feature> features, Point point, String kind) {
        if (point == null) return;
        Feature f = Feature.fromGeometry(point);
        f.addStringProperty("kind", kind);
        features.add(f);
    }

    private void addSourceIfMissing(String sourceId) {
        if (style.styleSourceExists(sourceId)) return;
        Value props = parse("{\"type\":\"geojson\",\"data\":" + EMPTY_FC + "}");
        if (props == null) return;
        Expected<String, None> result = style.addStyleSource(sourceId, props);
        if (result.isError()) {
            SurakshaLog.warn(SurakshaLog.MAP, "addStyleSource failed", "source=" + sourceId, "error=" + result.getError());
        }
    }

    private void addLayerIfMissing(String layerId, String json) {
        if (style.styleLayerExists(layerId)) return;
        Value props = parse(json);
        if (props == null) return;
        Expected<String, None> result = style.addStyleLayer(props, null);
        if (result.isError()) {
            SurakshaLog.warn(SurakshaLog.MAP, "addStyleLayer failed", "layer=" + layerId, "error=" + result.getError());
        }
    }

    private void setSourceData(String sourceId, String geoJson) {
        Value data = parse(geoJson);
        if (data == null) return;
        Expected<String, None> result = style.setStyleSourceProperty(sourceId, "data", data);
        if (result.isError()) {
            SurakshaLog.warn(SurakshaLog.MAP, "setStyleSourceProperty failed", "source=" + sourceId, "error=" + result.getError());
        }
    }

    private static Value parse(String json) {
        Expected<String, Value> parsed = Value.fromJson(json);
        if (parsed.isError()) {
            SurakshaLog.warn(SurakshaLog.MAP, "Value.fromJson failed", "error=" + parsed.getError());
            return null;
        }
        return parsed.getValue();
    }
}
