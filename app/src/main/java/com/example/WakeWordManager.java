package com.example;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Manages Wake Word detection state, chime tones, and listening lifecycle.
 */
public class WakeWordManager {

    private static final String TAG = "WakeWordManager";

    public enum State {
        IDLE,
        LISTENING_FOR_WAKE,
        WAKE_DETECTED,
        LISTENING_FOR_COMMAND,
        PROCESSING,
        EXECUTING,
        COMPLETED,
        FAILED
    }

    public interface WakeWordListener {
        void onStateChanged(State state);
        void onWakeWordDetected();
        void onCommandSpeechRecognized(String command);
        void onTimeout(String message);
        void onError(String error);
        void onRmsChanged(float rmsDb);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private State currentState = State.IDLE;
    private WakeWordListener listener;
    private ToneGenerator toneGenerator;

    public WakeWordManager(Context context) {
        this.context = context.getApplicationContext();
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        } catch (Exception e) {
            Log.w(TAG, "ToneGenerator init failed", e);
        }
    }

    public void setListener(WakeWordListener listener) {
        this.listener = listener;
    }

    public State getState() {
        return currentState;
    }

    public void setState(State state) {
        this.currentState = state;
        if (listener != null) {
            handler.post(() -> listener.onStateChanged(state));
        }
    }

    public void playWakeChime() {
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play wake chime", e);
        }
    }

    public boolean isWakePhrase(String text) {
        if (text == null) return false;
        String clean = text.toLowerCase().replaceAll("[^a-z0-9\\u0900-\\u097F\\s]", " ").trim();
        return clean.contains("hey zava") ||
               clean.contains("zava") ||
               clean.contains("hey java") ||
               clean.contains("ok zava") ||
               clean.contains("हे ज़ावा") ||
               clean.contains("हे जावा") ||
               clean.contains("ज़ावा");
    }

    public void release() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}
