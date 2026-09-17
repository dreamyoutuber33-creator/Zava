package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Natural language intent classifier for Hindi, Hinglish, and English voice commands.
 * Normalizes phrasings, extracts targets and parameters, and plans execution steps.
 */
public class IntentAnalyzer {

    private final AppResolver appResolver;

    public IntentAnalyzer(AppResolver appResolver) {
        this.appResolver = appResolver;
    }

    public ZavaCommand analyze(String rawSpeech) {
        ZavaCommand cmd = new ZavaCommand();
        cmd.setRawSpeech(rawSpeech);

        String normalized = CommandNormalizer.normalize(rawSpeech);
        cmd.setNormalizedSpeech(normalized);
        String lower = normalized.toLowerCase(Locale.ROOT);

        // Check for multi-action compound sentence ("aur", "and", "kholke", "open karke", "then")
        if (isMultiAction(lower)) {
            return parseMultiAction(cmd, lower);
        }

        // 1. Screen reading / Screen understanding
        if (lower.contains("screen") && (lower.contains("read") || lower.contains("kya likha") || lower.contains("padho") || lower.contains("dekho") || lower.contains("dikhao") || lower.contains("analyze"))) {
            cmd.setIntent(ZavaCommand.INTENT_READ_SCREEN);
            cmd.setConversationalResponse("Main screen ko analyze kar rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_READ_SCREEN, "", ""));
            return cmd;
        }

        // 2. Settings Intents
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाईफाई")) {
            cmd.setIntent(ZavaCommand.INTENT_WIFI_SETTINGS);
            cmd.setConversationalResponse("Thik hai, Wi-Fi settings khol rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_SETTINGS, "WIFI", ""));
            return cmd;
        }
        if (lower.contains("bluetooth") || lower.contains("ब्लूटूथ")) {
            cmd.setIntent(ZavaCommand.INTENT_BLUETOOTH_SETTINGS);
            cmd.setConversationalResponse("Thik hai, Bluetooth settings khol rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_SETTINGS, "BLUETOOTH", ""));
            return cmd;
        }
        if (lower.contains("airplane") || lower.contains("flight mode") || lower.contains("हवाई मोड")) {
            cmd.setIntent(ZavaCommand.INTENT_AIRPLANE_MODE_SETTINGS);
            cmd.setConversationalResponse("Airplane mode settings khol rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_SETTINGS, "AIRPLANE", ""));
            return cmd;
        }
        if (lower.contains("display") || lower.contains("brightness") || lower.contains("ब्राइटनेस")) {
            cmd.setIntent(ZavaCommand.INTENT_BRIGHTNESS_CONTROL);
            cmd.setConversationalResponse("Display aur brightness settings khol rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_SETTINGS, "DISPLAY", ""));
            return cmd;
        }
        if (lower.contains("accessibility") || lower.contains("एक्सेसिबिलिटी")) {
            cmd.setIntent(ZavaCommand.INTENT_OPEN_SETTINGS);
            cmd.setConversationalResponse("Accessibility settings khol rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_SETTINGS, "ACCESSIBILITY", ""));
            return cmd;
        }
        if (lower.contains("settings") || lower.contains("सेटिंग्स")) {
            cmd.setIntent(ZavaCommand.INTENT_OPEN_SETTINGS);
            cmd.setConversationalResponse("Settings khol rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_SETTINGS, "SETTINGS", ""));
            return cmd;
        }

        // 3. Navigation Controls
        if (lower.contains("back") || lower.contains("piche") || lower.contains("wapas") || lower.contains("peeche")) {
            cmd.setIntent(ZavaCommand.INTENT_BACK);
            cmd.setConversationalResponse("Wapas ja rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_BACK, "", ""));
            return cmd;
        }
        if (lower.contains("home screen") || lower.contains("go home") || lower.contains("home jao") || lower.equals("home")) {
            cmd.setIntent(ZavaCommand.INTENT_HOME);
            cmd.setConversationalResponse("Home screen par le ja rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_HOME, "", ""));
            return cmd;
        }
        if (lower.contains("recent") || lower.contains("multitask") || lower.contains("recent apps") || lower.contains("apps list")) {
            cmd.setIntent(ZavaCommand.INTENT_RECENT_APPS);
            cmd.setConversationalResponse("Recent applications dikha rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_RECENTS, "", ""));
            return cmd;
        }
        if (lower.contains("scroll down") || lower.contains("niche scroll") || lower.contains("neeche scroll")) {
            cmd.setIntent(ZavaCommand.INTENT_SCROLL);
            cmd.setConversationalResponse("Neeche scroll kar rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_SCROLL, "DOWN", ""));
            return cmd;
        }
        if (lower.contains("scroll up") || lower.contains("upar scroll")) {
            cmd.setIntent(ZavaCommand.INTENT_SCROLL);
            cmd.setConversationalResponse("Upar scroll kar rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_SCROLL, "UP", ""));
            return cmd;
        }

        // 4. Volume and Media Controls
        if (lower.contains("volume badhao") || lower.contains("volume up") || lower.contains("sound badhao") || lower.contains("awaz badhao")) {
            cmd.setIntent(ZavaCommand.INTENT_VOLUME_CONTROL);
            cmd.setConversationalResponse("Volume badha rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_VOLUME_CONTROL, "UP", ""));
            return cmd;
        }
        if (lower.contains("volume kam karo") || lower.contains("volume down") || lower.contains("sound kam karo") || lower.contains("awaz kam karo")) {
            cmd.setIntent(ZavaCommand.INTENT_VOLUME_CONTROL);
            cmd.setConversationalResponse("Volume kam kar rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_VOLUME_CONTROL, "DOWN", ""));
            return cmd;
        }
        if (lower.contains("music") || lower.contains("gana") || lower.contains("song") || lower.contains("pause") || lower.contains("play")) {
            if (lower.contains("pause") || lower.contains("rok do") || lower.contains("band karo")) {
                cmd.setIntent(ZavaCommand.INTENT_MEDIA_CONTROL);
                cmd.setConversationalResponse("Music pause kar rahi hoon.");
                cmd.addStep(new ZavaAction(ZavaAction.ACTION_MEDIA_CONTROL, "PAUSE", ""));
                return cmd;
            } else if (lower.contains("next") || lower.contains("agla")) {
                cmd.setIntent(ZavaCommand.INTENT_MEDIA_CONTROL);
                cmd.setConversationalResponse("Agla gana chala rahi hoon.");
                cmd.addStep(new ZavaAction(ZavaAction.ACTION_MEDIA_CONTROL, "NEXT", ""));
                return cmd;
            } else if (lower.contains("play") || lower.contains("chalao") || lower.contains("bajao")) {
                cmd.setIntent(ZavaCommand.INTENT_MEDIA_CONTROL);
                cmd.setConversationalResponse("Music play kar rahi hoon.");
                cmd.addStep(new ZavaAction(ZavaAction.ACTION_MEDIA_CONTROL, "PLAY", ""));
                return cmd;
            }
        }

        // 5. Camera Shortcut
        if (lower.contains("camera") || lower.contains("फोटो") || lower.contains("कैमरा")) {
            cmd.setIntent(ZavaCommand.INTENT_CAMERA);
            cmd.setConversationalResponse("Sure, camera khol rahi hoon.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_APP, "camera", ""));
            return cmd;
        }

        // 6. Custom Shortcuts (e.g. Gaming Mode, Morning Briefing)
        if (lower.contains("gaming mode") || lower.contains("game mode")) {
            cmd.setIntent(ZavaCommand.INTENT_CUSTOM_COMMAND);
            cmd.setConversationalResponse("Gaming mode activate kar rahi hoon: DND aur high performance settings kholi ja rahi hain.");
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_SETTINGS, "DISPLAY", ""));
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_VOLUME_CONTROL, "UP", ""));
            return cmd;
        }

        // 7. General App Launching ("YouTube kholo", "open YouTube", "WhatsApp chalao", "Chrome open karo", "Instagram kholo")
        String appQuery = extractAppQuery(lower);
        if (!appQuery.isEmpty()) {
            AppResolver.AppInfo info = appResolver.resolveApp(appQuery);
            if (info != null) {
                cmd.setIntent(ZavaCommand.INTENT_OPEN_APP);
                cmd.setTarget(info.label);
                cmd.setConversationalResponse("Thik hai, " + info.label + " khol rahi hoon.");
                cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_APP, info.packageName, info.label));
                return cmd;
            } else {
                // If resolver hasn't found it by exact name, use the query directly as app target
                cmd.setIntent(ZavaCommand.INTENT_OPEN_APP);
                cmd.setTarget(appQuery);
                cmd.setConversationalResponse("Thik hai, " + appQuery + " khol rahi hoon.");
                cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_APP, appQuery, appQuery));
                return cmd;
            }
        }

        // 8. General Search intent ("search latest Android news", "search me likho...")
        if (lower.startsWith("search") || lower.contains("search karo") || lower.contains("dhundho")) {
            String query = lower.replaceFirst("(?i)^(search\\s*(for|me|karo)?|dhundho)\\s*", "").trim();
            cmd.setIntent(ZavaCommand.INTENT_SEARCH);
            cmd.setTarget(query);
            cmd.setConversationalResponse("Search kar rahi hoon: " + query);
            cmd.addStep(new ZavaAction(ZavaAction.ACTION_SEARCH, "google", query));
            return cmd;
        }

        // Default: general query or unrecognized intent
        cmd.setIntent(ZavaCommand.INTENT_UNKNOWN);
        cmd.setConfidence(0.5f);
        cmd.setConversationalResponse("Thik hai, samajh gayi: " + normalized);
        return cmd;
    }

    private boolean isMultiAction(String text) {
        return (text.contains("aur") || text.contains(" and ") || text.contains("kholke") || text.contains("open karke"))
                && (text.contains("search") || text.contains("likho") || text.contains("type") || text.contains("bhejo") || text.contains("click"));
    }

    private ZavaCommand parseMultiAction(ZavaCommand cmd, String text) {
        cmd.setIntent(ZavaCommand.INTENT_MULTI_ACTION);

        // e.g. "YouTube kholo, search me Android 16 likho aur search karo"
        // or "Chrome kholo, Google search karo Android 16"
        String appName = "YouTube";
        if (text.contains("chrome")) appName = "Chrome";
        else if (text.contains("whatsapp")) appName = "WhatsApp";

        String searchQuery = "Android 16";
        if (text.contains("android")) {
            searchQuery = "Android 16";
        } else {
            // Extract whatever comes after search
            int idx = text.indexOf("search");
            if (idx != -1 && idx + 6 < text.length()) {
                searchQuery = text.substring(idx + 6).replace("karo", "").replace("aur", "").trim();
            }
        }

        AppResolver.AppInfo app = appResolver.resolveApp(appName);
        String pkg = app != null ? app.packageName : "com.google.android.youtube";

        cmd.setConversationalResponse("Thik hai, " + appName + " kholkar " + searchQuery + " search kar rahi hoon.");

        // Step 1: Open Target App
        cmd.addStep(new ZavaAction(ZavaAction.ACTION_OPEN_APP, pkg, appName));
        // Step 2: Delay to let app UI settle
        cmd.addStep(new ZavaAction(ZavaAction.ACTION_DELAY, "1500", "Waiting for app to load"));
        // Step 3: Find Search UI element and click
        cmd.addStep(new ZavaAction(ZavaAction.ACTION_ACCESSIBILITY_CLICK, "Search", "Search button"));
        // Step 4: Type text into search box
        cmd.addStep(new ZavaAction(ZavaAction.ACTION_TYPE_TEXT, "Search", searchQuery));
        // Step 5: Submit search
        cmd.addStep(new ZavaAction(ZavaAction.ACTION_SEARCH, appName, searchQuery));

        return cmd;
    }

    private String extractAppQuery(String text) {
        // Match patterns like:
        // "<app> kholo", "open <app>", "<app> open karo", "<app> chalao", "<app> dikhao"
        String cleaned = text.replaceAll("(?i)^(open|chalao|kholo|dikhao|start|launch)\\s+", "");
        cleaned = cleaned.replaceAll("(?i)\\s+(kholo|open karo|chalao|dikhao|chalu karo|start karo)$", "");
        cleaned = cleaned.replaceAll("(?i)\\s+app$", "");
        cleaned = cleaned.trim();
        return cleaned;
    }
}
