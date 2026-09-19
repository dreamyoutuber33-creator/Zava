package com.example;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service for streaming text and conversation responses from Gemini API.
 */
public class GeminiService {

    private static final String TAG = "GeminiService";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

    private final OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface GeminiCallback {
        void onResponse(String text);
        void onError(String errorMessage);
    }

    public GeminiService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void generateResponse(String prompt, GeminiCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("MY_GEMINI_API_KEY")) {
            Log.w(TAG, "Gemini API key is not configured or is placeholder");
            if (callback != null) {
                callback.onError("Gemini API key configure nahi hai.");
            }
            return;
        }

        try {
            JSONObject root = new JSONObject();

            // System instruction for voice assistant persona
            JSONObject systemInstruction = new JSONObject();
            JSONArray sysParts = new JSONArray();
            JSONObject sysTextPart = new JSONObject();
            sysTextPart.put("text", "You are Hey Zava, an intelligent, helpful, and polite Android voice assistant. " +
                    "Respond concisely and conversationally in 1-2 short sentences in the user's language (Hindi, Hinglish, or English). " +
                    "Your answer will be spoken via Text-to-Speech.");
            sysParts.put(sysTextPart);
            systemInstruction.put("parts", sysParts);
            root.put("systemInstruction", systemInstruction);

            // User prompt contents
            JSONArray contents = new JSONArray();
            JSONObject userContent = new JSONObject();
            userContent.put("role", "user");
            JSONArray parts = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);
            parts.put(textPart);
            userContent.put("parts", parts);
            contents.put(userContent);
            root.put("contents", contents);

            // Generation config
            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.7);
            genConfig.put("maxOutputTokens", 250);
            root.put("generationConfig", genConfig);

            String requestJson = root.toString();
            RequestBody requestBody = RequestBody.create(requestJson, MediaType.parse("application/json; charset=utf-8"));

            String url = BASE_URL + "?key=" + apiKey;
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Gemini API call failed", e);
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "";
                        Log.e(TAG, "Gemini error response: " + response.code() + " " + errBody);
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("Gemini server error: " + response.code()));
                        }
                        return;
                    }

                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        JSONObject resObj = new JSONObject(responseBody);
                        JSONArray candidates = resObj.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject firstCandidate = candidates.getJSONObject(0);
                            JSONObject contentObj = firstCandidate.optJSONObject("content");
                            if (contentObj != null) {
                                JSONArray resParts = contentObj.optJSONArray("parts");
                                if (resParts != null && resParts.length() > 0) {
                                    String reply = resParts.getJSONObject(0).optString("text", "");
                                    if (callback != null) {
                                        mainHandler.post(() -> callback.onResponse(reply.trim()));
                                    }
                                    return;
                                }
                            }
                        }

                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("Gemini se koi valid response nahi mila."));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Gemini response", e);
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("Response parse error: " + e.getMessage()));
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error creating Gemini request", e);
            if (callback != null) {
                callback.onError("Request build error: " + e.getMessage());
            }
        }
    }
}
