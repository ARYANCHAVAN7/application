package com.example.application.utils;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Selects the nearest suitable hospital for an emergency location.
 *
 * Pipeline (all steps are the project's existing algorithms):
 * 1. Firestore: users where role == "hospital" and isVerified == true  (verified only)
 * 2. Drop hospitals in excludedHospitalIds (already rejected this emergency)
 * 3. Drop hospitals with availableBeds <= 0 or invalid coordinates  (FirestoreValues, Haversine validation)
 * 4. GeoRadius.findWithinExpandingRadius: 5 -> 10 -> 15 -> 20 km, then nearest overall
 * 5. Nearest by HaversineDistance; ties broken by more available beds
 *
 * The selected hospital's UID becomes emergencies/{id}.hospitalId.
 */
public final class HospitalSelection {

    public static class Candidate {
        public String hospitalId;
        public String hospitalName;
        public double latitude;
        public double longitude;
        public int availableBeds;
        public double distanceMeters;
        /** Search tier that produced the match; -1 when the unlimited fallback was needed. */
        public double searchRadiusMeters;
    }

    public interface Callback {
        void onSelected(Candidate candidate);

        void onNoneAvailable(String reason);

        void onError(Exception e);
    }

    private HospitalSelection() {}

    public static void selectNearest(FirebaseFirestore db,
                                     double latitude, double longitude,
                                     Collection<String> excludedHospitalIds,
                                     Callback callback) {
        if (!HaversineDistance.isValidCoordinate(latitude, longitude)) {
            callback.onNoneAvailable("Invalid emergency location");
            return;
        }

        db.collection("users")
                .whereEqualTo("role", "hospital")
                .whereEqualTo("isVerified", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> eligible = new ArrayList<>();
                    int skippedBeds = 0;
                    int skippedLocation = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (excludedHospitalIds != null && excludedHospitalIds.contains(doc.getId())) {
                            continue;
                        }
                        Integer beds = FirestoreValues.getInt(doc, "availableBeds");
                        if (beds == null || beds <= 0) {
                            skippedBeds++;
                            continue;
                        }
                        Double lat = FirestoreValues.getDouble(doc, "latitude");
                        Double lng = FirestoreValues.getDouble(doc, "longitude");
                        if (!HaversineDistance.isValidCoordinate(lat, lng)) {
                            skippedLocation++;
                            continue;
                        }
                        eligible.add(doc);
                    }

                    SurakshaLog.info(SurakshaLog.SOS, "hospital candidates",
                            "verified=" + snapshot.size(),
                            "eligible=" + eligible.size(),
                            "noBeds=" + skippedBeds,
                            "noLocation=" + skippedLocation);

                    if (eligible.isEmpty()) {
                        callback.onNoneAvailable("No verified hospital with free beds and a registered location");
                        return;
                    }

                    GeoRadius.SearchResult result = GeoRadius.findWithinExpandingRadius(latitude, longitude, eligible);
                    if (result.documents.isEmpty()) {
                        callback.onNoneAvailable("No verified hospital within 50 km emergency response range");
                        return;
                    }

                    Candidate best = null;
                    for (DocumentSnapshot doc : result.documents) {
                        Candidate c = toCandidate(doc, latitude, longitude, result.radiusMeters);
                        if (best == null
                                || c.distanceMeters < best.distanceMeters - 1.0
                                || (Math.abs(c.distanceMeters - best.distanceMeters) <= 1.0
                                && c.availableBeds > best.availableBeds)) {
                            best = c;
                        }
                    }

                    SurakshaLog.info(SurakshaLog.SOS, "hospital selected",
                            "hospitalId=" + best.hospitalId,
                            "distance=" + HaversineDistance.formatDistance(best.distanceMeters),
                            "radius=" + (best.searchRadiusMeters < 0 ? "unlimited" : GeoRadius.formatRadius(best.searchRadiusMeters)));
                    callback.onSelected(best);
                })
                .addOnFailureListener(callback::onError);
    }

    private static Candidate toCandidate(DocumentSnapshot doc, double lat, double lng, double radius) {
        Candidate c = new Candidate();
        c.hospitalId = doc.getId();
        c.hospitalName = FirestoreValues.getString(doc, "hospitalName");
        c.latitude = FirestoreValues.getDouble(doc, "latitude");
        c.longitude = FirestoreValues.getDouble(doc, "longitude");
        Integer beds = FirestoreValues.getInt(doc, "availableBeds");
        c.availableBeds = beds == null ? 0 : beds;
        c.distanceMeters = HaversineDistance.distanceInMeters(lat, lng, c.latitude, c.longitude);
        c.searchRadiusMeters = radius;
        return c;
    }
}
