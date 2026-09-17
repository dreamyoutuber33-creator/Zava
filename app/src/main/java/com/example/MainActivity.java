package com.example;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
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
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    private TextView tvAssistantStatus;
    private WaveformView waveformView;
    private TextView tvConversationalSpeech;
    private TextView tvUserSpeechPreview;
    private FrameLayout btnMicContainer;
    private ImageView ivMicIcon;
    private TextView tvMicHint;

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

    private VoiceAssistantService assistantService;
    private boolean isServiceBound = false;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean audioGranted = Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO));
                updateMicStatus(audioGranted);
                if (audioGranted) {
                    startAndBindAssistantService();
                } else {
                    Toast.makeText(this, "Microphone permission is required for voice commands", Toast.LENGTH_LONG).show();
                }
            });

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            VoiceAssistantService.LocalBinder localBinder = (VoiceAssistantService.LocalBinder) binder;
            assistantService = localBinder.getService();
            isServiceBound = true;

            assistantService.setServiceListener(new VoiceAssistantService.ServiceListener() {
                @Override
                public void onStateChanged(WakeWordManager.State state) {
                    runOnUiThread(() -> updateAssistantStateUI(state));
                }

                @Override
                public void onSpeechRecognized(String text) {
                    runOnUiThread(() -> {
                        tvUserSpeechPreview.setText("“" + text + "”");
                    });
                }

                @Override
                public void onCommandProcessed(ZavaCommand command, boolean success, String message) {
                    runOnUiThread(() -> {
                        tvConversationalSpeech.setText(message);
                    });
                }

                @Override
                public void onRmsLevel(float rms) {
                    runOnUiThread(() -> {
                        waveformView.setAudioLevel(rms);
                    });
                }

                @Override
                public void onPermissionRestricted() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Microphone access restricted. Check permissions in settings.", Toast.LENGTH_LONG).show();
                        updateMicStatus(false);
                    });
                }
            });

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
        setupClickListeners();
        requestNecessaryPermissions();
    }

    private void initViews() {
        tvAssistantStatus = findViewById(R.id.tvAssistantStatus);
        waveformView = findViewById(R.id.waveformView);
        tvConversationalSpeech = findViewById(R.id.tvConversationalSpeech);
        tvUserSpeechPreview = findViewById(R.id.tvUserSpeechPreview);
        btnMicContainer = findViewById(R.id.btnMicContainer);
        ivMicIcon = findViewById(R.id.ivMicIcon);
        tvMicHint = findViewById(R.id.tvMicHint);

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

    private void setupClickListeners() {
        btnMicContainer.setOnClickListener(v -> {
            if (!PermissionManager.hasRecordAudioPermission(this)) {
                requestNecessaryPermissions();
                return;
            }
            if (assistantService != null) {
                assistantService.triggerManualListen();
            } else {
                startAndBindAssistantService();
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
            toggleAssistantService();
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
        tvConversationalSpeech.setText("Processing command...");
        waveformView.setMode(WaveformView.Mode.PROCESSING);

        if (assistantService != null) {
            assistantService.processDirectCommand(command);
        } else {
            // Local fallback execution if service is not yet bound
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
                                            waveformView.setMode(WaveformView.Mode.IDLE);
                                            history.recordCommand(command, plannedCommand.getIntent(), overallSuccess, finalMessage);
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
        switch (state) {
            case IDLE:
                tvAssistantStatus.setText("◉ Standby (Wake Ready)");
                tvAssistantStatus.setBackgroundResource(R.drawable.bg_status_pill);
                tvAssistantStatus.setTextColor(getColor(R.color.text_secondary));
                waveformView.setMode(WaveformView.Mode.IDLE);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_circle);
                tvMicHint.setText(R.string.tap_to_speak);
                break;

            case LISTENING_FOR_WAKE:
                tvAssistantStatus.setText("◉ Listening for “Hey Zava”");
                tvAssistantStatus.setBackgroundResource(R.drawable.bg_status_pill_active);
                tvAssistantStatus.setTextColor(getColor(R.color.neon_cyan));
                waveformView.setMode(WaveformView.Mode.IDLE);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_circle);
                tvMicHint.setText("Say “Hey Zava” or tap mic");
                break;

            case WAKE_DETECTED:
                tvAssistantStatus.setText("◉ Wake Phrase Recognized!");
                tvAssistantStatus.setTextColor(getColor(R.color.neon_green));
                tvConversationalSpeech.setText("“Ji, boliye.”");
                waveformView.setMode(WaveformView.Mode.SPEAKING);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_active);
                break;

            case LISTENING_FOR_COMMAND:
                tvAssistantStatus.setText("◉ Listening for your command...");
                tvAssistantStatus.setTextColor(getColor(R.color.neon_coral));
                waveformView.setMode(WaveformView.Mode.LISTENING);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_active);
                tvMicHint.setText("Speak now...");
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
                tvAssistantStatus.setText("✔ Action Finished");
                tvAssistantStatus.setTextColor(getColor(R.color.neon_green));
                waveformView.setMode(WaveformView.Mode.IDLE);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_circle);
                break;

            case FAILED:
                tvAssistantStatus.setText("✖ Execution Unsuccessful");
                tvAssistantStatus.setTextColor(getColor(R.color.neon_coral));
                waveformView.setMode(WaveformView.Mode.IDLE);
                btnMicContainer.setBackgroundResource(R.drawable.bg_mic_circle);
                break;
        }
    }

    private void toggleAssistantService() {
        if (isServiceBound && assistantService != null) {
            unbindService(serviceConnection);
            isServiceBound = false;
            Intent stopIntent = new Intent(this, VoiceAssistantService.class);
            stopIntent.setAction(VoiceAssistantService.ACTION_STOP);
            startService(stopIntent);
            tvServiceStatusText.setText("OFFLINE");
            tvServiceStatusText.setTextColor(getColor(R.color.neon_coral));
            Toast.makeText(this, "Assistant Service Paused", Toast.LENGTH_SHORT).show();
        } else {
            startAndBindAssistantService();
            Toast.makeText(this, "Assistant Service Started", Toast.LENGTH_SHORT).show();
        }
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
        Intent intent = new Intent(this, VoiceAssistantService.class);
        intent.setAction(VoiceAssistantService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMicStatus(PermissionManager.hasRecordAudioPermission(this));
        updateAccessibilityUI();

        if (PermissionManager.hasRecordAudioPermission(this) && !isServiceBound) {
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
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}
