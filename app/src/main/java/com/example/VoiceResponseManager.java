package com.example;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * Text-To-Speech engine for natural conversational Hindi and English responses.
 */
public class VoiceResponseManager implements TextToSpeech.OnInitListener {

    private static final String TAG = "VoiceResponseManager";

    public interface SpeechCallback {
        default void onSpeechStarted() {}
        void onSpeechCompleted();
    }

    private final Context context;
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private SpeechCallback currentCallback;

    public VoiceResponseManager(Context context) {
        this.context = context.getApplicationContext();
        this.tts = new TextToSpeech(this.context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true;
            // Prefer Hindi if available, fallback to Indian English or US English
            Locale hi = new Locale("hi", "IN");
            int resHi = tts.setLanguage(hi);
            if (resHi == TextToSpeech.LANG_MISSING_DATA || resHi == TextToSpeech.LANG_NOT_SUPPORTED) {
                Locale enIn = new Locale("en", "IN");
                int resEn = tts.setLanguage(enIn);
                if (resEn == TextToSpeech.LANG_MISSING_DATA || resEn == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }
            }

            tts.setSpeechRate(0.98f);
            tts.setPitch(1.02f);

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    if (currentCallback != null) {
                        currentCallback.onSpeechStarted();
                    }
                }

                @Override
                public void onDone(String utteranceId) {
                    if (currentCallback != null) {
                        currentCallback.onSpeechCompleted();
                    }
                }

                @Override
                public void onError(String utteranceId) {
                    if (currentCallback != null) {
                        currentCallback.onSpeechCompleted();
                    }
                }
            });
        } else {
            Log.e(TAG, "TTS Initialization failed");
        }
    }

    public void speak(String text, SpeechCallback callback) {
        this.currentCallback = callback;
        if (!isInitialized || tts == null || text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onSpeechCompleted();
            return;
        }

        // Auto switch language based on script
        LanguageDetector.Language lang = LanguageDetector.detect(text);
        if (lang == LanguageDetector.Language.HINDI) {
            tts.setLanguage(new Locale("hi", "IN"));
        } else {
            tts.setLanguage(new Locale("en", "IN"));
        }

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ZAVA_" + System.currentTimeMillis());
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ZAVA_" + System.currentTimeMillis());
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
