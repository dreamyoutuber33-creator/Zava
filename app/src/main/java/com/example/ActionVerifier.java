package com.example;

import android.content.Context;

/**
 * Verifies execution results of assistant actions and formulates conversational feedback.
 */
public class ActionVerifier {

    public static class VerificationResult {
        public final boolean isSuccess;
        public final String feedbackMessage;

        public VerificationResult(boolean isSuccess, String feedbackMessage) {
            this.isSuccess = isSuccess;
            this.feedbackMessage = feedbackMessage;
        }
    }

    public static VerificationResult verify(Context context, ZavaAction action, boolean executionSuccess) {
        if (!executionSuccess) {
            return new VerificationResult(false, action.getAction() + " execute nahi ho paya. Main dobara try karun?");
        }

        switch (action.getAction()) {
            case ZavaAction.ACTION_OPEN_APP:
                String targetName = action.getParam().isEmpty() ? action.getTarget() : action.getParam();
                ZavaAccessibilityService service = ZavaAccessibilityService.getInstance();
                if (service != null) {
                    String fg = service.getCurrentForegroundPackage();
                    if (!fg.isEmpty() && !action.getTarget().isEmpty() && fg.contains(action.getTarget())) {
                        return new VerificationResult(true, targetName + " open ho gaya.");
                    }
                }
                return new VerificationResult(true, targetName + " launch kar diya gaya hai.");

            case ZavaAction.ACTION_OPEN_SETTINGS:
                return new VerificationResult(true, action.getTarget() + " settings open ho gayi.");

            case ZavaAction.ACTION_ACCESSIBILITY_CLICK:
                return new VerificationResult(true, action.getTarget() + " par tap kar diya.");

            case ZavaAction.ACTION_TYPE_TEXT:
                return new VerificationResult(true, "'" + action.getParam() + "' likh diya gaya hai.");

            case ZavaAction.ACTION_SCROLL:
                return new VerificationResult(true, "Screen scroll ho gayi.");

            case ZavaAction.ACTION_READ_SCREEN:
                return new VerificationResult(true, "Screen read complete.");

            case ZavaAction.ACTION_VOLUME_CONTROL:
                return new VerificationResult(true, "Volume adjust ho gaya.");

            case ZavaAction.ACTION_MEDIA_CONTROL:
                return new VerificationResult(true, "Media action complete.");

            default:
                return new VerificationResult(true, "Action safalta-purvak complete ho gaya.");
        }
    }
}
