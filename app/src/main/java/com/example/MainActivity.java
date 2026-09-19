package com.example;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Main dashboard for Hey Zava Voice Assistant.
 * Validates RECORD_AUDIO and POST_NOTIFICATIONS runtime permissions before starting
 * ZavaVoiceService to prevent ForegroundServiceStartNotAllowedException on Android 14+.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TextView tvAssistantStatus;
    private WaveformView waveformView;
    private TextView tvConversationalSpeech;
    private TextView tvUserSpeechPreview;
    private FrameLayout btnMicContainer;
    private ImageView ivMicIcon;
    private TextView tvMicHint;

    private MaterialCardView cardMasterSwitch;
    private MaterialSwitch switchVoiceAssistant;
    private TextView tvSwitchTitle;
    private TextView tvSwitchSubtitle;

    private RecyclerView rvCaptionsFeed;
    private CaptionsAdapter captionsAdapter;
    private TextView btnClearCaptions;

    private TextView tvMicStatusText;
    private TextView tvWakeStatusText;
    private TextView tvAccessibilityStatusText;
    private TextView tvServiceStatusText;

    private MaterialCardView cardAccessibilityStatus;
    private MaterialCardView cardAssistantStatus;

    private EditText etDirectCommand;
    private ImageButton btnSendCommand;
    private ImageButton btnHistory;
    private ImageButton btnSettings;

    private ZavaVoiceService assistantService;
    private boolean isServiceBound = false;
    private boolean isUserSwitchOn = true;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean audioGranted = Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO));
                boolean notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        Boolean.TRUE.equals(result.get(Manifest.permission.POST_NOTIFICATIONS));

                Log.d(TAG, "Permissions result: audio=" + audioGranted + ", notif=" + notifGranted);
                updateMicStatus(audioGranted);

                if (audioGranted && notifGranted) {
                    if (isUserSwitchOn) {
                        startAndBindAssistantService();
                    }
                } else {
                    Toast.makeText(this, "Microphone and notification permissions are required for voice assistant", Toast.LENGTH_LONG).show();
                    setSwitchState(false);
                }
            });

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onServiceConnected to ZavaVoiceService");
            ZavaVoiceService.LocalBinder localBinder = (ZavaVoiceService.LocalBinder) binder;
            assistantService = localBinder.getService();
            isServiceBound = true;

            assistantService.setServiceListener(new ZavaVoiceService.ServiceListener() {
                @Override
                public void onStateChanged(WakeWordManager.State state) {
                    runOnUiThread(() -> updateAssistantStateUI(state));
                }

                @Override
                public void onSpeechPartialResult(String text) {
                    runOnUiThread(() -> {
                        tvUserSpeechPreview.setText("“" + text + "…”");
                        tvUserSpeechPreview.setTextColor(getColor(R.color.neon_cyan));
                        captionsAdapter.updateLiveUserCaption(text);
                        rvCaptionsFeed.smoothScrollToPosition(Math.max(0, captionsAdapter.getItemCount() - 1));
                    });
                }

                @Override
                public void onSpeechRecognized(String text) {
                    runOnUiThread(() -> {
                        tvUserSpeechPreview.setText("“" + text + "”");
                        tvUserSpeechPreview.setTextColor(getColor(R.color.text_primary));
                        captionsAdapter.finalizeUserCaption(text);
                        rvCaptionsFeed.smoothScrollToPosition(Math.max(0, captionsAdapter.getItemCount() - 1));
                    });
                }

                @Override
                public void onCommandProcessed(ZavaCommand command, boolean success, String message) {
                    runOnUiThread(() -> {
                        tvConversationalSpeech.setText(message);
                        captionsAdapter.addAssistantCaption(message);
                        rvCaptionsFeed.smoothScrollToPosition(Math.max(0, captionsAdapter.getItemCount() - 1));
                    });
                }

                @Override
                public void onRmsLevel(float rms) {
                    runOnUiThread(() -> waveformView.setAudioLevel(rms));
                }

                @Override
                public void onPermissionRestricted() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Microphone restricted. Check permissions.", Toast.LENGTH_LONG).show();
                        updateMicStatus(false);
                        setSwitchState(false);
                    });
                }
            });

            if (isUserSwitchOn && hasRequiredForegroundPermissions()) {
                assistantService.startAssistant();
            }

            tvServiceStatusText.setText("ONLINE");
            tvServiceStatusText.setTextColor(getColor(R.color.neon_cyan));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            assistantService = null;
            isServiceBound = false;
            tvServiceStatusText.setText("OFFLINE");
            tvServiceStatusText.setTextColor(getColor(R.color.neon_coral));
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupCaptionsFeed();
        setupClickListeners();

        // Check and request permissions before starting foreground service
        if (hasRequiredForegroundPermissions()) {
            updateMicStatus(true);
            if (isUserSwitchOn) {
                startAndBindAssistantService();
            }
        } else {
            requestNecessaryPermissions();
        }
    }

    private void initViews() {
        tvAssistantStatus = findViewById(R.id.tvAssistantStatus);
        waveformView = findViewById(R.id.waveformView);
        tvConversationalSpeech = findViewById(R.id.tvConversationalSpeech);
        tvUserSpeechPreview = findViewById(R.id.tvUserSpeechPreview);
        btnMicContainer = findViewById(R.id.btnMicContainer);
        ivMicIcon = findViewById(R.id.ivMicIcon);
        tvMicHint = findViewById(R.id.tvMicHint);

        cardMasterSwitch = findViewById(R.id.cardMasterSwitch);
        switchVoiceAssistant = findViewById(R.id.switchVoiceAssistant);
        tvSwitchTitle = findViewById(R.id.tvSwitchTitle);
        tvSwitchSubtitle = findViewById(R.id.tvSwitchSubtitle);

        rvCaptionsFeed = findViewById(R.id.rvCaptionsFeed);
        btnClearCaptions = findViewById(R.id.btnClearCaptions);

        tvMicStatusText = findViewById(R.id.tvMicStatusText);
        tvWakeStatusText = findViewById(R.id.tvWakeStatusText);
        tvAccessibilityStatusText = findViewById(R.id.tvAccessibilityStatusText);
        tvServiceStatusText = findViewById(R.id.tvServiceStatusText);

        cardAccessibilityStatus = findViewById(R.id.cardAccessibilityStatus);
        cardAssistantStatus = findViewById(R.id.cardAssistantStatus);

        etDirectCommand = findViewById(R.id.etDirectCommand);
        btnSendCommand = findViewById(R.id.btnSendCommand);
        btnHistory = findViewById(R.id.btnHistory);
        btnSettings = findViewById(R.id.btnSettings);
    }

    private void setupCaptionsFeed() {
        captionsAdapter = new CaptionsAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvCaptionsFeed.setLayoutManager(layoutManager);
        rvCaptionsFeed.setAdapter(captionsAdapter);

        // Initial welcome caption
        captionsAdapter.addAssistantCaption("Namaste! Hey Zava is ready. Say “YouTube kholo” or any command.");

        btnClearCaptions.setOnClickListener(v -> captionsAdapter.clear());
    }

    private void setupClickListeners() {
        switchVoiceAssistant.setOnCheckedChangeListener((buttonView, isChecked) -> {
            onToggleMasterSwitch(isChecked);
        });

        cardMasterSwitch.setOnClickListener(v -> {
            switchVoiceAssistant.setChecked(!switchVoiceAssistant.isChecked());
        });

        btnMicContainer.setOnClickListener(v -> {
            if (!hasRequiredForegroundPermissions()) {
                requestNecessaryPermissions();
                return;
            }
            if (!isUserSwitchOn) {
                setSwitchState(true);
            } else if (assistantService != null) {
                assistantService.triggerManualListen();
            }
        });

        btnHistory.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, HistoryActivity.class));
        });

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        cardAccessibilityStatus.setOnClickListener(v -> {
            PermissionManager.openAccessibilitySettings(MainActivity.this);
        });

        cardAssistantStatus.setOnClickListener(v -> {
            switchVoiceAssistant.setChecked(!switchVoiceAssistant.isChecked());
        });

        btnSendCommand.setOnClickListener(v -> handleDirectCommandSubmit());

        etDirectCommand.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                handleDirectCommandSubmit();
                return true;
            }
            return false;
        });

        // Quick action chips
        setupChip(R.id.chipCmdYouTube, "YouTube kholo");
        setupChip(R.id.chipCmdWifi, "Wi-Fi settings kholo");
        setupChip(R.id.chipCmdCamera, "Camera kholo");
        setupChip(R.id.chipCmdVolume, "Volume badhao");
        setupChip(R.id.chipCmdScreenRead, "Screen read karo");
        setupChip(R.id.chipCmdGaming, "Gaming Mode");
    }

    private void onToggleMasterSwitch(boolean isChecked) {
        this.isUserSwitchOn = isChecked;
        if (isChecked) {
            tvSwitchTitle.setText("Voice Assistant: ON");
            tvSwitchTitle.setTextColor(getColor(R.color.neon_cyan));
            tvSwitchSubtitle.setText("Always-on continuous listening active");
            tvAssistantStatus.setText("◉ Always-On Listening Active");
            tvAssistantStatus.setTextColor(getColor(R.color.neon_cyan));
            tvServiceStatusText.setText("ONLINE");
            tvServiceStatusText.setTextColor(getColor(R.color.neon_cyan));

            if (!hasRequiredForegroundPermissions()) {
                requestNecessaryPermissions();
                return;
            }

            if (assistantService != null) {
                assistantService.startAssistant();
            } else {
                startAndBindAssistantService();
            }
            Toast.makeText(this, "Voice Assistant ON (Always Listening)", Toast.LENGTH_SHORT).show();
        } else {
            tvSwitchTitle.setText("Voice Assistant: OFF");
            tvSwitchTitle.setTextColor(getColor(R.color.text_muted));
            tvSwitchSubtitle.setText("Standby • Tap to activate always-on listening");
            tvAssistantStatus.setText("◉ Voice Assistant Paused (Standby)");
            tvAssistantStatus.setTextColor(getColor(R.color.text_muted));
            tvServiceStatusText.setText("OFFLINE");
            tvServiceStatusText.setTextColor(getColor(R.color.neon_coral));
            waveformView.setMode(WaveformView.Mode.IDLE);

            if (isServiceBound && assistantService != null) {
                assistantService.stopAssistant();
                try {
                    unbindService(serviceConnection);
                } catch (Exception ignored) {}
                isServiceBound = false;
            }

            Intent stopIntent = new Intent(this, ZavaVoiceService.class);
            stopIntent.setAction(ZavaVoiceService.ACTION_STOP);
            try {
                startService(stopIntent);
            } catch (Exception ignored) {}

            Toast.makeText(this, "Voice Assistant Paused", Toast.LENGTH_SHORT).show();
        }
    }

    private void setSwitchState(boolean checked) {
        if (switchVoiceAssistant.isChecked() != checked) {
            switchVoiceAssistant.setChecked(checked);
        }
    }

    private void setupChip(int resId, String commandText) {
        TextView chip = findViewById(resId);
        if (chip != null) {
            chip.setOnClickListener(v -> executeCommandString(commandText));
        }
    }

    private void handleDirectCommandSubmit() {
        String input = etDirectCommand.getText().toString().trim();
        if (!input.isEmpty()) {
            etDirectCommand.setText("");
            executeCommandString(input);
        }
    }

    private void executeCommandString(String command) {
        tvUserSpeechPreview.setText("“" + command + "”");
        captionsAdapter.finalizeUserCaption(command);
        rvCaptionsFeed.smoothScrollToPosition(Math.max(0, captionsAdapter.getItemCount() - 1));

        tvConversationalSpeech.setText("Processing command...");
        waveformView.setMode(WaveformView.Mode.PROCESSING);

        if (assistantService != null) {
            assistantService.processDirectCommand(command);
        } else {
            AppResolver resolver = new AppResolver(this);
            LocalCommandEngine engine = new LocalCommandEngine(this, resolver);
            ActionExecutor executor = new ActionExecutor(this, resolver);
            VoiceResponseManager voiceManager = new VoiceResponseManager(this);
            CommandHistoryManager history = new CommandHistoryManager(this);

            engine.plan(command, new AICommandEngine.Callback() {
                @Override
                public void onPlanReady(ZavaCommand plannedCommand) {
                    runOnUiThread(() -> {
                        String resp = plannedCommand.getConversationalResponse();
                        tvConversationalSpeech.setText(resp);
                        captionsAdapter.addAssistantCaption(resp);
                        rvCaptionsFeed.smoothScrollToPosition(Math.max(0, captionsAdapter.getItemCount() - 1));

                        waveformView.setMode(WaveformView.Mode.SPEAKING);

                        voiceManager.speak(resp, new VoiceResponseManager.SpeechCallback() {
                            @Override
                            public void onSpeechStarted() {}

                            @Override
                            public void onSpeechCompleted() {
                                executor.executeCommand(plannedCommand, new ActionExecutor.ExecutionCallback() {
                                    @Override
                                    public void onStepCompleted(ZavaAction action, boolean success, String message) {}

                                    @Override
                                    public void onAllCompleted(boolean overallSuccess, String finalMessage) {
                                        runOnUiThread(() -> {
                                            tvConversationalSpeech.setText(finalMessage);
                                            captionsAdapter.addAssistantCaption(finalMessage);
                                            rvCaptionsFeed.smoothScrollToPosition(Math.max(0, captionsAdapter.getItemCount() - 1));

                                            waveformView.setMode(WaveformView.Mode.IDLE);
                                            history.recordCommand(command, plannedCommand.getIntent(), overallSuccess, finalMessage);
                                            voiceManager.speak(finalMessage, () -> {});
                                        });
                                    }
                                });
                            }
                        });
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        tvConversationalSpeech.setText(errorMessage);
                        waveformView.setMode(WaveformView.Mode.IDLE);
                    });
                }
            });
        }
    }

    private void updateAssistantStateUI(WakeWordManager.State state) {
        if (!isUserSwitchOn) {
            tvAssistantStatus.setText("◉ Voice Assistant Paused");
            tvAssistantStatus.setTextColor(getColor(R.color.text_muted));
            waveformView.setMode(WaveformView.Mode.IDLE);
            btnMicContainer.setBackgroundResource(R.drawable.bg_mic_circle);
            return;
        }

        switch (state) {
            case IDLE:
            case LISTENING_FOR_WAKE:
            case LISTENING_FOR_COMMAND:
                tvAssistantStatus.setText("◉ Always-On Listening Active");
                tvAssistantStatus.setBackgroundResource(R.drawable.bg_status_pill_active);
                tvAssistantStatus.setTextColor(getColor(R.color.neon_cyan));
                waveformView.setMode(WaveformView.Mode.LISTENING);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_active);
                tvMicHint.setText("Always listening • speak anytime");
                break;

            case WAKE_DETECTED:
                tvAssistantStatus.setText("◉ Voice Detected!");
                tvAssistantStatus.setTextColor(getColor(R.color.neon_green));
                tvConversationalSpeech.setText("“Ji, boliye.”");
                waveformView.setMode(WaveformView.Mode.SPEAKING);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_active);
                break;

            case PROCESSING:
                tvAssistantStatus.setText("◉ Analyzing intent & planning...");
                tvAssistantStatus.setTextColor(getColor(R.color.neon_purple));
                waveformView.setMode(WaveformView.Mode.PROCESSING);
                break;

            case EXECUTING:
                tvAssistantStatus.setText("◉ Executing automation step...");
                tvAssistantStatus.setTextColor(getColor(R.color.neon_blue));
                waveformView.setMode(WaveformView.Mode.SPEAKING);
                break;

            case COMPLETED:
                tvAssistantStatus.setText("✔ Action Completed");
                tvAssistantStatus.setTextColor(getColor(R.color.neon_green));
                waveformView.setMode(WaveformView.Mode.IDLE);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_active);
                break;

            case FAILED:
                tvAssistantStatus.setText("✖ Execution Unsuccessful");
                tvAssistantStatus.setTextColor(getColor(R.color.neon_coral));
                waveformView.setMode(WaveformView.Mode.IDLE);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_active);
                break;
        }
    }

    private boolean hasRequiredForegroundPermissions() {
        boolean audio = PermissionManager.hasRecordAudioPermission(this);
        boolean notif = PermissionManager.hasNotificationPermission(this);
        return audio && notif;
    }

    private void requestNecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
            });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.RECORD_AUDIO
            });
        }

        if (!PermissionManager.isBatteryOptimizationIgnored(this)) {
            PermissionManager.requestIgnoreBatteryOptimizations(this);
        }
    }

    private void updateMicStatus(boolean granted) {
        if (granted) {
            tvMicStatusText.setText("READY");
            tvMicStatusText.setTextColor(getColor(R.color.neon_green));
        } else {
            tvMicStatusText.setText("DENIED");
            tvMicStatusText.setTextColor(getColor(R.color.neon_coral));
        }
    }

    private void startAndBindAssistantService() {
        // Enforce permission check before attempting Foreground Service launch
        if (!hasRequiredForegroundPermissions()) {
            Log.w(TAG, "Cannot start foreground service: missing required runtime permissions.");
            requestNecessaryPermissions();
            return;
        }

        try {
            Intent intent = new Intent(this, ZavaVoiceService.class);
            intent.setAction(ZavaVoiceService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "startAndBindAssistantService initiated successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed starting or binding ZavaVoiceService", e);
            Toast.makeText(this, "Could not start Voice Assistant service: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean audioGranted = PermissionManager.hasRecordAudioPermission(this);
        updateMicStatus(audioGranted);
        updateAccessibilityUI();

        if (isUserSwitchOn && hasRequiredForegroundPermissions() && !isServiceBound) {
            startAndBindAssistantService();
        }
    }

    private void updateAccessibilityUI() {
        boolean enabled = PermissionManager.isAccessibilityEnabled(this);
        if (enabled) {
            tvAccessibilityStatusText.setText("ACTIVE");
            tvAccessibilityStatusText.setTextColor(getColor(R.color.neon_green));
        } else {
            tvAccessibilityStatusText.setText("CONFIG");
            tvAccessibilityStatusText.setTextColor(getColor(R.color.neon_amber));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            try {
                unbindService(serviceConnection);
            } catch (Exception ignored) {}
            isServiceBound = false;
        }
    }
}
