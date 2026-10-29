package com.example.application.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Ambulance login keeps the existing "hospital name + security number" UX, but the security
 * number is never stored or queried in plain text. Instead the document
 * ambulanceLogins/{sha256(normalizedHospitalName|securityNumber)} maps to an ambulanceId.
 * Clients may only GET that document by its exact ID (listing is denied by rules), so the
 * ambulanceId can only be discovered by someone who knows the credentials.
 */
public final class DriverCredentials {

    /** Short numeric codes can be guessed online; enforce a minimum length. */
    public static final int MIN_SECURITY_NUMBER_LENGTH = 6;

    private DriverCredentials() {}

    public static String normalizeHospitalName(String hospitalName) {
        if (hospitalName == null) return "";
        return hospitalName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String loginKey(String hospitalName, String securityNumber) {
        String input = normalizeHospitalName(hospitalName) + "|" + (securityNumber == null ? "" : securityNumber.trim());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.US, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
