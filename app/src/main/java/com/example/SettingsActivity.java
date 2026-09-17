package com.example;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchChime;
    private TextView tvAccessibilityDesc;
    private TextView tvBatteryDesc;
    private TextView tvMicPermissionDesc;
    private TextView tvAppCount;
    private Button btnEnableAccessibility;
    private Button btnConfigureBattery;
    private Button btnOpenAppSettings;
    private Button btnReloadApps;
    private Button btnClearAllHistory;

    private AppResolver appResolver;
    private CommandHistoryManager historyManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("zava_settings", MODE_PRIVATE);
        appResolver = new AppResolver(this);
        historyManager = new CommandHistoryManager(this);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        switchChime = findViewById(R.id.switchChime);
        tvAccessibilityDesc = findViewById(R.id.tvAccessibilityDesc);
        tvBatteryDesc = findViewById(R.id.tvBatteryDesc);
        tvMicPermissionDesc = findViewById(R.id.tvMicPermissionDesc);
        tvAppCount = findViewById(R.id.tvAppCount);
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility);
        btnConfigureBattery = findViewById(R.id.btnConfigureBattery);
        btnOpenAppSettings = findViewById(R.id.btnOpenAppSettings);
        btnReloadApps = findViewById(R.id.btnReloadApps);
        btnClearAllHistory = findViewById(R.id.btnClearAllHistory);

        boolean chimeEnabled = prefs.getBoolean("audio_chime_enabled", true);
        switchChime.setChecked(chimeEnabled);
        switchChime.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("audio_chime_enabled", isChecked).apply();
        });

        btnEnableAccessibility.setOnClickListener(v -> {
            PermissionManager.openAccessibilitySettings(SettingsActivity.this);
        });

        btnConfigureBattery.setOnClickListener(v -> {
            PermissionManager.requestIgnoreBatteryOptimizations(SettingsActivity.this);
        });

        btnOpenAppSettings.setOnClickListener(v -> {
            PermissionManager.openAppSettings(SettingsActivity.this);
        });

        btnReloadApps.setOnClickListener(v -> {
            appResolver.reloadApps();
            updateAppCount();
            Toast.makeText(SettingsActivity.this, "App index refreshed", Toast.LENGTH_SHORT).show();
        });

        btnClearAllHistory.setOnClickListener(v -> {
            historyManager.clearHistory();
            Toast.makeText(SettingsActivity.this, "Command history cleared", Toast.LENGTH_SHORT).show();
        });

        updateAppCount();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
        updateBatteryStatus();
        updateMicPermissionStatus();
    }

    private void updateBatteryStatus() {
        boolean unrestricted = PermissionManager.isBatteryOptimizationIgnored(this);
        if (unrestricted) {
            tvBatteryDesc.setText("Status: UNRESTRICTED (Assistant will stay active in background)");
            tvBatteryDesc.setTextColor(getColor(R.color.neon_green));
            btnConfigureBattery.setText("Active");
            btnConfigureBattery.setEnabled(false);
        } else {
            tvBatteryDesc.setText("Status: RESTRICTED (Android may kill background mic listening)");
            tvBatteryDesc.setTextColor(getColor(R.color.neon_amber));
            btnConfigureBattery.setText("Unrestrict");
            btnConfigureBattery.setEnabled(true);
        }
    }

    private void updateMicPermissionStatus() {
        boolean hasMic = PermissionManager.hasRecordAudioPermission(this);
        if (hasMic) {
            tvMicPermissionDesc.setText("Status: GRANTED (Microphone accessible)");
            tvMicPermissionDesc.setTextColor(getColor(R.color.neon_green));
        } else {
            tvMicPermissionDesc.setText("Status: RESTRICTED (Tap App Info to allow Microphone)");
            tvMicPermissionDesc.setTextColor(getColor(R.color.neon_coral));
        }
    }

    private void updateAccessibilityStatus() {
        boolean enabled = PermissionManager.isAccessibilityEnabled(this);
        if (enabled) {
            tvAccessibilityDesc.setText("Status: ACTIVE (UI Automation Ready)");
            tvAccessibilityDesc.setTextColor(getColor(R.color.neon_green));
            btnEnableAccessibility.setText("Settings");
        } else {
            tvAccessibilityDesc.setText("Status: DISABLED (Required for screen clicks/scroll)");
            tvAccessibilityDesc.setTextColor(getColor(R.color.neon_amber));
            btnEnableAccessibility.setText("Enable");
        }
    }

    private void updateAppCount() {
        int count = appResolver.getInstalledApps().size();
        tvAppCount.setText(count + " installed applications indexed");
    }
}
