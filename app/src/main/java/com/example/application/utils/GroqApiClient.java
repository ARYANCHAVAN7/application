package com.example.application.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.example.application.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroqApiClient {

    private static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    public static final String MODEL_NAME = "openai/gpt-oss-120b";
    private static final String PREF_NAME = "suraksha_prefs";
    private static final String PREF_KEY_GROQ = "groq_api_key";

    private static final String SYSTEM_PROMPT =
            "You are Suraksha AI, a specialized real-time emergency medical and first-aid assistant built into the Suraksha Emergency Response Network.\n" +
            "Your highest priority is to provide immediate, life-saving, clear, step-by-step first aid instructions during accidents, medical emergencies, or disasters.\n\n" +
            "Core Rules:\n" +
            "1. Give direct, actionable instructions FIRST (e.g. Stop bleeding -> Apply direct pressure with clean cloth). Do not waste time with conversational fluff.\n" +
            "2. Keep steps in short numbered points (1, 2, 3) or bullet points so they are legible in high-stress situations within 5 seconds.\n" +
            "3. For critical emergencies (unconscious, no breathing, cardiac arrest, massive blood loss, chest pain, stroke, venomous bites), explicitly urge: '⚠️ Ensure 108 is called or press SOS in the app immediately!'\n" +
            "4. NEVER recommend harmful or unverified folk remedies (e.g. cutting snake bites, putting butter on burns).\n" +
            "5. Keep replies under 4-5 bullet points unless more detail is specifically requested.";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ApiCallback {
        void onSuccess(String responseText);
        void onError(String errorMessage);
    }

    public static String getApiKey(Context context) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String savedKey = prefs.getString(PREF_KEY_GROQ, null);
            if (!TextUtils.isEmpty(savedKey)) {
                return savedKey.trim();
            }
        }
        if (!TextUtils.isEmpty(BuildConfig.GROQ_API_KEY)) {
            return BuildConfig.GROQ_API_KEY.trim();
        }
        return "";
    }

    public static void saveApiKey(Context context, String apiKey) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_KEY_GROQ, apiKey != null ? apiKey.trim() : "").apply();
        }
    }

    public static void sendChat(Context context, List<ChatMessage> history, ApiCallback callback) {
        String apiKey = getApiKey(context);
        if (TextUtils.isEmpty(apiKey)) {
            mainHandler.post(() -> callback.onError("Groq API key not configured. Please tap the Key icon at the top to enter your free Groq API key (starts with gsk_)."));
            return;
        }

        try {
            JSONArray messagesArray = new JSONArray();

            // 1. System Prompt
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", SYSTEM_PROMPT);
            messagesArray.put(sysMsg);

            // 2. Append recent history (last 10 messages for context)
            int start = Math.max(0, history.size() - 10);
            for (int i = start; i < history.size(); i++) {
                ChatMessage m = history.get(i);
                if (m.isTyping()) continue;
                JSONObject msg = new JSONObject();
                msg.put("role", m.isUser() ? "user" : "assistant");
                msg.put("content", m.getText());
                messagesArray.put(msg);
            }

            JSONObject requestPayload = new JSONObject();
            requestPayload.put("model", MODEL_NAME);
            requestPayload.put("messages", messagesArray);
            requestPayload.put("temperature", 0.3); // Low temperature for high accuracy in medical advice
            requestPayload.put("max_tokens", 600);

            RequestBody body = RequestBody.create(
                    requestPayload.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(GROQ_ENDPOINT)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            JSONObject errObj = new JSONObject(responseBody);
                            String errMsg = errObj.optJSONObject("error") != null
                                    ? errObj.getJSONObject("error").optString("message", "API Error: " + response.code())
                                    : "API Error " + response.code();
                            mainHandler.post(() -> callback.onError(errMsg));
                            return;
                        }

                        JSONObject json = new JSONObject(responseBody);
                        JSONArray choices = json.getJSONArray("choices");
                        if (choices.length() > 0) {
                            String reply = choices.getJSONObject(0).getJSONObject("message").getString("content");
                            mainHandler.post(() -> callback.onSuccess(reply.trim()));
                        } else {
                            mainHandler.post(() -> callback.onError("No response received from Groq."));
                        }
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Failed to parse response: " + e.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Request build error: " + e.getMessage()));
        }
    }
}
