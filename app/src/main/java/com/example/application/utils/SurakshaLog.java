package com.example.application.utils;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/**
 * Central logging for Firebase / flow diagnostics.
 * Never pass passwords, security numbers, login keys, API keys or tokens to these methods.
 */
public final class SurakshaLog {

    public static final String AUTH = "SURAKSHA_AUTH";
    public static final String SOS = "SURAKSHA_SOS";
    public static final String HOSPITAL = "SURAKSHA_HOSPITAL";
    public static final String AMBULANCE = "SURAKSHA_AMBULANCE";
    public static final String DRIVER = "SURAKSHA_DRIVER";
    public static final String MAP = "SURAKSHA_MAP";
    public static final String FIRESTORE = "SURAKSHA_FIRESTORE";

    private SurakshaLog() {}

    /** Error code of a Firebase exception, e.g. PERMISSION_DENIED or ERROR_EMAIL_ALREADY_IN_USE. */
    public static String code(Throwable e) {
        if (e == null) return "NONE";
        if (e instanceof FirebaseFirestoreException) {
            return ((FirebaseFirestoreException) e).getCode().name();
        }
        if (e instanceof FirebaseAuthException) {
            return ((FirebaseAuthException) e).getErrorCode();
        }
        Throwable cause = e.getCause();
        if (cause != null && cause != e && (cause instanceof FirebaseFirestoreException || cause instanceof FirebaseAuthException)) {
            return code(cause);
        }
        return e.getClass().getSimpleName();
    }

    /**
     * Logs an operation failure. context entries should be "key=value" pairs such as
     * "emergencyId=abc", "hospitalId=xyz", "status=HOSPITAL_REQUESTED".
     */
    public static void error(String tag, String operation, Throwable e, String... context) {
        String msg = operation + " FAILED code=" + code(e)
                + " message=" + (e != null ? e.getMessage() : "null")
                + ctx(context);
        Log.e(tag, msg, e);
        if (e instanceof FirebaseFirestoreException && !FIRESTORE.equals(tag)) {
            Log.e(FIRESTORE, "[" + tag + "] " + msg);
        }
    }

    public static void info(String tag, String operation, String... context) {
        Log.i(tag, operation + ctx(context));
    }

    public static void warn(String tag, String operation, String... context) {
        Log.w(tag, operation + ctx(context));
    }

    /** Short user-facing message that still exposes the error code for field debugging. */
    public static String userMessage(String prefix, Throwable e) {
        return prefix + " (" + code(e) + ")";
    }

    private static String ctx(String... context) {
        if (context == null || context.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (String c : context) {
            if (c != null) sb.append(' ').append(c);
        }
        return sb.toString();
    }
}
