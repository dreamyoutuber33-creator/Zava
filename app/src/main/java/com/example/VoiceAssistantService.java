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
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Sticky Foreground Service providing persistent, Always-On background voice listening.
 * Manages continuous speech recognition, audio focus, wake lock, intent execution, and live captions.
 */
public class VoiceAssistantService extends Service {

    private static final String TAG = "VoiceAssistantService";

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

    private SpeechRecognizerManager speechRecognizerManager;
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

    private boolean isAssistantActive = false;

    public class LocalBinder extends Binder {
        public VoiceAssistantService getService() {
            return VoiceAssistantService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeyZava::VoiceServiceWakeLock");
            wakeLock.setReferenceCounted(false);
        }

        appResolver = new AppResolver(this);
        commandEngine = new LocalCommandEngine(this, appResolver);
        actionExecutor = new ActionExecutor(this, appResolver);
        historyManager = new CommandHistoryManager(this);
        geminiService = new GeminiService();

        wakeWordManager = new WakeWordManager(this);
        voiceResponseManager = new VoiceResponseManager(this);
        speechRecognizerManager = new SpeechRecognizerManager(this);

        setupSpeechRecognizer();
    }

    private void setupSpeechRecognizer() {
        speechRecognizerManager.setListener(new SpeechRecognizerManager.SpeechListener() {
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

                // Pause speech recognizer so it doesn't transcribe TTS speech output
                speechRecognizerManager.pauseForTts();

                handleCommandSpeech(recognizedText);
            }

            @Override
            public void onSpeechError(int errorCode, String errorMessage) {
                Log.w(TAG, "onSpeechError: code " + errorCode + " - " + errorMessage);

                if (errorCode == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                        !PermissionManager.hasRecordAudioPermission(VoiceAssistantService.this)) {
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
            if (serviceListener != null) serviceListener.onPermissionRestricted();
            return;
        }

        isAssistantActive = true;
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(12 * 60 * 60 * 1000L); // 12 hours max hold
        }

        requestAudioFocus();
        startForegroundServiceNotification();

        wakeWordManager.setState(WakeWordManager.State.LISTENING_FOR_COMMAND);
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.LISTENING_FOR_COMMAND);
        }

        speechRecognizerManager.startContinuousListening(true);
        updateServiceNotification("Active • Listening continuously for voice commands");
    }

    public void stopAssistant() {
        isAssistantActive = false;
        speechRecognizerManager.stopListening();
        abandonAudioFocus();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        wakeWordManager.setState(WakeWordManager.State.IDLE);
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.IDLE);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    public void triggerManualListen() {
        if (!isAssistantActive) {
            startAssistant();
        } else {
            speechRecognizerManager.startContinuousListening(true);
        }
    }

    public void processDirectCommand(String text) {
        speechRecognizerManager.pauseForTts();
        handleCommandSpeech(text);
    }

    private void handleCommandSpeech(String commandText) {
        wakeWordManager.setState(WakeWordManager.State.PROCESSING);
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.PROCESSING);
        }
        updateServiceNotification("Processing: “" + commandText + "”");

        commandEngine.plan(commandText, new AICommandEngine.Callback() {
            @Override
            public void onPlanReady(ZavaCommand plannedCommand) {
                if (ZavaCommand.INTENT_UNKNOWN.equals(plannedCommand.getIntent())) {
                    geminiService.generateResponse(commandText, new GeminiService.GeminiCallback() {
                        @Override
                        public void onResponse(String text) {
                            plannedCommand.setConversationalResponse(text);
                            executePlannedCommand(plannedCommand);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Log.d(TAG, "Gemini fallback error: " + errorMessage);
                            executePlannedCommand(plannedCommand);
                        }
                    });
                } else {
                    executePlannedCommand(plannedCommand);
                }
            }

            @Override
            public void onError(String errorMessage) {
                wakeWordManager.setState(WakeWordManager.State.FAILED);
                voiceResponseManager.speak("Command process karne me samasya aayi.", () -> {
                    onAssistantInteractionFinished();
                });
            }
        });
    }

    private void executePlannedCommand(ZavaCommand command) {
        wakeWordManager.setState(WakeWordManager.State.EXECUTING);
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.EXECUTING);
        }

        String resp = command.getConversationalResponse();
        updateServiceNotification("Zava: " + resp);

        voiceResponseManager.speak(resp, new VoiceResponseManager.SpeechCallback() {
            @Override
            public void onSpeechStarted() {}

            @Override
            public void onSpeechCompleted() {
                actionExecutor.executeCommand(command, new ActionExecutor.ExecutionCallback() {
                    @Override
                    public void onStepCompleted(ZavaAction action, boolean success, String message) {}

                    @Override
                    public void onAllCompleted(boolean overallSuccess, String finalMessage) {
                        wakeWordManager.setState(overallSuccess ? WakeWordManager.State.COMPLETED : WakeWordManager.State.FAILED);

                        // Speak feedback
                        voiceResponseManager.speak(finalMessage, () -> {
                            onAssistantInteractionFinished();
                        });

                        historyManager.recordCommand(command.getRawSpeech(), command.getIntent(), overallSuccess, finalMessage);

                        if (serviceListener != null) {
                            serviceListener.onCommandProcessed(command, overallSuccess, finalMessage);
                            serviceListener.onStateChanged(wakeWordManager.getState());
                        }
                    }
                });
            }
        });
    }

    private void onAssistantInteractionFinished() {
        wakeWordManager.setState(WakeWordManager.State.LISTENING_FOR_COMMAND);
        updateServiceNotification("Active • Listening continuously for voice commands");

        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.LISTENING_FOR_COMMAND);
        }

        // Resume continuous listening now that TTS is finished
        if (isAssistantActive) {
            speechRecognizerManager.resumeAfterTts();
        }
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }

    private void updateServiceNotification(String statusText) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildServiceNotification(statusText));
        }
    }

    private Notification buildServiceNotification(String statusText) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent mainPi = PendingIntent.getActivity(
                this, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Action button to stop assistant
        Intent stopIntent = new Intent(this, VoiceAssistantService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HEY ZAVA - Voice Assistant Active")
                .setContentText(statusText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(statusText))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(mainPi)
                .addAction(R.drawable.ic_clear, "Stop Assistant", stopPi)
                .build();
    }

    private void startForegroundServiceNotification() {
        Notification notification = buildServiceNotification("Active • Listening continuously for voice commands");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
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
