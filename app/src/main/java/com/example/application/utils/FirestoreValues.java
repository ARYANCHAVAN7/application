package com.example.application.utils;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;

/**
 * Type-tolerant readers. Older documents stored numbers (beds, coordinates) as Strings;
 * DocumentSnapshot.getDouble/getString throw when the stored type differs, so all
 * numeric reads go through here.
 */
public final class FirestoreValues {

    private FirestoreValues() {}

    public static Double getDouble(DocumentSnapshot doc, String field) {
        if (doc == null || !doc.exists()) return null;
        return toDouble(doc.get(field));
    }

    public static Double getDouble(Map<String, Object> data, String field) {
        if (data == null) return null;
        return toDouble(data.get(field));
    }

    public static Integer getInt(DocumentSnapshot doc, String field) {
        Double d = getDouble(doc, field);
        return d == null ? null : (int) Math.floor(d);
    }

    /** Reads any value as text (numbers stored where text is expected no longer crash). */
    public static String getString(DocumentSnapshot doc, String field) {
        if (doc == null || !doc.exists()) return null;
        Object v = doc.get(field);
        if (v == null) return null;
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.rint(d)) return String.valueOf((long) d);
        }
        return String.valueOf(v);
    }

    public static boolean getBoolean(DocumentSnapshot doc, String field) {
        if (doc == null || !doc.exists()) return false;
        Object v = doc.get(field);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return "true".equalsIgnoreCase(((String) v).trim());
        return false;
    }

    public static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
        }
        if (v instanceof String) {
            try {
                String s = ((String) v).trim();
                if (s.isEmpty()) return null;
                double d = Double.parseDouble(s);
                return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
