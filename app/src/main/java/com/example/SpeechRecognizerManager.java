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
import java.util.Locale;

/**
 * Manages speech recognition for Hindi, English, and Hinglish.
 */
public class SpeechRecognizerManager implements RecognitionListener {

    private static final String TAG = "SpeechRecognizerMgr";

    public interface SpeechListener {
        void onRmsChanged(float rmsdB);
        void onSpeechResult(String recognizedText);
        void onSpeechError(int errorCode, String errorMessage);
        void onReadyForSpeech();
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer speechRecognizer;
    private SpeechListener listener;
    private boolean isListening = false;

    public SpeechRecognizerManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(SpeechListener listener) {
        this.listener = listener;
    }

    public synchronized void startListening(boolean preferHindi) {
        handler.post(() -> {
            stopListening();

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                if (listener != null) {
                    listener.onSpeechError(-1, "Device par Speech Recognition uplabdh nahi hai.");
                }
                return;
            }

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                speechRecognizer.setRecognitionListener(SpeechRecognizerManager.this);

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

                // Multi-lingual support: support both Hindi & Indian English
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
            } catch (Exception e) {
                Log.e(TAG, "Error starting speech recognition", e);
                if (listener != null) {
                    listener.onSpeechError(-2, "Mic start nahi ho paya: " + e.getMessage());
                }
            }
        });
    }

    public synchronized void stopListening() {
        handler.post(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.stopListening();
                    speechRecognizer.cancel();
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }
            isListening = false;
        });
    }

    public boolean isListening() {
        return isListening;
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        if (listener != null) listener.onReadyForSpeech();
    }

    @Override
    public void onBeginningOfSpeech() {}

    @Override
    public void onRmsChanged(float rmsdB) {
        if (listener != null) {
            // Normalize RMS dB roughly from [-2, 10] to [0.0, 1.0]
            float normalized = Math.max(0.0f, Math.min(1.0f, (rmsdB + 2.0f) / 12.0f));
            listener.onRmsChanged(normalized);
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        isListening = false;
    }

    @Override
    public void onError(int error) {
        isListening = false;
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
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network error. Offline parser ka upyog kiya ja raha hai.";
                break;
            default:
                message = "Speech recognition error (" + error + ").";
                break;
        }
        if (listener != null) {
            listener.onSpeechError(error, message);
        }
    }

    @Override
    public void onResults(Bundle results) {
        isListening = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String bestResult = matches.get(0);
            if (listener != null) {
                listener.onSpeechResult(bestResult);
            }
        } else {
            if (listener != null) {
                listener.onSpeechError(SpeechRecognizer.ERROR_NO_MATCH, "Koi speech recognize nahi hua.");
            }
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {}

    @Override
    public void onEvent(int eventType, Bundle params) {}
}
