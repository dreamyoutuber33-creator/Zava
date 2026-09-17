package com.example;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Android Foreground Service enabling background wake-word listening and voice automation.
 */
public class VoiceAssistantService extends Service {

    private static final String TAG = "VoiceAssistantService";
    public static final String CHANNEL_ID = "hey_zava_service_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.example.action.START_ASSISTANT";
    public static final String ACTION_STOP = "com.example.action.STOP_ASSISTANT";
    public static final String ACTION_TRIGGER_MIC = "com.example.action.TRIGGER_MIC";

    public interface ServiceListener {
        void onStateChanged(WakeWordManager.State state);
        void onSpeechRecognized(String text);
        void onCommandProcessed(ZavaCommand command, boolean success, String message);
        void onRmsLevel(float rms);
        void onPermissionRestricted();
    }

    public class LocalBinder extends Binder {
        public VoiceAssistantService getService() {
            return VoiceAssistantService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private ServiceListener serviceListener;

    private WakeWordManager wakeWordManager;
    private SpeechRecognizerManager speechRecognizerManager;
    private AppResolver appResolver;
    private LocalCommandEngine commandEngine;
    private ActionExecutor actionExecutor;
    private VoiceResponseManager voiceResponseManager;
    private CommandHistoryManager historyManager;

    private boolean isListeningForWake = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        appResolver = new AppResolver(this);
        commandEngine = new LocalCommandEngine(this, appResolver);
        actionExecutor = new ActionExecutor(this, appResolver);
        voiceResponseManager = new VoiceResponseManager(this);
        historyManager = new CommandHistoryManager(this);
        wakeWordManager = new WakeWordManager(this);
        speechRecognizerManager = new SpeechRecognizerManager(this);

        initSpeechPipeline();
    }

