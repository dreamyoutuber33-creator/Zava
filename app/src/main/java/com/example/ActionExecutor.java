package com.example;

import android.accessibilityservice.AccessibilityService;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Validates and securely executes all system intents, settings, and accessibility automation.
 */
public class ActionExecutor {

    private static final String TAG = "ActionExecutor";

    public interface ExecutionCallback {
        void onStepCompleted(ZavaAction action, boolean success, String message);
        void onAllCompleted(boolean overallSuccess, String finalMessage);
    }

    private final Context context;
    private final AppResolver appResolver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ActionExecutor(Context context, AppResolver appResolver) {
        this.context = context.getApplicationContext();
        this.appResolver = appResolver;
    }

    public void executeCommand(ZavaCommand command, ExecutionCallback callback) {
        if (command == null || command.getSteps() == null || command.getSteps().isEmpty()) {
            if (callback != null) {
                callback.onAllCompleted(false, "Koi action step nahi mila.");
            }
            return;
        }

        executeStep(command, 0, callback);
    }

    private void executeStep(ZavaCommand command, int stepIndex, ExecutionCallback callback) {
        if (stepIndex >= command.getSteps().size()) {
            if (callback != null) {
                callback.onAllCompleted(true, "Sabhi steps safaltapoorvak complete ho gaye.");
            }
            return;
        }

        ZavaAction step = command.getSteps().get(stepIndex);
        boolean success = false;
        String message = "";

        try {
            switch (step.getAction()) {
                case ZavaAction.ACTION_OPEN_APP:
                    success = executeOpenApp(step);
                    break;

                case ZavaAction.ACTION_OPEN_SETTINGS:
                    success = executeOpenSettings(step);
                    break;

                case ZavaAction.ACTION_ACCESSIBILITY_CLICK:
                    success = executeAccessibilityClick(step);
                    break;

                case ZavaAction.ACTION_TYPE_TEXT:
                    success = executeTypeText(step);
                    break;

                case ZavaAction.ACTION_SCROLL:
                    success = executeScroll(step);
                    break;

                case ZavaAction.ACTION_BACK:
                    success = executeGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                    break;

                case ZavaAction.ACTION_HOME:
                    success = executeGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                    break;

                case ZavaAction.ACTION_RECENTS:
                    success = executeGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                    break;

                case ZavaAction.ACTION_READ_SCREEN:
                    message = executeReadScreen();
                    success = true;
                    break;

                case ZavaAction.ACTION_SEARCH:
                    success = executeSearch(step);
                    break;

                case ZavaAction.ACTION_VOLUME_CONTROL:
                    success = executeVolume(step);
                    break;

                case ZavaAction.ACTION_MEDIA_CONTROL:
                    success = executeMedia(step);
                    break;

                case ZavaAction.ACTION_DELAY:
                    long delay = 1000;
                    try {
                        delay = Long.parseLong(step.getTarget());
                    } catch (Exception ignored) {}
                    mainHandler.postDelayed(() -> executeStep(command, stepIndex + 1, callback), delay);
                    return;

                default:
                    Log.w(TAG, "Unknown action: " + step.getAction());
                    success = false;
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Execution error on step " + stepIndex, e);
            success = false;
        }

        ActionVerifier.VerificationResult result = ActionVerifier.verify(context, step, success);
        String stepMsg = message.isEmpty() ? result.feedbackMessage : message;

        if (callback != null) {
            callback.onStepCompleted(step, success, stepMsg);
        }

        if (!success && command.isMultiStep()) {
            // Intelligent recovery or stop on failure
            if (callback != null) {
                callback.onAllCompleted(false, "Step " + (stepIndex + 1) + " par ruk gaye: " + stepMsg);
            }
            return;
        }

        // Proceed to next step with slight pacing delay
        final boolean finalSuccess = success;
        mainHandler.postDelayed(() -> {
            if (stepIndex + 1 < command.getSteps().size()) {
                executeStep(command, stepIndex + 1, callback);
            } else {
                if (callback != null) {
                    callback.onAllCompleted(finalSuccess, stepMsg);
                }
            }
        }, 500);
    }

    private boolean executeOpenApp(ZavaAction action) {
        String target = action.getTarget();
        AppResolver.AppInfo app = appResolver.resolveApp(target);
        if (app != null && app.launchIntent != null) {
            app.launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(app.launchIntent);
            return true;
        }
        Intent directLaunch = context.getPackageManager().getLaunchIntentForPackage(target);
        if (directLaunch != null) {
            directLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(directLaunch);
            return true;
        }
        return false;
    }

    private boolean executeOpenSettings(ZavaAction action) {
        Intent intent;
        String setting = action.getTarget().toUpperCase();
        switch (setting) {
            case "WIFI":
                intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                break;
            case "BLUETOOTH":
                intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                break;
            case "DISPLAY":
                intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                break;
            case "ACCESSIBILITY":
                intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                break;
            case "AIRPLANE":
                intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
                break;
            default:
                intent = new Intent(Settings.ACTION_SETTINGS);
                break;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    private boolean executeAccessibilityClick(ZavaAction action) {
        ZavaAccessibilityService service = ZavaAccessibilityService.getInstance();
        if (service != null) {
            return service.clickElementByText(action.getTarget());
        }
        return false;
    }

    private boolean executeTypeText(ZavaAction action) {
        ZavaAccessibilityService service = ZavaAccessibilityService.getInstance();
        if (service != null) {
            return service.typeText(action.getTarget(), action.getParam());
        }
        return false;
    }

    private boolean executeScroll(ZavaAction action) {
        ZavaAccessibilityService service = ZavaAccessibilityService.getInstance();
        if (service != null) {
            boolean down = !"UP".equalsIgnoreCase(action.getTarget());
            return service.performScroll(down);
        }
        return false;
    }

    private boolean executeGlobalAction(int globalAction) {
        ZavaAccessibilityService service = ZavaAccessibilityService.getInstance();
        if (service != null) {
            return service.performGlobalAction(globalAction);
        }
        return false;
    }

    private String executeReadScreen() {
        ZavaAccessibilityService service = ZavaAccessibilityService.getInstance();
        if (service != null) {
            return service.readScreenContent();
        }
        return "Accessibility service enabled nahi hai. Kripya settings se enable karein.";
    }

    private boolean executeSearch(ZavaAction action) {
        Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
        searchIntent.putExtra(SearchManager.QUERY, action.getParam());
        searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (searchIntent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(searchIntent);
            return true;
        }
        return false;
    }

    private boolean executeVolume(ZavaAction action) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int direction = "UP".equalsIgnoreCase(action.getTarget())
                    ? AudioManager.ADJUST_RAISE
                    : AudioManager.ADJUST_LOWER;
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            return true;
        }
        return false;
    }

    private boolean executeMedia(ZavaAction action) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return false;

        int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        if ("NEXT".equalsIgnoreCase(action.getTarget())) keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
        else if ("PAUSE".equalsIgnoreCase(action.getTarget())) keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE;
        else if ("PLAY".equalsIgnoreCase(action.getTarget())) keyCode = KeyEvent.KEYCODE_MEDIA_PLAY;

        audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        return true;
    }
}
