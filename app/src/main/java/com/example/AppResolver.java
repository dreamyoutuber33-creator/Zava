package com.example;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dynamically indexes and resolves installed applications using PackageManager
 * with fuzzy alias matching and multi-language support.
 */
public class AppResolver {

    public static class AppInfo {
        public final String label;
        public final String packageName;
        public final Intent launchIntent;

        public AppInfo(String label, String packageName, Intent launchIntent) {
            this.label = label;
            this.packageName = packageName;
            this.launchIntent = launchIntent;
        }
    }

    private final Context context;
    private final List<AppInfo> installedApps = new ArrayList<>();
    private final Map<String, String> commonAliases = new HashMap<>();

    public AppResolver(Context context) {
        this.context = context.getApplicationContext();
        initAliases();
        reloadApps();
    }

    private void initAliases() {
        commonAliases.put("yt", "youtube");
        commonAliases.put("यूट्यूब", "youtube");
        commonAliases.put("insta", "instagram");
        commonAliases.put("इंस्टाग्राम", "instagram");
        commonAliases.put("wa", "whatsapp");
        commonAliases.put("व्हाट्सएप", "whatsapp");
        commonAliases.put("व्हाट्सऐप", "whatsapp");
        commonAliases.put("chrome", "chrome");
        commonAliases.put("browser", "chrome");
        commonAliases.put("इंटरनेट", "chrome");
        commonAliases.put("camera", "camera");
        commonAliases.put("कैमरा", "camera");
        commonAliases.put("photos", "photos");
        commonAliases.put("gallery", "gallery");
        commonAliases.put("गैलरी", "gallery");
        commonAliases.put("settings", "settings");
        commonAliases.put("सेटिंग्स", "settings");
        commonAliases.put("maps", "maps");
        commonAliases.put("गूगल मैप्स", "maps");
        commonAliases.put("calc", "calculator");
        commonAliases.put("calculator", "calculator");
        commonAliases.put("कैलकुलेटर", "calculator");
        commonAliases.put("phone", "dialer");
        commonAliases.put("dialer", "dialer");
        commonAliases.put("फोन", "dialer");
        commonAliases.put("messages", "messaging");
        commonAliases.put("sms", "messaging");
        commonAliases.put("मैसेज", "messaging");
        commonAliases.put("clock", "clock");
        commonAliases.put("alarm", "clock");
        commonAliases.put("घड़ी", "clock");
        commonAliases.put("अलार्म", "clock");
        commonAliases.put("spotify", "spotify");
        commonAliases.put("स्पॉटिफाई", "spotify");
        commonAliases.put("play store", "vending");
        commonAliases.put("प्ले स्टोर", "vending");
    }

    public synchronized void reloadApps() {
        installedApps.clear();
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolvedList = pm.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo ri : resolvedList) {
            if (ri.activityInfo != null) {
                String pkg = ri.activityInfo.packageName;
                CharSequence charSeq = ri.loadLabel(pm);
                String label = charSeq != null ? charSeq.toString() : pkg;
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    installedApps.add(new AppInfo(label, pkg, launch));
                }
            }
        }
    }

    public AppInfo resolveApp(String nameQuery) {
        if (nameQuery == null || nameQuery.trim().isEmpty()) {
            return null;
        }

        String target = nameQuery.trim().toLowerCase(Locale.ROOT);
        // Check alias mapping
        if (commonAliases.containsKey(target)) {
            target = commonAliases.get(target);
        }

        // 1. Exact match on app label
        for (AppInfo app : installedApps) {
            if (app.label.equalsIgnoreCase(target)) {
                return app;
            }
        }

        // 2. Starts with / contains match on label
        for (AppInfo app : installedApps) {
            String appLower = app.label.toLowerCase(Locale.ROOT);
            if (appLower.contains(target) || target.contains(appLower)) {
                return app;
            }
        }

        // 3. Match on package name
        for (AppInfo app : installedApps) {
            String pkgLower = app.packageName.toLowerCase(Locale.ROOT);
            if (pkgLower.contains(target)) {
                return app;
            }
        }

        // 4. Special fallback mappings for known system apps if package queries are restricted
        PackageManager pm = context.getPackageManager();
        if (target.contains("settings") || target.contains("सेटिंग्स")) {
            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
            return new AppInfo("Settings", "com.android.settings", intent);
        } else if (target.contains("wifi") || target.contains("wi-fi") || target.contains("वाईफाई")) {
            Intent intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
            return new AppInfo("Wi-Fi Settings", "com.android.settings", intent);
        } else if (target.contains("bluetooth") || target.contains("ब्लूटूथ")) {
            Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            return new AppInfo("Bluetooth Settings", "com.android.settings", intent);
        } else if (target.contains("youtube") || target.contains("यूट्यूब")) {
            Intent intent = pm.getLaunchIntentForPackage("com.google.android.youtube");
            if (intent == null) {
                intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"));
            }
            return new AppInfo("YouTube", "com.google.android.youtube", intent);
        } else if (target.contains("whatsapp") || target.contains("व्हाट्सएप") || target.contains("व्हाट्सऐप")) {
            Intent intent = pm.getLaunchIntentForPackage("com.whatsapp");
            if (intent == null) {
                intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://api.whatsapp.com"));
            }
            return new AppInfo("WhatsApp", "com.whatsapp", intent);
        } else if (target.contains("chrome") || target.contains("क्रोम") || target.contains("browser")) {
            Intent intent = pm.getLaunchIntentForPackage("com.android.chrome");
            if (intent == null) {
                intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"));
            }
            return new AppInfo("Chrome", "com.android.chrome", intent);
        } else if (target.contains("camera") || target.contains("कैमरा")) {
            Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
            return new AppInfo("Camera", "camera", intent);
        } else if (target.contains("maps") || target.contains("मैप्स")) {
            Intent intent = pm.getLaunchIntentForPackage("com.google.android.apps.maps");
            if (intent == null) {
                intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://maps.google.com"));
            }
            return new AppInfo("Google Maps", "com.google.android.apps.maps", intent);
        }

        return null;
    }

    public List<AppInfo> getInstalledApps() {
        return new ArrayList<>(installedApps);
    }
}
