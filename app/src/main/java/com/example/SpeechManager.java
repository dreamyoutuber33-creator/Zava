package com.example;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

/**
 * Robust SpeechManager for persistent always-on voice recognition.
 * Strictly guarantees all SpeechRecognizer creation, execution, and cancellation
 * occur on the Main (Looper) Thread to prevent Android threading violations.
 * Handles timeouts and recognizer busy states with safe delays and clean session teardowns.
 */
public class SpeechManager implements RecognitionListener {

    private static final String TAG = "SpeechManager";

    public interface SpeechListener {
        void onRmsChanged(float rmsdB);
        void onSpeechResult(String recognizedText);
        void onSpeechPartialResult(String partialText);
        void onSpeechError(int errorCode, String errorMessage);
        void onReadyForSpeech();
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer speechRecognizer;
    private SpeechRecognizerManager.SpeechListener listener;

    private boolean isListening = false;
    private boolean isContinuousMode = false;
    private boolean isPausedForTts = false;
    private boolean preferHindi = true;

    // Safety watchdog: restarts continuous listener if onEndOfSpeech fires without a terminal result
    private final Runnable speechEndWatchdog = () -> {
        if (isContinuousMode && !isPausedForTts) {
            Log.d(TAG, "Speech end watchdog triggered: recycling session");
            restartListeningWithDelay(500);
        }
    };

    private final Runnable restartRunnable = () -> {
        if (isContinuousMode && !isPausedForTts) {
            startListeningInternal();
        }
    };

    public SpeechManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(SpeechRecognizerManager.SpeechListener listener) {
        this.listener = listener;
    }

    public void setContinuousMode(boolean continuous) {
        this.isContinuousMode = continuous;
        if (!continuous) {
            stopListening();
        }
    }

    public boolean isContinuousMode() {
        return isContinuousMode;
    }

    public synchronized void startContinuousListening(boolean preferHindi) {
        this.isContinuousMode = true;
        this.isPausedForTts = false;
        this.preferHindi = preferHindi;
        startListening(preferHindi);
    }

    public synchronized void pauseForTts() {
        Log.d(TAG, "Pausing microphone recognition for TTS output");
        this.isPausedForTts = true;
        mainHandler.removeCallbacks(restartRunnable);
        mainHandler.removeCallbacks(speechEndWatchdog);
        postToMain(this::destroyRecognizerInternal);
    }

    public synchronized void resumeAfterTts() {
        Log.d(TAG, "Resuming microphone recognition after TTS completed");
        this.isPausedForTts = false;
        if (isContinuousMode) {
            restartListeningWithDelay(500);
        }
    }

    public synchronized void startListening(boolean preferHindi) {
        this.preferHindi = preferHindi;
        postToMain(this::startListeningInternal);
    }

    public synchronized void stopListening() {
        this.isContinuousMode = false;
        this.isPausedForTts = false;
        mainHandler.removeCallbacks(restartRunnable);
        mainHandler.removeCallbacks(speechEndWatchdog);
        postToMain(this::destroyRecognizerInternal);
    }

    /**
     * Executes runnable on Main Looper Thread strictly.
     */
    private void postToMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private void restartListeningWithDelay(long delayMs) {
        mainHandler.removeCallbacks(restartRunnable);
        if (isContinuousMode && !isPausedForTts) {
            mainHandler.postDelayed(restartRunnable, delayMs);
        }
    }

    private void startListeningInternal() {
        mainHandler.removeCallbacks(restartRunnable);
        mainHandler.removeCallbacks(speechEndWatchdog);

        if (isPausedForTts) {
            Log.d(TAG, "SpeechRecognizer is paused for TTS output; not starting.");
            return;
        }

        // Clean up previous recognizer instance thoroughly
        destroyRecognizerInternal();

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition is not available on this device.");
            if (listener != null) {
                listener.onSpeechError(-1, "Speech Recognition is not available on this device.");
            }
            return;
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(this);

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());

            // Provide ample silence lengths before speech timeout
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L);

            if (preferHindi) {
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN");
            } else {
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN");
            }
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"hi-IN", "en-IN", "en-US"});

            speechRecognizer.startListening(intent);
            isListening = true;
            Log.d(TAG, "SpeechRecognizer successfully started on Main Thread.");
        } catch (Exception e) {
            Log.e(TAG, "Exception starting SpeechRecognizer", e);
            isListening = false;
            destroyRecognizerInternal();
            if (isContinuousMode && !isPausedForTts) {
                restartListeningWithDelay(1000);
            } else if (listener != null) {
                listener.onSpeechError(-2, "Mic start failed: " + e.getMessage());
            }
        }
    }

    private void destroyRecognizerInternal() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception e) {
                Log.w(TAG, "Exception destroying SpeechRecognizer", e);
            }
            speechRecognizer = null;
        }
        isListening = false;
    }

    public boolean isListening() {
        return isListening;
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        isListening = true;
        if (listener != null) {
            listener.onReadyForSpeech();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        isListening = true;
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        if (listener != null) {
            float normalized = Math.max(0.0f, Math.min(1.0f, (rmsdB + 2.0f) / 12.0f));
            listener.onRmsChanged(normalized);
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        isListening = false;
        if (isContinuousMode && !isPausedForTts) {
            mainHandler.removeCallbacks(speechEndWatchdog);
            mainHandler.postDelayed(speechEndWatchdog, 2500);
        }
    }

    @Override
    public void onError(int error) {
        isListening = false;
        mainHandler.removeCallbacks(speechEndWatchdog);
        Log.d(TAG, "SpeechRecognizer onError: code " + error);

        // In continuous always-on listening, timeout (6) and no-match (7) indicate silence.
        // Cleanly cancel & recreate with 500ms delay to avoid CPU loops or sound glitches.
        if (isContinuousMode && !isPausedForTts) {
            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_NO_MATCH) {
                destroyRecognizerInternal();
                restartListeningWithDelay(500);
                return;
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                    error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                destroyRecognizerInternal();
                restartListeningWithDelay(750);
                return;
            }
        }

        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "Sorry, mujhe command samajh nahi aayi.";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "Ji, command boliye.";
                break;
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording me samasya aayi.";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Microphone permission required.";
                break;
            default:
                message = "Speech recognition error (" + error + ").";
                break;
        }

        if (listener != null) {
            listener.onSpeechError(error, message);
        }

        if (isContinuousMode && !isPausedForTts) {
            destroyRecognizerInternal();
            restartListeningWithDelay(1000);
        }
    }

    @Override
    public void onResults(Bundle results) {
        isListening = false;
        mainHandler.removeCallbacks(speechEndWatchdog);

        ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
        if (matches != null && !matches.isEmpty()) {
            String bestResult = matches.get(0);
            if (bestResult != null && !bestResult.trim().isEmpty()) {
                if (listener != null) {
                    listener.onSpeechResult(bestResult.trim());
                }
                return;
            }
        }

        // Empty match in continuous mode: cycle recognizer cleanly
        if (isContinuousMode && !isPausedForTts) {
            destroyRecognizerInternal();
            restartListeningWithDelay(500);
        } else if (listener != null) {
            listener.onSpeechError(SpeechRecognizer.ERROR_NO_MATCH, "Koi speech recognize nahi hua.");
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (partialResults == null) return;
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String partialText = matches.get(0);
            if (listener != null && partialText != null && !partialText.trim().isEmpty()) {
                listener.onSpeechPartialResult(partialText.trim());
            }
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {}
}
