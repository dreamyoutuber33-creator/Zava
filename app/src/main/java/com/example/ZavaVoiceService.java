package com.example;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Robust Foreground Service for "HEY ZAVA" Voice Assistant.
 * Fully compliant with Android 14+ (API 34) Foreground Service type microphone mandates.
 * Calls startForeground() immediately in onCreate() to prevent ForegroundServiceStartNotAllowedException
 * and Android ANR / BadNotificationException crashes.
 */
public class ZavaVoiceService extends Service {

    private static final String TAG = "ZavaVoiceService";

    public static final String ACTION_START = "com.example.action.START";
    public static final String ACTION_STOP = "com.example.action.STOP";
    public static final String ACTION_TRIGGER_MIC = "com.example.action.TRIGGER_MIC";

    private static final String CHANNEL_ID = "zava_voice_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    public interface ServiceListener {
        void onStateChanged(WakeWordManager.State state);
        void onSpeechPartialResult(String text);
        void onSpeechRecognized(String text);
        void onCommandProcessed(ZavaCommand command, boolean success, String message);
        void onRmsLevel(float rms);
        void onPermissionRestricted();
    }

    private final IBinder binder = new LocalBinder();
    private ServiceListener serviceListener;

    private SpeechManager speechManager;
    private WakeWordManager wakeWordManager;
    private VoiceResponseManager voiceResponseManager;
    private AICommandEngine commandEngine;
    private ActionExecutor actionExecutor;
    private AppResolver appResolver;
    private CommandHistoryManager historyManager;
    private GeminiService geminiService;

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private PowerManager.WakeLock wakeLock;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isAssistantActive = false;

    public class LocalBinder extends Binder {
        public ZavaVoiceService getService() {
            return ZavaVoiceService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate() initializing ZavaVoiceService");

        // 1. Ensure notification channel exists
        createNotificationChannel();

        // 2. CRITICAL ANDROID 14+: Call startForeground immediately in onCreate()
        promoteToForegroundImmediately();

        // 3. Initialize Audio & WakeLock safely
        try {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeyZava::VoiceServiceWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring system services in onCreate", e);
        }

        // 4. Initialize engines & helpers
        try {
            appResolver = new AppResolver(this);
            commandEngine = new LocalCommandEngine(this, appResolver);
            actionExecutor = new ActionExecutor(this, appResolver);
            historyManager = new CommandHistoryManager(this);
            geminiService = new GeminiService();

            wakeWordManager = new WakeWordManager(this);
            voiceResponseManager = new VoiceResponseManager(this);
            speechManager = new SpeechManager(this);

            setupSpeechRecognizer();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing assistant engines", e);
        }
    }

    /**
     * Immediately satisfies Android's foreground service execution guarantee.
     */
    private void promoteToForegroundImmediately() {
        try {
            Notification notification = buildServiceNotification("Hey Zava • Voice Assistant Active");
            if (Build.VERSION.SDK_INT >= 34) { // Android 14+
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            Log.i(TAG, "Service successfully promoted to Foreground Service");
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Failed to promote service to foreground", e);
        }
    }

    private void setupSpeechRecognizer() {
        if (speechManager == null) return;

        speechManager.setListener(new SpeechRecognizerManager.SpeechListener() {
            @Override
            public void onRmsChanged(float rmsdB) {
                if (serviceListener != null) {
                    serviceListener.onRmsLevel(rmsdB);
                }
            }

            @Override
            public void onSpeechPartialResult(String partialText) {
                if (serviceListener != null) {
                    serviceListener.onSpeechPartialResult(partialText);
                }
                updateServiceNotification("Hearing: “" + partialText + "…”");
            }

            @Override
            public void onSpeechResult(String recognizedText) {
                if (recognizedText == null || recognizedText.trim().isEmpty()) return;

                if (serviceListener != null) {
                    serviceListener.onSpeechRecognized(recognizedText);
                }
                updateServiceNotification("You: “" + recognizedText + "”");

                // Pause speech recognizer so it does not transcribe TTS speech feedback
                if (speechManager != null) {
                    speechManager.pauseForTts();
                }

                handleCommandSpeech(recognizedText);
            }

            @Override
            public void onSpeechError(int errorCode, String errorMessage) {
                Log.w(TAG, "Speech recognition error: code " + errorCode + " - " + errorMessage);

                if (errorCode == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                        !PermissionManager.hasRecordAudioPermission(ZavaVoiceService.this)) {
                    showPermissionRestrictedNotification();
                    if (serviceListener != null) {
                        serviceListener.onPermissionRestricted();
                    }
                }
            }

            @Override
            public void onReadyForSpeech() {
                if (serviceListener != null) {
                    serviceListener.onStateChanged(WakeWordManager.State.LISTENING_FOR_COMMAND);
                }
            }
        });
    }

    public void setServiceListener(ServiceListener listener) {
        this.serviceListener = listener;
    }

    public boolean isAssistantActive() {
        return isAssistantActive;
    }

    public void startAssistant() {
        if (!PermissionManager.hasRecordAudioPermission(this)) {
            Log.w(TAG, "Cannot start assistant: RECORD_AUDIO permission not granted");
            if (serviceListener != null) serviceListener.onPermissionRestricted();
            return;
        }

        isAssistantActive = true;
        try {
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(12 * 60 * 60 * 1000L); // 12 hours max hold
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed acquiring wakelock", e);
        }

        requestAudioFocus();
        promoteToForegroundImmediately();

        if (wakeWordManager != null) {
            wakeWordManager.setState(WakeWordManager.State.LISTENING_FOR_COMMAND);
        }
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.LISTENING_FOR_COMMAND);
        }

        if (speechManager != null) {
            speechManager.startContinuousListening(true);
        }
        updateServiceNotification("Active • Listening continuously for voice commands");
    }

    public void stopAssistant() {
        isAssistantActive = false;
        if (speechManager != null) {
            speechManager.stopListening();
        }
        abandonAudioFocus();

        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error releasing wakelock", e);
        }

        if (wakeWordManager != null) {
            wakeWordManager.setState(WakeWordManager.State.IDLE);
        }
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.IDLE);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    public void triggerManualListen() {
        if (!isAssistantActive) {
            startAssistant();
        } else if (speechManager != null) {
            speechManager.startContinuousListening(true);
        }
    }

    public void processDirectCommand(String text) {
        if (speechManager != null) {
            speechManager.pauseForTts();
        }
        handleCommandSpeech(text);
    }

    private void handleCommandSpeech(String commandText) {
        if (wakeWordManager != null) {
            wakeWordManager.setState(WakeWordManager.State.PROCESSING);
        }
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.PROCESSING);
        }
        updateServiceNotification("Processing: “" + commandText + "”");

        if (commandEngine == null) {
            if (speechManager != null) speechManager.resumeAfterTts();
            return;
        }

        commandEngine.plan(commandText, new AICommandEngine.Callback() {
            @Override
            public void onPlanReady(ZavaCommand plannedCommand) {
                if (ZavaCommand.INTENT_UNKNOWN.equals(plannedCommand.getIntent()) && geminiService != null) {
                    geminiService.generateResponse(commandText, new GeminiService.GeminiCallback() {
                        @Override
                        public void onResponse(String text) {
                            plannedCommand.setConversationalResponse(text);
                            executePlannedCommand(plannedCommand);
                        }

                        @Override
                        public void onError(String error) {
                            plannedCommand.setConversationalResponse("Kshama karein, main yeh command samajh nahi paya.");
                            executePlannedCommand(plannedCommand);
                        }
                    });
                } else {
                    executePlannedCommand(plannedCommand);
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (voiceResponseManager != null) {
                    voiceResponseManager.speak(errorMessage, () -> {
                        if (speechManager != null && isAssistantActive) {
                            speechManager.resumeAfterTts();
                        }
                    });
                }
                updateServiceNotification("Ready • " + errorMessage);
                if (serviceListener != null) {
                    serviceListener.onCommandProcessed(null, false, errorMessage);
                }
            }
        });
    }

    private void executePlannedCommand(ZavaCommand command) {
        if (wakeWordManager != null) {
            wakeWordManager.setState(WakeWordManager.State.EXECUTING);
        }
        String verbalResponse = command.getConversationalResponse();
        updateServiceNotification("Zava: “" + verbalResponse + "”");

        if (voiceResponseManager != null) {
            voiceResponseManager.speak(verbalResponse, new VoiceResponseManager.SpeechCallback() {
                @Override
                public void onSpeechStarted() {}

                @Override
                public void onSpeechCompleted() {
                    if (actionExecutor != null) {
                        actionExecutor.executeCommand(command, new ActionExecutor.ExecutionCallback() {
                            @Override
                            public void onStepCompleted(ZavaAction action, boolean success, String message) {}

                            @Override
                            public void onAllCompleted(boolean overallSuccess, String finalMessage) {
                                if (historyManager != null) {
                                    historyManager.recordCommand(command.getRawSpeech(), command.getIntent(), overallSuccess, finalMessage);
                                }
                                if (serviceListener != null) {
                                    serviceListener.onCommandProcessed(command, overallSuccess, finalMessage);
                                }

                                if (voiceResponseManager != null && finalMessage != null && !finalMessage.isEmpty()) {
                                    voiceResponseManager.speak(finalMessage, () -> {
                                        finishExecutionCycle();
                                    });
                                } else {
                                    finishExecutionCycle();
                                }
                            }
                        });
                    } else {
                        finishExecutionCycle();
                    }
                }
            });
        }
    }

    private void finishExecutionCycle() {
        if (wakeWordManager != null) {
            wakeWordManager.setState(WakeWordManager.State.LISTENING_FOR_COMMAND);
        }
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.LISTENING_FOR_COMMAND);
        }
        updateServiceNotification("Active • Listening continuously for voice commands");

        // Automatically resume microphone recognition loop
        if (isAssistantActive && speechManager != null) {
            speechManager.resumeAfterTts();
        }
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();

                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(focusChange -> Log.d(TAG, "Audio focus changed: " + focusChange))
                        .build();

                audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error requesting audio focus", e);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error abandoning audio focus", e);
        }
    }

    private void updateServiceNotification(String statusText) {
        try {
            Notification notification = buildServiceNotification(statusText);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed updating notification: " + e.getMessage());
        }
    }

    private Notification buildServiceNotification(String statusText) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent mainPi = PendingIntent.getActivity(
                this, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent stopIntent = new Intent(this, ZavaVoiceService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_channel_name))
                .setContentText(statusText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(mainPi)
                .addAction(R.drawable.ic_clear, "Stop Assistant", stopPi)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Immediate foreground guarantee
        promoteToForegroundImmediately();

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopAssistant();
                return START_NOT_STICKY;
            } else if (ACTION_TRIGGER_MIC.equals(action)) {
                triggerManualListen();
                return START_STICKY;
            }
        }

        startAssistant();
        return START_STICKY;
    }

    private void showPermissionRestrictedNotification() {
        try {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            PendingIntent pi = PendingIntent.getActivity(
                    this, 3, settingsIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.notification_restricted_title))
                    .setContentText(getString(R.string.notification_restricted_text))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_restricted_text)))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID + 1, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing restricted permission notification", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.notification_channel_desc));
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification channel", e);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        promoteToForegroundImmediately();
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAssistant();
        if (voiceResponseManager != null) {
            voiceResponseManager.shutdown();
        }
    }
}
