package com.example.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.application.utils.DriverCredentials;
import com.example.application.utils.HaversineDistance;

import org.junit.Test;

/** Pure-JVM tests for the emergency state machine, Haversine and driver credential hashing. */
public class EmergencyFlowLogicTest {

    @Test
    public void haversineMatchesKnownDistances() {
        double nagpurMumbai = HaversineDistance.distanceInMeters(21.1458, 79.0882, 19.0760, 72.8777);
        assertTrue(nagpurMumbai > 670_000 && nagpurMumbai < 700_000);
        assertEquals(0.0, HaversineDistance.distanceInMeters(21.1458, 79.0882, 21.1458, 79.0882), 0.001);
    }

    @Test
    public void invalidCoordinatesRejected() {
        assertFalse(HaversineDistance.isValidCoordinate(0.0, 0.0));
        assertFalse(HaversineDistance.isValidCoordinate(null, 79.0));
        assertFalse(HaversineDistance.isValidCoordinate(Double.NaN, 79.0));
        assertFalse(HaversineDistance.isValidCoordinate(91.0, 79.0));
        assertTrue(HaversineDistance.isValidCoordinate(21.1458, 79.0882));
    }

    @Test
    public void driverStatusChainReachesCompleted() {
        String s = EmergencyStatus.DRIVER_ACCEPTED;
        String[] expected = {EmergencyStatus.AMBULANCE_ARRIVED, EmergencyStatus.PATIENT_PICKED_UP,
                EmergencyStatus.EN_ROUTE_TO_HOSPITAL, EmergencyStatus.COMPLETED};
        for (String next : expected) {
            s = EmergencyStatus.nextDriverStatus(s);
            assertEquals(next, s);
        }
        assertNull(EmergencyStatus.nextDriverStatus(EmergencyStatus.COMPLETED));
    }

    @Test
    public void statusGroupsAreConsistent() {
        assertTrue(EmergencyStatus.HOSPITAL_VISIBLE.contains(EmergencyStatus.HOSPITAL_REQUESTED));
        assertTrue(EmergencyStatus.HOSPITAL_VISIBLE.size() <= 30); // Firestore whereIn limit
        assertFalse(EmergencyStatus.isActive(EmergencyStatus.COMPLETED));
        assertFalse(EmergencyStatus.isActive(EmergencyStatus.HOSPITAL_REJECTED));
        assertTrue(EmergencyStatus.needsDispatch(EmergencyStatus.DRIVER_REJECTED));
        assertTrue(EmergencyStatus.isHeadingToHospital(EmergencyStatus.PATIENT_PICKED_UP));
        assertFalse(EmergencyStatus.isHeadingToHospital(EmergencyStatus.AMBULANCE_ARRIVED));
    }

    @Test
    public void driverLoginKeyNormalizesNameButNotSecret() {
        String a = DriverCredentials.loginKey("  City   Hospital ", "123456");
        String b = DriverCredentials.loginKey("city hospital", "123456");
        assertEquals(a, b);
        assertEquals(64, a.length());
        assertNotEquals(a, DriverCredentials.loginKey("city hospital", "123457"));
    }
}
