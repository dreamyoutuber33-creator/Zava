package com.example;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Legitimate Accessibility Service for user-requested voice automation:
 * UI inspection, semantic clicking, text input, scrolling, global navigation, and screen reading.
 */
public class ZavaAccessibilityService extends AccessibilityService {

    private static final String TAG = "ZavaAccessService";
    private static volatile ZavaAccessibilityService sInstance;

    private String currentForegroundPackage = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.i(TAG, "ZavaAccessibilityService connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg != null) {
            currentForegroundPackage = pkg.toString();
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "ZavaAccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sInstance == this) {
            sInstance = null;
        }
    }

    public static ZavaAccessibilityService getInstance() {
        return sInstance;
    }

    public static boolean isServiceRunning() {
        return sInstance != null;
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        if (sInstance != null) return true;
        int accessibilityEnabled = 0;
        final String service = context.getPackageName() + "/" + ZavaAccessibilityService.class.getName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException ignored) {}

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (settingValue != null) {
                return settingValue.contains(service);
            }
        }
        return false;
    }

    public String getCurrentForegroundPackage() {
        return currentForegroundPackage;
    }

    /**
     * Finds and clicks a UI node matching the given semantic query (text or contentDescription).
     */
    public boolean clickElementByText(String targetText) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        findNodesByText(root, targetText.toLowerCase(), candidates);

        for (AccessibilityNodeInfo node : candidates) {
            AccessibilityNodeInfo clickable = findClickableParent(node);
            if (clickable != null) {
                boolean clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                clickable.recycle();
                root.recycle();
                return clicked;
            }
        }

        root.recycle();
        return false;
    }

    /**
     * Enters text into the focused or target editable element.
     */
    public boolean typeText(String targetLabel, String textToEnter) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        // Try focused node first
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToEnter);
            boolean success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            focused.recycle();
            root.recycle();
            return success;
        }

        // Search for editable element matching label
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        findNodesByText(root, targetLabel.toLowerCase(), candidates);
        for (AccessibilityNodeInfo node : candidates) {
            if (node.isEditable()) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToEnter);
                boolean success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                node.recycle();
                root.recycle();
                return success;
            }
        }

        root.recycle();
        return false;
    }

    /**
     * Reads all visible text content on screen.
     */
    public String readScreenContent() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "Screen content is currently not accessible.";

        StringBuilder sb = new StringBuilder();
        collectText(root, sb, 0);
        root.recycle();

        String result = sb.toString().trim();
        return result.isEmpty() ? "Screen par koi visible text nahi mila." : result;
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 15) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        if (!TextUtils.isEmpty(text)) {
            sb.append(text).append(". ");
        } else if (!TextUtils.isEmpty(desc)) {
            sb.append(desc).append(". ");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectText(child, sb, depth + 1);
                child.recycle();
            }
        }
    }

    private void findNodesByText(AccessibilityNodeInfo node, String query, List<AccessibilityNodeInfo> outList) {
        if (node == null) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        if ((text != null && text.toString().toLowerCase().contains(query)) ||
            (desc != null && desc.toString().toLowerCase().contains(query))) {
            outList.add(AccessibilityNodeInfo.obtain(node));
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findNodesByText(child, query, outList);
                child.recycle();
            }
        }
    }

    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (current != node) {
                current.recycle();
            }
            current = parent;
        }
        return null;
    }

    public boolean performScroll(boolean down) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        int action = down ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        boolean result = performScrollInternal(root, action);
        root.recycle();
        return result;
    }

    private boolean performScrollInternal(AccessibilityNodeInfo node, int action) {
        if (node == null) return false;
        if (node.isScrollable()) {
            return node.performAction(action);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean scrolled = performScrollInternal(child, action);
                child.recycle();
                if (scrolled) return true;
            }
        }
        return false;
    }
}