    private void initSpeechPipeline() {
        speechRecognizerManager.setListener(new SpeechRecognizerManager.SpeechListener() {
            @Override
            public void onRmsChanged(float rmsdB) {
                if (serviceListener != null) {
                    serviceListener.onRmsLevel(rmsdB);
                }
            }

            @Override
            public void onSpeechResult(String recognizedText) {
                if (serviceListener != null) {
                    serviceListener.onSpeechRecognized(recognizedText);
                }

                if (wakeWordManager.getState() == WakeWordManager.State.LISTENING_FOR_WAKE) {
                    if (wakeWordManager.isWakePhrase(recognizedText)) {
                        handleWakeWordDetected();
                    } else {
                        // Continue listening for wake word
                        if (isListeningForWake) {
                            listenForWakeWord();
                        }
                    }
                } else if (wakeWordManager.getState() == WakeWordManager.State.LISTENING_FOR_COMMAND) {
                    handleCommandSpeech(recognizedText);
                }
            }

            @Override
            public void onSpeechError(int errorCode, String errorMessage) {
                Log.w(TAG, "onSpeechError code: " + errorCode + " - " + errorMessage);
                // Check if Android restricted microphone access (ERROR_INSUFFICIENT_PERMISSIONS or audio error)
                if (errorCode == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                        !PermissionManager.hasRecordAudioPermission(VoiceAssistantService.this)) {
                    showPermissionRestrictedNotification();
                    if (serviceListener != null) {
                        serviceListener.onPermissionRestricted();
                    }
                    return;
                }

                if (wakeWordManager.getState() == WakeWordManager.State.LISTENING_FOR_WAKE) {
                    if (isListeningForWake) {
                        // Reschedule with backoff if it is an audio hardware glitch
                        long delay = (errorCode == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY) ? 1000 : 300;
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (isListeningForWake) {
                                listenForWakeWord();
                            }
                        }, delay);
                    }
                } else if (wakeWordManager.getState() == WakeWordManager.State.LISTENING_FOR_COMMAND) {
                    wakeWordManager.setState(WakeWordManager.State.FAILED);
                    voiceResponseManager.speak(errorMessage, () -> {
                        wakeWordManager.setState(WakeWordManager.State.IDLE);
                        if (isListeningForWake) listenForWakeWord();
                    });
                }
            }

            @Override
            public void onReadyForSpeech() {}
        });
    }

    public void setServiceListener(ServiceListener listener) {
        this.serviceListener = listener;
    }

    public void startListeningWake() {
        isListeningForWake = true;
        listenForWakeWord();
    }

    public void stopListeningWake() {
        isListeningForWake = false;
        speechRecognizerManager.stopListening();
        wakeWordManager.setState(WakeWordManager.State.IDLE);
    }

    private void listenForWakeWord() {
        if (!PermissionManager.hasRecordAudioPermission(this)) return;
        wakeWordManager.setState(WakeWordManager.State.LISTENING_FOR_WAKE);
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.LISTENING_FOR_WAKE);
        }
        speechRecognizerManager.startListening(true);
    }

    public void triggerManualListen() {
        handleWakeWordDetected();
    }

    private void handleWakeWordDetected() {
        wakeWordManager.setState(WakeWordManager.State.WAKE_DETECTED);
        wakeWordManager.playWakeChime();

        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.WAKE_DETECTED);
        }

        // Conversational acknowledgment: "Ji, boliye."
        voiceResponseManager.speak("Ji, boliye.", new VoiceResponseManager.SpeechCallback() {
            @Override
            public void onSpeechStarted() {}

            @Override
            public void onSpeechCompleted() {
                listenForCommand();
            }
        });
    }

    private void listenForCommand() {
        wakeWordManager.setState(WakeWordManager.State.LISTENING_FOR_COMMAND);
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.LISTENING_FOR_COMMAND);
        }
        speechRecognizerManager.startListening(false);
    }

    public void processDirectCommand(String text) {
        handleCommandSpeech(text);
    }

    private void handleCommandSpeech(String commandText) {
        wakeWordManager.setState(WakeWordManager.State.PROCESSING);
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.PROCESSING);
        }

        commandEngine.plan(commandText, new AICommandEngine.Callback() {
            @Override
            public void onPlanReady(ZavaCommand plannedCommand) {
                executePlannedCommand(plannedCommand);
            }

            @Override
            public void onError(String errorMessage) {
                wakeWordManager.setState(WakeWordManager.State.FAILED);
                voiceResponseManager.speak("Command process karne me samasya aayi.", () -> {
                    wakeWordManager.setState(WakeWordManager.State.IDLE);
                });
            }
        });
    }

    private void executePlannedCommand(ZavaCommand command) {
        wakeWordManager.setState(WakeWordManager.State.EXECUTING);
        if (serviceListener != null) {
            serviceListener.onStateChanged(WakeWordManager.State.EXECUTING);
        }

        // Voice pre-acknowledgment if available
        String resp = command.getConversationalResponse();
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
                            wakeWordManager.setState(WakeWordManager.State.IDLE);
                            if (isListeningForWake) {
                                listenForWakeWord();
                            }
                        });

                        // Record to history
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopListeningWake();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_TRIGGER_MIC.equals(action)) {
                triggerManualListen();
                return START_STICKY;
            }
        }

        startForegroundServiceNotification();
        startListeningWake();
        return START_STICKY;
    }

    private void startForegroundServiceNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Action 1: Trigger Mic directly from Notification
        Intent micIntent = new Intent(this, VoiceAssistantService.class);
        micIntent.setAction(ACTION_TRIGGER_MIC);
        PendingIntent micPendingIntent = PendingIntent.getService(
                this, 1, micIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Action 2: Stop / Pause Zava Service
        Intent stopIntent = new Intent(this, VoiceAssistantService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 2, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Hey Zava Active")
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(R.drawable.ic_mic, getString(R.string.notification_action_mic), micPendingIntent)
                .addAction(R.drawable.ic_clear, getString(R.string.notification_action_pause), stopPendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
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
        stopListeningWake();
        if (wakeWordManager != null) wakeWordManager.release();
        if (voiceResponseManager != null) voiceResponseManager.shutdown();
    }
}
